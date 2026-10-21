package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListStudyPlansUseCase {
  private static final Set<String> DATE_SORT_FIELDS = Set.of("effectiveFrom", "effectiveTo");

  private final StudyPlanRepository studyPlanRepository;
  private final ScopedAuthorizationService scopedAuthorization;

  @Transactional(readOnly = true)
  public PaginatedResponse<StudyPlanResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable StudyPlanStatus status,
      final @Nullable UUID trainingPathId,
      final @Nullable LocalDate validOn,
      final boolean deleted,
      final Pageable pageable) {
    return PaginatedResponse.from(
        studyPlanRepository
            .findAll(
                byFilters(
                    institutionId,
                    AcademicNameNormalizer.search(search),
                    status,
                    trainingPathId,
                    validOn,
                    deleted),
                mapDateSort(pageable))
            .map(StudyPlanResponse::from));
  }

  public PaginatedResponse<StudyPlanResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable StudyPlanStatus status,
      final Pageable pageable) {
    return execute(institutionId, search, status, null, null, false, pageable);
  }

  public PaginatedResponse<StudyPlanResponse> execute(
      final UUID institutionId,
      final @Nullable String search,
      final @Nullable StudyPlanStatus status,
      final @Nullable UUID trainingPathId,
      final @Nullable LocalDate validOn,
      final Pageable pageable) {
    return execute(institutionId, search, status, trainingPathId, validOn, false, pageable);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<StudyPlanResponse> published(
      final @Nullable UUID institutionId,
      final @Nullable UUID trainingPathId,
      final @Nullable String search,
      final Pageable pageable) {
    return PaginatedResponse.from(
        studyPlanRepository
            .findPublished(
                institutionId,
                trainingPathId,
                search == null || search.isBlank() ? null : search.trim(),
                pageable)
            .map(StudyPlanResponse::from));
  }

  private Specification<StudyPlan> byFilters(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable StudyPlanStatus status,
      final @Nullable UUID trainingPathId,
      final @Nullable LocalDate validOn,
      final boolean deleted) {
    return (root, query, criteriaBuilder) -> {
      final List<Predicate> predicates = new ArrayList<>();
      var access = scopedAuthorization.managementAccess(PermissionCode.STUDY_PLAN_READ);
      if (!access.institutional()) {
        predicates.add(root.get("trainingPath").get("id").in(access.trainingPathIds()));
      }
      if (institutionId != null) {
        predicates.add(criteriaBuilder.equal(root.get("institution").get("id"), institutionId));
      }
      predicates.add(
          deleted
              ? criteriaBuilder.isNotNull(root.get("deletedAt"))
              : criteriaBuilder.isNull(root.get("deletedAt")));
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status));
      }
      if (trainingPathId != null) {
        predicates.add(criteriaBuilder.equal(root.get("trainingPath").get("id"), trainingPathId));
      }
      if (validOn != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("effectiveFrom"), validOn));
        predicates.add(
            criteriaBuilder.or(
                criteriaBuilder.isNull(root.get("effectiveTo")),
                criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveTo"), validOn)));
      }
      if (search != null) {
        final var normalizedName =
            SearchNormalization.unaccentLower(criteriaBuilder, root.get("name"));
        final var normalizedSearch =
            SearchNormalization.unaccentLower(
                criteriaBuilder, criteriaBuilder.literal("%" + search + "%"));
        final var normalizedInstitutionName =
            SearchNormalization.unaccentLower(criteriaBuilder, root.get("institution").get("name"));
        predicates.add(
            criteriaBuilder.or(
                criteriaBuilder.like(normalizedName, normalizedSearch),
                criteriaBuilder.like(normalizedInstitutionName, normalizedSearch)));
      }
      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Pageable mapDateSort(final Pageable pageable) {
    if (pageable.getSort().isUnsorted()) {
      return pageable;
    }
    final List<Sort.Order> orders =
        pageable.getSort().stream()
            .map(
                order -> DATE_SORT_FIELDS.contains(order.getProperty()) ? order.nullsLast() : order)
            .toList();
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
  }
}
