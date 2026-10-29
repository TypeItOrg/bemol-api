package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountActionRateLimiter;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CannotGuardianSelfException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterGuardianDependentUseCase {
  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final PersonGuardianRepository personGuardianRepository;
  private final Validator validator;
  private final AuditEventRecorder auditEventRecorder;
  private final AccountAccessAuthorization access;
  private final AccountActionRateLimiter limiter;

  @Transactional
  public GuardianDependentResponse execute(
      final UUID institutionId,
      final UUID tutorPersonId,
      final CreateGuardianDependentRequest request) {
    final var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    access.requirePermission(institutionId, tutorPersonId, PermissionCode.GUARDIAN_LINK_REQUEST);
    final var institution =
        institutionRepository
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var tutor =
        personRepository
            .findByIdAndInstitution_Id(tutorPersonId, institutionId)
            .orElseThrow(PersonNotFoundException::new);
    if (request.documentNumber().equals(tutor.getDocumentNumber())) {
      throw new CannotGuardianSelfException();
    }
    final var mode =
        request.requestMode() == null ? GuardianDependentRequestMode.NEW : request.requestMode();
    if (personGuardianRepository.existsOpenLinkForDocument(
        institutionId, tutorPersonId, request.documentNumber())) {
      throw new DependentAlreadyLinkedException();
    }
    limiter.guardianRequest(institutionId, tutorPersonId);
    final var link =
        PersonGuardian.requestUnmaterialized(
            institution,
            tutor,
            request.documentNumber(),
            mode,
            request.relationship(),
            request.isPrimaryContact(),
            mode == GuardianDependentRequestMode.NEW ? request.firstName() : null,
            mode == GuardianDependentRequestMode.NEW ? request.lastName() : null,
            mode == GuardianDependentRequestMode.NEW ? request.birthDate() : null);
    link.requestOwnAccess(request.ownAccessRequested(), request.email());
    try {
      personGuardianRepository.saveAndFlush(link);
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
            && "person_guardians_pending_document_unique".equals(violation.getConstraintName())) {
          throw new DependentAlreadyLinkedException();
        }
      }
      throw exception;
    }
    auditEventRecorder.recordCurrentActor(
        institution,
        null,
        AuditAction.GUARDIAN_LINK_REQUESTED,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
    return GuardianDependentResponse.from(link, 0, List.of());
  }
}
