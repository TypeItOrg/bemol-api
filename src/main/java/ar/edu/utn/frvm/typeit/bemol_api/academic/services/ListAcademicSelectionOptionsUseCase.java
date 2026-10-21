package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSelectionOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAcademicSelectionOptionsUseCase {
  private final EntityManager entityManager;
  private final ScopedAuthorizationService authorization;

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicSelectionOptionResponse> execute(
      UUID institutionId,
      String resource,
      @Nullable String search,
      @Nullable Boolean active,
      @Nullable Boolean published,
      @Nullable UUID trainingPathId,
      @Nullable StudyPlanStatus status,
      Pageable pageable) {
    return execute(
        institutionId, resource, search, active, published, trainingPathId, status, pageable, null);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicSelectionOptionResponse> execute(
      UUID institutionId,
      String resource,
      @Nullable String search,
      @Nullable Boolean active,
      @Nullable Boolean published,
      @Nullable UUID trainingPathId,
      @Nullable StudyPlanStatus status,
      Pageable pageable,
      @Nullable PermissionCode operation) {
    return execute(
        institutionId,
        resource,
        search,
        active,
        published,
        trainingPathId,
        status,
        pageable,
        operation,
        null);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicSelectionOptionResponse> execute(
      UUID institutionId,
      String resource,
      @Nullable String search,
      @Nullable Boolean active,
      @Nullable Boolean published,
      @Nullable UUID trainingPathId,
      @Nullable StudyPlanStatus status,
      Pageable pageable,
      @Nullable PermissionCode operation,
      @Nullable UUID id) {
    final var source = AcademicSelectionSource.forResource(resource);
    final var access = requireAccess(source.permissions(), operation);
    final var selection =
        selectionQuery(
            source, access, institutionId, search, active, published, trainingPathId, status, id);

    final var countQuery =
        entityManager.createQuery("SELECT count(item)" + selection.clause(), Long.class);
    final var query =
        entityManager.createQuery(
            "SELECT item"
                + selection.clause()
                + (source.entity().equals("AcademicYear")
                    ? " ORDER BY item.year DESC, item.id"
                    : " ORDER BY item.name, item.id"),
            Object.class);
    selection
        .parameters()
        .forEach(
            (name, value) -> {
              query.setParameter(name, value);
              countQuery.setParameter(name, value);
            });
    final long total = countQuery.getSingleResult();
    final var items =
        query
            .setFirstResult(Math.toIntExact(pageable.getOffset()))
            .setMaxResults(pageable.getPageSize())
            .getResultStream()
            .map(this::option)
            .toList();

    return new PaginatedResponse<>(
        items,
        pageable.getPageNumber(),
        pageable.getPageSize(),
        total,
        (int) Math.ceil((double) total / pageable.getPageSize()));
  }

  private PermissionAccess requireAccess(
      Set<PermissionCode> permissions, final @Nullable PermissionCode operation) {
    if (operation != null) {
      if (!permissions.contains(operation)) {
        throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
      }
      permissions = Set.of(operation);
    }

    var access = PermissionAccess.none();
    for (final var permission : permissions) {
      access = access.union(authorization.managementAccess(permission));
    }
    if (!access.institutional() && access.trainingPathIds().isEmpty()) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }

    return access;
  }

  private SelectionQuery selectionQuery(
      final AcademicSelectionSource source,
      final PermissionAccess access,
      final UUID institutionId,
      final @Nullable String search,
      final @Nullable Boolean active,
      final @Nullable Boolean published,
      final @Nullable UUID trainingPathId,
      final @Nullable StudyPlanStatus status,
      final @Nullable UUID id) {
    final var parameters = new HashMap<String, Object>();
    parameters.put("institution", institutionId);
    String where = " WHERE item.institution.id = :institution AND item.deletedAt IS NULL";
    if (id != null) {
      where += " AND item.id = :id";
      parameters.put("id", id);
    }
    if (source.pathExpression() != null && !access.institutional()) {
      where += " AND " + source.pathExpression() + " IN :paths";
      parameters.put("paths", access.trainingPathIds());
    }
    if (trainingPathId != null && source.pathExpression() != null) {
      where += " AND " + source.pathExpression() + " = :trainingPath";
      parameters.put("trainingPath", trainingPathId);
    }
    if (source.entity().equals("StudyPlan")) {
      if (status != null) {
        where += " AND item.status = :status";
        parameters.put("status", status);
      }
      if (Boolean.TRUE.equals(published)) {
        where += " AND item.status <> :draft";
        parameters.put("draft", StudyPlanStatus.DRAFT);
      }
    } else if (source.entity().equals("AcademicYear")) {
      if (active != null) {
        where += active ? " AND item.status = :yearStatus" : " AND item.status <> :yearStatus";
        parameters.put("yearStatus", AcademicYearStatus.ACTIVE);
      }
    } else if (active != null) {
      where += " AND item.active = :active";
      parameters.put("active", active);
    }
    if (search != null && !search.isBlank()) {
      where +=
          source.entity().equals("AcademicYear")
              ? " AND cast(item.year as string) LIKE :search"
              : " AND lower(item.name) LIKE :search";
      parameters.put("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
    }

    return new SelectionQuery(" FROM " + source.entity() + " item" + where, parameters);
  }

  private record SelectionQuery(String clause, Map<String, Object> parameters) {}

  private AcademicSelectionOptionResponse option(Object value) {
    if (value instanceof StudyPlan item) {
      return new AcademicSelectionOptionResponse(
          item.getId(),
          item.getName(),
          null,
          item.getTrainingPath().getId(),
          item.getTrainingPath().getName(),
          item.getVersionNumber(),
          null,
          null,
          null);
    }
    if (value instanceof TrainingPath item) {
      return new AcademicSelectionOptionResponse(
          item.getId(), item.getName(), null, item.getId(), item.getName(), null, null, null, null);
    }
    if (value instanceof AcademicYear item) {
      return new AcademicSelectionOptionResponse(
          item.getId(),
          String.valueOf(item.getYear()),
          item.getYear(),
          null,
          null,
          null,
          null,
          null,
          null);
    }
    if (value instanceof AcademicSpace item) {
      return new AcademicSelectionOptionResponse(
          item.getId(),
          item.getName(),
          null,
          null,
          null,
          null,
          item.getType().name(),
          item.getFormat().name(),
          item.isInstrumental());
    }
    final var item = (Instrument) value;
    return new AcademicSelectionOptionResponse(
        item.getId(), item.getName(), null, null, null, null, null, null, null);
  }
}
