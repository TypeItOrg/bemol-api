package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentPeriodsUseCase {
  private final EnrollmentPeriodAccessService periodAccess;

  private static final Map<EnrollmentPeriodStatus, String> STATUS_LABELS =
      Map.of(
          EnrollmentPeriodStatus.PLANNED, "Planificado",
          EnrollmentPeriodStatus.OPEN, "Abierto",
          EnrollmentPeriodStatus.CLOSED, "Cerrado");

  private final EnrollmentPeriodRepository periodRepository;
  private final ScopedAuthorizationService scopedAuthorization;

  @Transactional(readOnly = true)
  public PaginatedResponse<EnrollmentPeriodResponse> execute(
      final UUID institutionId,
      final @Nullable UUID academicYearId,
      final @Nullable EnrollmentPeriodStatus status,
      final @Nullable String search,
      final boolean deleted,
      final Pageable pageable) {
    final var page =
        periodRepository.findAll(
            byFilters(institutionId, academicYearId, status, search, deleted), pageable);

    return PaginatedResponse.from(page.map(periodAccess::response));
  }

  private Specification<EnrollmentPeriod> byFilters(
      final UUID institutionId,
      final @Nullable UUID academicYearId,
      final @Nullable EnrollmentPeriodStatus status,
      final @Nullable String search,
      final boolean deleted) {
    return (root, query, criteriaBuilder) -> {
      final List<Predicate> predicates = new ArrayList<>();
      var access = scopedAuthorization.managementAccess(PermissionCode.ENROLLMENT_PERIOD_READ);
      if (!access.institutional()) {
        var offered = query.subquery(UUID.class);
        var offering = offered.from(EnrollmentPeriodOffering.class);
        offered
            .select(offering.get("id"))
            .where(
                criteriaBuilder.equal(offering.get("period").get("id"), root.get("id")),
                offering
                    .get("studyPlan")
                    .get("trainingPath")
                    .get("id")
                    .in(access.trainingPathIds()));
        predicates.add(criteriaBuilder.exists(offered));
      }
      predicates.add(criteriaBuilder.equal(root.get("institution").get("id"), institutionId));
      predicates.add(
          deleted
              ? criteriaBuilder.isNotNull(root.get("deletedAt"))
              : criteriaBuilder.isNull(root.get("deletedAt")));

      if (academicYearId != null) {
        predicates.add(criteriaBuilder.equal(root.get("academicYear").get("id"), academicYearId));
      }

      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status));
      }

      final String normalizedSearch = SearchNormalization.normalizeSearch(search);

      if (normalizedSearch != null) {
        predicates.add(searchPredicate(root, criteriaBuilder, normalizedSearch));
      }

      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate searchPredicate(
      final Root<EnrollmentPeriod> root,
      final CriteriaBuilder criteriaBuilder,
      final String search) {
    final String pattern = SearchNormalization.likeContainsPattern(search);
    final List<Predicate> matches = new ArrayList<>();
    matches.add(
        like(
            criteriaBuilder,
            SearchNormalization.unaccentLower(criteriaBuilder, root.get("name")),
            pattern));

    final Set<EnrollmentPeriodStatus> matchingStatuses = matchingStatuses(search);

    if (!matchingStatuses.isEmpty()) {
      matches.add(root.get("status").in(matchingStatuses));
    }

    return criteriaBuilder.or(matches.toArray(Predicate[]::new));
  }

  private static Predicate like(
      final CriteriaBuilder criteriaBuilder,
      final Expression<String> expression,
      final String pattern) {
    return criteriaBuilder.like(expression, pattern, '\\');
  }

  private static Set<EnrollmentPeriodStatus> matchingStatuses(final String search) {
    final String normalizedSearch = SearchNormalization.normalizeForComparison(search);

    return Arrays.stream(EnrollmentPeriodStatus.values())
        .filter(
            status ->
                SearchNormalization.normalizeForComparison(status.name()).contains(normalizedSearch)
                    || SearchNormalization.normalizeForComparison(
                            requireNonNull(STATUS_LABELS.get(status)))
                        .contains(normalizedSearch))
        .collect(Collectors.toUnmodifiableSet());
  }
}
