package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.DocumentDefinitionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.*;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.time.Clock;
import java.util.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestEnrollmentDocumentsUseCase {
  private final AuditEventRecorder auditRecorder;
  private final EnrollmentInstitutionLock lock;
  private final EnrollmentApplicationRepository applications;
  private final DocumentDefinitionRepository definitions;
  private final ScopedAuthorizationService authorization;
  private final PlatformAccountRepository accounts;
  private final PersonRepository people;
  private final Clock clock;

  @Transactional
  public EnrollmentDocumentRequestResponse execute(
      final UUID institutionId,
      final UUID applicationId,
      final CreateEnrollmentDocumentRequest input) {
    lock.lock(institutionId);
    var application =
        applications
            .findForAttachmentUpdate(applicationId, institutionId)
            .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
    authorization.require(
        PermissionCode.ENROLLMENT_DOCUMENT_REQUEST_CREATE,
        institutionId,
        application.getTrainingPathId());
    authorization.require(
        PermissionCode.ENROLLMENT_ATTACHMENT_READ, institutionId, application.getTrainingPathId());
    if (application.getStatus() != EnrollmentApplicationStatus.SUBMITTED
        && application.getStatus() != EnrollmentApplicationStatus.PROVISIONALLY_APPROVED) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_REQUEST_CLOSED);
    }
    Set<UUID> seen = new HashSet<>();
    List<DocumentDefinition> selected = new ArrayList<>();
    for (var item : input.documents()) {
      var existing =
          application.getDocumentRequirements().stream()
              .filter(value -> value.getDocument().getId().equals(item.documentId()))
              .findFirst();
      if (existing.isPresent()) {
        var requirement = existing.orElseThrow();
        throw new DocumentCatalogException(
            ErrorCategory.CONFLICT,
            EnrollmentMessages.DOCUMENT_REQUEST_DUPLICATE + " " + requirement.getName(),
            Map.of(
                "existingRequirementId",
                requirement.getId().toString(),
                "documentId",
                item.documentId().toString()));
      }
      if (!seen.add(item.documentId())) {
        throw new DocumentCatalogException(
            ErrorCategory.CONFLICT, EnrollmentMessages.DOCUMENT_REQUEST_DUPLICATE);
      }
      selected.add(
          definitions
              .findByIdAndInstitutionId(item.documentId(), institutionId)
              .filter(value -> value.isActive())
              .orElseThrow(
                  () ->
                      new EnrollmentValidationException(
                          EnrollmentMessages.DOCUMENT_REQUEST_INACTIVE)));
    }
    var actor =
        EnrollmentDocumentAudit.Actor.from(SecurityContextHolder.getContext().getAuthentication());
    var actorId = Objects.requireNonNull(actor.id());
    String name;
    if (actor.accountType().equals("PLATFORM")) {
      var account = accounts.findById(actorId).orElseThrow();
      name = account.getName() + " " + account.getLastName();
    } else {
      var person = people.findById(actorId).orElseThrow();
      name = person.getFirstName() + " " + person.getLastName();
    }
    var request =
        EnrollmentDocumentRequest.create(
            application, input.reason(), clock.instant(), actorId, actor.accountType(), name);
    application.addDocumentRequest(request);
    long order =
        application.getDocumentRequirements().stream()
                .mapToInt(value -> value.getDisplayOrder())
                .max()
                .orElse(-1)
            + 1L;
    for (int i = 0; i < selected.size(); i++) {
      application.addDocumentRequirement(
          EnrollmentDocumentRequirement.additional(
              application,
              selected.get(i),
              request,
              input.documents().get(i).level(),
              (int) Math.min(Integer.MAX_VALUE, order + i)));
    }
    applications.flush();
    auditRecorder.recordCurrentActor(
        application.getInstitution(),
        application.getApplicantPerson().getId(),
        AuditAction.ENROLLMENT_DOCUMENTS_REQUESTED,
        AuditEntityType.ENROLLMENT_APPLICATION,
        application.getId(),
        AuditOrigin.administration(),
        Map.of("after", Map.of("requirementCount", selected.size())));
    return EnrollmentDocumentRequestResponse.from(request);
  }
}
