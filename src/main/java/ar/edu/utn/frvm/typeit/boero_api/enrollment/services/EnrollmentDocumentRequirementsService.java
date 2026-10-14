package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentRequirementLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.DocumentReviewStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentAttachmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentDocumentRequirementResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentDocumentRequirementsService {
  private final TrainingPathDocumentRequirementRepository definitions;
  private final EnrollmentAttachmentRepository attachments;
  private final EnrollmentDocumentAuthorization authorization;
  private final EnrollmentApplicationPeriodService periods;

  @Transactional(propagation = Propagation.MANDATORY)
  public void snapshot(EnrollmentApplication application) {
    definitions
        .findByTrainingPathIdOrderByDisplayOrderAscIdAsc(application.getTrainingPathId())
        .stream()
        .filter(value -> value.isEffectiveActive())
        .forEach(
            value ->
                application.addDocumentRequirement(
                    EnrollmentDocumentRequirement.snapshot(application, value)));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void synchronize(final EnrollmentApplication application, final Instant at) {
    synchronize(
        application,
        definitions.findByTrainingPathIdOrderByDisplayOrderAscIdAsc(
            application.getTrainingPathId()),
        at);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void synchronize(
      final EnrollmentApplication application,
      final List<TrainingPathDocumentRequirement> sources,
      final Instant at) {
    if (application.getStatus() != EnrollmentApplicationStatus.DRAFT) {
      return;
    }
    var actor =
        EnrollmentDocumentAudit.Actor.from(SecurityContextHolder.getContext().getAuthentication());
    var originals = new HashMap<UUID, EnrollmentDocumentRequirement>();
    for (var requirement : application.getDocumentRequirements()) {
      final var sourceId = requirement.getSourceRequirementId();
      if (sourceId != null) {
        originals.put(sourceId, requirement);
      }
    }
    for (var source : sources) {
      var existing = originals.remove(source.getId());
      if (!source.isEffectiveActive()) {
        if (existing != null && existing.retire()) {
          existing.recordChange("RETIRED", at, actor.id(), actor.accountType());
        }
      } else if (existing == null) {
        var added = EnrollmentDocumentRequirement.snapshot(application, source);
        application.addDocumentRequirement(added);
        added.recordChange("ADDED", at, actor.id(), actor.accountType());
      } else {
        boolean wasActive = existing.isActive();
        if (existing.synchronize(source)) {
          existing.recordChange(
              wasActive ? "UPDATED" : "REACTIVATED", at, actor.id(), actor.accountType());
        }
      }
    }
    for (var removed : originals.values()) {
      if (removed.retire()) {
        removed.recordChange("RETIRED", at, actor.id(), actor.accountType());
      }
    }
  }

  public void requireActive(final EnrollmentDocumentRequirement requirement) {
    if (!requirement.isActive()) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_REQUIREMENT_RETIRED);
    }
  }

  public EnrollmentDocumentRequirement requirement(EnrollmentApplication application, UUID id) {
    return application.getDocumentRequirements().stream()
        .filter(value -> value.getId().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new EnrollmentValidationException(
                    EnrollmentMessages.DOCUMENT_REQUIREMENT_NOT_FOUND));
  }

  public void requireSubmission(EnrollmentApplication application) {
    var current = current(application);
    for (var requirement : application.getDocumentRequirements()) {
      var file = current.get(requirement.getId());
      if (requirement.isActive()
          && file != null
          && !requirement.getAllowedFormats().contains(file.getContentType())) {
        throw new EnrollmentValidationException(
            requirement.getName() + ": " + EnrollmentMessages.DOCUMENT_FORMAT_CHANGED,
            Map.of(
                "documents." + requirement.getId(),
                requirement.getName() + ": " + EnrollmentMessages.DOCUMENT_FORMAT_CHANGED));
      }
    }
    var missing =
        application.getDocumentRequirements().stream()
            .filter(
                value ->
                    value.isActive()
                        && value.getLevel() == DocumentRequirementLevel.AT_SUBMISSION
                        && !current.containsKey(value.getId()))
            .map(value -> value.getName())
            .toList();
    if (!missing.isEmpty()) {
      var message =
          EnrollmentMessages.DOCUMENT_SUBMISSION_REQUIRED + " " + String.join(", ", missing) + ".";
      throw new EnrollmentValidationException(message, Map.of("documents", message));
    }
  }

  public boolean initialAccepted(EnrollmentApplication application) {
    return accepted(application, true);
  }

  public boolean allAccepted(EnrollmentApplication application) {
    return accepted(application, false);
  }

  private boolean accepted(EnrollmentApplication application, boolean initialOnly) {
    var current = current(application);
    return application.getDocumentRequirements().stream()
        .filter(value -> value.isActive())
        .filter(
            value ->
                initialOnly
                    ? value.getLevel() == DocumentRequirementLevel.AT_SUBMISSION
                    : value.getLevel() != DocumentRequirementLevel.OPTIONAL)
        .allMatch(
            value ->
                current.containsKey(value.getId())
                    && value
                        .getAllowedFormats()
                        .contains(current.get(value.getId()).getContentType())
                    && current.get(value.getId()).getReviewStatus()
                        == DocumentReviewStatus.ACCEPTED);
  }

  public void requireApproval(EnrollmentApplication application, boolean provisional) {
    if (provisional) {
      if (!initialAccepted(application) || allAccepted(application)) {
        throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_PROVISIONAL_REQUIRED);
      }
      return;
    }

    if (!allAccepted(application)) {
      throw new EnrollmentValidationException(EnrollmentMessages.DOCUMENT_CONFIRMATION_REQUIRED);
    }
  }

  public boolean editable(EnrollmentApplication application) {
    return application.getDeletedAt() == null
        && (application.getStatus() == EnrollmentApplicationStatus.SUBMITTED
            || application.getStatus() == EnrollmentApplicationStatus.PROVISIONALLY_APPROVED
            || application.isEditable() && periods.isOpen(application));
  }

  public List<EnrollmentDocumentRequirementResponse> responses(
      EnrollmentApplication application, @Nullable Authentication authentication) {
    var current = current(application);
    boolean editable = editable(application);
    boolean upload =
        editable
            && authorization.canAccess(
                application, authentication, PermissionCode.ENROLLMENT_ATTACHMENT_UPLOAD);
    boolean delete =
        editable
            && authorization.canAccess(
                application, authentication, PermissionCode.ENROLLMENT_ATTACHMENT_DELETE);
    boolean review =
        application.getStatus() != EnrollmentApplicationStatus.DRAFT
            && editable
            && authorization.canAccess(
                application, authentication, PermissionCode.ENROLLMENT_ATTACHMENT_REVIEW);
    return application.getDocumentRequirements().stream()
        .sorted(
            Comparator.comparingInt(
                    (EnrollmentDocumentRequirement requirement) -> requirement.getDisplayOrder())
                .thenComparing(requirement -> requirement.getId()))
        .map(
            requirement ->
                EnrollmentDocumentRequirementResponse.from(
                    requirement,
                    current.get(requirement.getId()),
                    application.isEditable(),
                    upload,
                    delete,
                    review))
        .toList();
  }

  private Map<UUID, EnrollmentAttachment> current(EnrollmentApplication application) {
    Map<UUID, EnrollmentAttachment> result = new HashMap<>();
    for (var file :
        attachments.findByEnrollmentApplicationIdAndDeletedAtIsNull(application.getId())) {
      if (file.isCurrent()) {
        result.put(file.getRequirement().getId(), file);
      }
    }
    return result;
  }
}
