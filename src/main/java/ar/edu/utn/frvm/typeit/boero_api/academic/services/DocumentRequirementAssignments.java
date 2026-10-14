package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentAssignmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentRequirementAssignments {
  private final TrainingPathDocumentRequirementRepository assignments;
  private final TrainingPathRepository paths;
  private final AcademicAccessGuard access;

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public void validate(
      final UUID institutionId,
      final DocumentDefinition document,
      final List<DocumentAssignmentRequest> changes) {
    final var seen = new HashSet<UUID>();
    for (final var change : changes) {
      if (!seen.add(change.trainingPathId())) {
        throw new DocumentCatalogException(
            ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_DUPLICATE_ASSIGNMENT);
      }
      access.require(
          PermissionCode.TRAINING_PATH_UPDATE,
          institutionId,
          ScopedResource.TRAINING_PATH,
          change.trainingPathId());
      path(institutionId, change.trainingPathId());
      if (document.getId() != null) {
        assignments
            .findByTrainingPathIdAndDocumentId(change.trainingPathId(), document.getId())
            .ifPresent(existing -> requireRevision(existing.getRevision(), change.revision()));
      }
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TrainingPathDocumentRequirement apply(
      final UUID institutionId,
      final DocumentDefinition document,
      final DocumentAssignmentRequest change) {
    final var value =
        assignments
            .findByTrainingPathIdAndDocumentId(change.trainingPathId(), document.getId())
            .orElseGet(
                () ->
                    TrainingPathDocumentRequirement.create(
                        path(institutionId, change.trainingPathId()), document));
    value.update(
        change.level(), change.displayOrder(), change.active(), change.specificInstructions());

    return assignments.save(value);
  }

  public static void requireRevision(final long current, final @Nullable Long expected) {
    if (expected == null || current != expected.longValue()) {
      throw new DocumentCatalogException(
          ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_REVISION_CONFLICT);
    }
  }

  private TrainingPath path(final UUID institutionId, final UUID pathId) {
    return paths
        .findById(pathId)
        .filter(
            value ->
                value.getDeletedAt() == null
                    && value.getInstitution().getId().equals(institutionId))
        .orElseThrow(
            () ->
                new DocumentCatalogException(
                    ErrorCategory.NOT_FOUND, AcademicMessages.DOCUMENT_NOT_FOUND));
  }
}
