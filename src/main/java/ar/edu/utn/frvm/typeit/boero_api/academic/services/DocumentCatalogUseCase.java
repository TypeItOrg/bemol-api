package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.DocumentDefinitionRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentAssignmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentCatalogSaveResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentDefinitionRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentDefinitionResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentRequirementRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.PlatformDocumentDefinitionResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.SynchronizeDraftDocumentsUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentCatalogUseCase {
  private final DocumentDefinitionRepository definitions;
  private final TrainingPathDocumentRequirementRepository assignments;
  private final InstitutionRepository institutions;
  private final ScopedAuthorizationService authorization;
  private final AcademicAccessGuard access;
  private final EnrollmentInstitutionLock lock;
  private final SynchronizeDraftDocumentsUseCase synchronize;
  private final QueryDocumentCatalogUseCase queries;
  private final DocumentRequirementAssignments requirements;

  @Transactional(readOnly = true)
  public Page<DocumentDefinitionResponse> list(
      final UUID institutionId,
      final String search,
      final @Nullable Boolean active,
      final @Nullable UUID pathId,
      final @Nullable UUID applicationId,
      final boolean forTrainingPathCreation,
      final Pageable pageable) {
    return queries.list(
        institutionId, search, active, pathId, applicationId, forTrainingPathCreation, pageable);
  }

  @Transactional(readOnly = true)
  public Page<PlatformDocumentDefinitionResponse> listPlatform(
      final @Nullable UUID institutionId,
      final String search,
      final @Nullable Boolean active,
      final Pageable pageable) {
    return queries.listPlatform(institutionId, search, active, pageable);
  }

  @Transactional(readOnly = true)
  public DocumentDefinitionResponse get(final UUID institutionId, final UUID id) {
    return queries.get(institutionId, id);
  }

  @Transactional(readOnly = true)
  public Page<DocumentRequirementResponse> associations(
      final UUID institutionId,
      final UUID id,
      final @Nullable UUID pathId,
      final @Nullable Boolean active,
      final Pageable pageable) {
    return queries.associations(institutionId, id, pathId, active, pageable);
  }

  @Transactional
  public DocumentCatalogSaveResponse save(
      final UUID institutionId, final @Nullable UUID id, final DocumentDefinitionRequest request) {
    require(PermissionCode.DOCUMENT_CATALOG_MANAGE, institutionId);
    final var destinationId =
        request.targetInstitutionId() == null ? institutionId : request.targetInstitutionId();
    final boolean transferring = !destinationId.equals(institutionId);
    if (transferring && (id == null || !authorization.isPlatformAdministrator())) {
      throw new ScopedResourceNotFoundException();
    }

    // Always acquire tenant locks in the same order when transferring a definition.
    for (final var tenantId : new TreeSet<>(List.of(institutionId, destinationId))) {
      lock.lock(tenantId);
    }
    final var value =
        id == null
            ? DocumentDefinition.create(
                institutions
                    .findById(institutionId)
                    .orElseThrow(
                        () ->
                            new DocumentCatalogException(
                                ErrorCategory.NOT_FOUND, AcademicMessages.DOCUMENT_NOT_FOUND)))
            : queries.definition(institutionId, id);
    if (id != null) {
      DocumentRequirementAssignments.requireRevision(value.getRevision(), request.revision());
    }
    if (transferring) {
      transfer(value, destinationId);
    }

    final var changes =
        request.assignments() == null
            ? List.<DocumentAssignmentRequest>of()
            : request.assignments();
    requirements.validate(destinationId, value, changes);
    value.update(
        request.name(), request.instructions(), request.allowedFormats(), request.active());
    definitions.saveAndFlush(value);
    final var affected = new HashSet<UUID>();
    for (final var assignment : assignments.findByDocumentId(value.getId())) {
      affected.add(assignment.getTrainingPath().getId());
    }
    for (final var change : changes) {
      requirements.apply(destinationId, value, change);
      affected.add(change.trainingPathId());
    }
    assignments.flush();
    final int drafts = synchronize.execute(destinationId, affected);

    return queries.result(destinationId, value.getId(), affected.size(), drafts);
  }

  @Transactional
  public DocumentCatalogSaveResponse saveAssignments(
      final UUID institutionId, final UUID id, final List<DocumentAssignmentRequest> changes) {
    if (changes.isEmpty()) {
      require(PermissionCode.DOCUMENT_CATALOG_READ, institutionId);
    }
    lock.lock(institutionId);
    final var value = queries.definition(institutionId, id);
    requirements.validate(institutionId, value, changes);

    final var affected = new HashSet<UUID>();
    for (final var change : changes) {
      requirements.apply(institutionId, value, change);
      affected.add(change.trainingPathId());
    }
    assignments.flush();
    final int drafts = synchronize.execute(institutionId, affected);

    return queries.result(institutionId, id, affected.size(), drafts);
  }

  @Transactional
  public DocumentRequirementResponse saveAssignment(
      final UUID institutionId,
      final UUID pathId,
      final @Nullable UUID id,
      final DocumentRequirementRequest request) {
    access.require(
        PermissionCode.TRAINING_PATH_UPDATE, institutionId, ScopedResource.TRAINING_PATH, pathId);
    lock.lock(institutionId);
    final var document = queries.definition(institutionId, request.documentId());
    if (id != null) {
      final var existing =
          assignments
              .findByIdAndTrainingPathId(id, pathId)
              .orElseThrow(
                  () ->
                      new DocumentCatalogException(
                          ErrorCategory.NOT_FOUND, AcademicMessages.DOCUMENT_NOT_FOUND));
      if (!existing.getDocument().getId().equals(request.documentId())) {
        throw new DocumentCatalogException(
            ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_ASSIGNMENT_IMMUTABLE);
      }
    }
    final var change =
        new DocumentAssignmentRequest(
            pathId,
            request.revision(),
            request.level(),
            request.displayOrder(),
            request.active(),
            request.specificInstructions());
    requirements.validate(institutionId, document, List.of(change));

    final var saved = requirements.apply(institutionId, document, change);
    assignments.flush();
    final var savedId = saved.getId();
    synchronize.execute(institutionId, Set.of(pathId));

    return DocumentRequirementResponse.from(assignments.findById(savedId).orElseThrow());
  }

  private void transfer(final DocumentDefinition value, final UUID destinationId) {
    if (value.getUsedAt() != null
        || assignments.countByDocumentId(value.getId()) > 0
        || definitions.hasEnrollmentUsage(value.getId())) {
      throw new DocumentCatalogException(
          ErrorCategory.CONFLICT, AcademicMessages.DOCUMENT_INSTITUTION_CHANGE_BLOCKED);
    }

    value.changeInstitution(institutions.findById(destinationId).orElseThrow());
  }

  private void require(final PermissionCode permission, final UUID institutionId) {
    authorization.require(permission, institutionId, new UUID(0, 0));
  }
}
