package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueryEnrollmentApplicationsUseCase {
  private final EnrollmentApplicationRepository applications;
  private final EnrollmentApplicationResponseFactory responses;
  private final ScopedAuthorizationService authorization;

  @Transactional(readOnly = true)
  public PaginatedResponse<EnrollmentApplicationResponse> list(
      final UUID institutionId,
      final @Nullable UUID periodId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable String search,
      final Pageable pageable) {
    final var page =
        applications.findAll(filters(institutionId, periodId, status, search), pageable);

    return PaginatedResponse.from(page.map(responses::from));
  }

  @Transactional(readOnly = true)
  public EnrollmentApplicationResponse get(
      final @Nullable UUID institutionId, final @Nullable UUID personId, final UUID applicationId) {
    final var application =
        applications
            .findById(applicationId)
            .filter(value -> value.getDeletedAt() == null)
            .filter(
                value ->
                    personId != null && value.getApplicantPerson().getId().equals(personId)
                        || institutionId != null
                            && value.getInstitution().getId().equals(institutionId))
            .orElseThrow(() -> new EnrollmentApplicationNotFoundException(applicationId));
    if (personId == null || !application.getApplicantPerson().getId().equals(personId)) {
      authorization.require(
          PermissionCode.ENROLLMENT_APPLICATION_READ,
          application.getInstitution().getId(),
          application.getTrainingPathId());
    }

    return responses.from(application);
  }

  private Specification<EnrollmentApplication> filters(
      final UUID institutionId,
      final @Nullable UUID periodId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable String search) {
    return (root, query, cb) -> {
      final var predicates = new ArrayList<Predicate>();
      final var access = authorization.managementAccess(PermissionCode.ENROLLMENT_APPLICATION_READ);
      if (!access.institutional()) {
        predicates.add(root.get("trainingPathId").in(access.trainingPathIds()));
      }
      predicates.add(cb.equal(root.get("institution").get("id"), institutionId));
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (periodId != null) {
        final var selectionQuery = query.subquery(UUID.class);
        final var selection = selectionQuery.from(EnrollmentApplicationCourse.class);
        selectionQuery
            .select(selection.get("id"))
            .where(
                cb.equal(selection.get("enrollmentApplication").get("id"), root.get("id")),
                cb.equal(selection.get("enrollmentPeriod").get("id"), periodId));
        predicates.add(
            cb.or(
                cb.equal(root.get("enrollmentPeriod").get("id"), periodId),
                cb.exists(selectionQuery)));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }

      final var normalizedSearch = SearchNormalization.normalizeSearch(search);
      if (normalizedSearch != null) {
        final var pattern = SearchNormalization.likeContainsPattern(normalizedSearch);
        final var person = root.join("applicantPerson");
        predicates.add(
            cb.or(
                cb.like(
                    SearchNormalization.unaccentLower(cb, person.get("firstName")), pattern, '\\'),
                cb.like(
                    SearchNormalization.unaccentLower(cb, person.get("lastName")), pattern, '\\'),
                cb.like(
                    SearchNormalization.unaccentLower(cb, person.get("documentNumber")),
                    pattern,
                    '\\')));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
