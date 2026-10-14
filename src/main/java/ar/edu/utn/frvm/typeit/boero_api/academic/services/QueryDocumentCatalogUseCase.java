package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.DocumentCatalogException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.DocumentDefinitionRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathDocumentRequirementRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentCatalogSaveResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentDefinitionResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.DocumentRequirementResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.PlatformDocumentDefinitionResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.ScopedResourceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryDocumentCatalogUseCase {
  private final EnrollmentApplicationRepository applications;
  private final DocumentDefinitionRepository definitions;
  private final TrainingPathDocumentRequirementRepository assignments;
  private final ScopedAuthorizationService authorization;
  private final AcademicAccessGuard access;

  @Transactional(readOnly = true)
  public Page<DocumentDefinitionResponse> list(
      final UUID institutionId,
      final String search,
      final @Nullable Boolean active,
      final @Nullable UUID pathId,
      final @Nullable UUID applicationId,
      final boolean forTrainingPathCreation,
      final Pageable pageable) {
    if (applicationId != null) {
      final var application =
          applications
              .findById(applicationId)
              .filter(
                  value ->
                      value.getDeletedAt() == null
                          && value.getInstitution().getId().equals(institutionId))
              .orElseThrow(
                  () ->
                      new DocumentCatalogException(
                          ErrorCategory.NOT_FOUND, AcademicMessages.DOCUMENT_NOT_FOUND));
      authorization.require(
          PermissionCode.ENROLLMENT_DOCUMENT_REQUEST_CREATE,
          institutionId,
          application.getTrainingPathId());
    } else if (pathId == null) {
      require(
          forTrainingPathCreation
              ? PermissionCode.TRAINING_PATH_CREATE
              : PermissionCode.DOCUMENT_CATALOG_READ,
          institutionId);
    } else {
      access.require(
          PermissionCode.TRAINING_PATH_READ, institutionId, ScopedResource.TRAINING_PATH, pathId);
    }

    return definitions
        .findAll(filters(institutionId, search, active), pageable)
        .map(DocumentDefinitionResponse::from);
  }

  @Transactional(readOnly = true)
  public Page<PlatformDocumentDefinitionResponse> listPlatform(
      final @Nullable UUID institutionId,
      final String search,
      final @Nullable Boolean active,
      final Pageable pageable) {
    if (!authorization.isPlatformAdministrator()) {
      throw new ScopedResourceNotFoundException();
    }

    return definitions
        .findAll(filters(institutionId, search, active), pageable)
        .map(PlatformDocumentDefinitionResponse::from);
  }

  @Transactional(readOnly = true)
  public DocumentDefinitionResponse get(final UUID institutionId, final UUID id) {
    require(PermissionCode.DOCUMENT_CATALOG_READ, institutionId);
    final var value = definition(institutionId, id);
    final long pathCount = assignments.countByDocumentId(id);
    final long draftCount = applications.countDraftsUsingDocument(id);

    return DocumentDefinitionResponse.from(value, canChangeInstitution(value, pathCount))
        .withImpact(pathCount, draftCount);
  }

  @Transactional(readOnly = true)
  public DocumentDefinition definition(final UUID institutionId, final UUID id) {
    return definitions
        .findByIdAndInstitutionId(id, institutionId)
        .orElseThrow(
            () ->
                new DocumentCatalogException(
                    ErrorCategory.NOT_FOUND, AcademicMessages.DOCUMENT_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Page<DocumentRequirementResponse> associations(
      final UUID institutionId,
      final UUID id,
      final @Nullable UUID pathId,
      final @Nullable Boolean active,
      final Pageable pageable) {
    require(PermissionCode.DOCUMENT_CATALOG_READ, institutionId);
    definition(institutionId, id);
    final var readAccess = authorization.managementAccess(PermissionCode.TRAINING_PATH_READ);
    final var readablePaths =
        readAccess.trainingPathIds().isEmpty()
            ? Set.of(new UUID(0, 0))
            : readAccess.trainingPathIds();

    return assignments
        .findAll(
            (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("document").get("id"), id),
                    cb.equal(root.get("institutionId"), institutionId),
                    cb.isNull(root.get("trainingPath").get("deletedAt")),
                    active == null ? cb.conjunction() : cb.equal(root.get("active"), active),
                    pathId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("trainingPath").get("id"), pathId),
                    readAccess.institutional()
                        ? cb.conjunction()
                        : root.get("trainingPath").get("id").in(readablePaths)),
            pageable)
        .map(DocumentRequirementResponse::from);
  }

  @Transactional(readOnly = true)
  public DocumentCatalogSaveResponse result(
      final UUID institutionId, final UUID id, final int pathCount, final int draftCount) {
    final var readAccess = authorization.managementAccess(PermissionCode.TRAINING_PATH_READ);
    final var all = assignments.findByDocumentId(id);
    final var value = definition(institutionId, id);
    final var visible =
        all.stream()
            .filter(assignment -> readAccess.includes(assignment.getTrainingPath().getId()))
            .map(DocumentRequirementResponse::from)
            .toList();

    return new DocumentCatalogSaveResponse(
        DocumentDefinitionResponse.from(value, canChangeInstitution(value, all.size())),
        visible,
        pathCount,
        draftCount);
  }

  private boolean canChangeInstitution(final DocumentDefinition value, final long assignmentCount) {
    return authorization.isPlatformAdministrator()
        && value.getUsedAt() == null
        && assignmentCount == 0
        && !definitions.hasEnrollmentUsage(value.getId());
  }

  private void require(final PermissionCode permission, final UUID institutionId) {
    authorization.require(permission, institutionId, new UUID(0, 0));
  }

  private Specification<DocumentDefinition> filters(
      final @Nullable UUID institutionId, final String search, final @Nullable Boolean active) {
    return (root, query, cb) -> {
      var predicate = cb.conjunction();
      if (institutionId != null) {
        predicate = cb.and(predicate, cb.equal(root.get("institution").get("id"), institutionId));
      }
      if (active != null) {
        predicate = cb.and(predicate, cb.equal(root.get("active"), active));
      }
      if (!search.isBlank()) {
        final var text = SearchNormalization.escapeLike(search.trim().toLowerCase(Locale.ROOT));
        predicate = cb.and(predicate, cb.like(cb.lower(root.get("name")), "%" + text + "%", '\\'));
      }

      return predicate;
    };
  }
}
