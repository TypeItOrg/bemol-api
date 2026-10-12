package ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentPeriodRepository
    extends JpaRepository<EnrollmentPeriod, UUID>, JpaSpecificationExecutor<EnrollmentPeriod> {

  Optional<EnrollmentPeriod> findByIdAndInstitutionIdAndDeletedAtIsNull(
      UUID id, UUID institutionId);

  @Query(
      """
SELECT DISTINCT offering.studyPlan.id
FROM EnrollmentPeriodOfferingLevel selection
JOIN selection.offering offering
JOIN offering.period period
WHERE period.institution.id = :institutionId
  AND offering.studyPlan.institution.id = :institutionId
  AND offering.studyPlan.id IN :studyPlanIds
  AND period.deletedAt IS NULL
  AND period.scopeConfigured = true
  AND period.status = ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus.OPEN
  AND period.startDate <= :now AND period.endDate >= :now
""")
  Set<UUID> findStudyPlanIdsWithOpenEnrollment(
      UUID institutionId, List<UUID> studyPlanIds, Instant now);

  @Query(
      "SELECT ep FROM EnrollmentPeriod ep WHERE ep.institution.id = :institutionId "
          + "AND ep.academicYear.id = :academicYearId "
          + "AND ep.status = :status "
          + "AND ep.startDate <= :now AND ep.endDate >= :now "
          + "AND ep.deletedAt IS NULL ORDER BY ep.startDate DESC, ep.id DESC")
  List<EnrollmentPeriod> findActivePeriods(
      @Param("institutionId") UUID institutionId,
      @Param("academicYearId") UUID academicYearId,
      @Param("status") EnrollmentPeriodStatus status,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      "SELECT ep FROM EnrollmentPeriod ep WHERE ep.institution.id = :institutionId "
          + "AND ep.status = :status "
          + "AND ep.startDate <= :now AND ep.endDate >= :now "
          + "AND ep.deletedAt IS NULL")
  Page<EnrollmentPeriod> findAvailableForEnrollment(
      @Param("institutionId") UUID institutionId,
      @Param("status") EnrollmentPeriodStatus status,
      @Param("now") Instant now,
      Pageable pageable);

  default Optional<EnrollmentPeriod> findActivePeriod(
      UUID institutionId, UUID academicYearId, EnrollmentPeriodStatus status, Instant now) {
    return findActivePeriods(institutionId, academicYearId, status, now, Pageable.ofSize(1))
        .stream()
        .findFirst();
  }

  @Query(
      """
SELECT ep FROM EnrollmentPeriod ep
WHERE ep.institution.id = :institutionId AND ep.status = ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus.OPEN
  AND ep.deletedAt IS NULL AND ep.scopeConfigured = true
  AND ep.academicYear.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus.ACTIVE
  AND (:academicYearId IS NULL OR ep.academicYear.id = :academicYearId)
  AND (:search IS NULL OR UNACCENT_LOWER(ep.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
  AND ep.startDate <= :now AND ep.endDate >= :now AND EXISTS (SELECT course.id FROM Course course
        WHERE course.institution.id = ep.institution.id
          AND course.academicYear.id = ep.academicYear.id
          AND course.studyPlanSpace.studyPlan.trainingPath.active = true
          AND course.studyPlanSpace.studyPlan.trainingPath.deletedAt IS NULL
          AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
          AND course.deletedAt IS NULL
          AND (:trainingPathId IS NULL OR course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId)
          AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection
    WHERE selection.offering.period.id = ep.id
      AND selection.offering.period.scopeConfigured = true
      AND selection.offering.studyPlan.deletedAt IS NULL
      AND selection.offering.studyPlan.trainingPath.deletedAt IS NULL
      AND selection.offering.studyPlan.trainingPath.active = true
      AND selection.offering.period.academicYear.id = course.academicYear.id
      AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
      AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
        OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL)))
""")
  Page<EnrollmentPeriod> findAvailableOffers(
      UUID institutionId,
      Instant now,
      @Nullable UUID trainingPathId,
      @Nullable UUID academicYearId,
      @Nullable String search,
      Pageable pageable);

  @Query(
      """
SELECT ep FROM EnrollmentPeriod ep WHERE ep.institution.id = :institutionId
  AND ep.scopeConfigured = true AND ep.deletedAt IS NULL
  AND ep.status = ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus.OPEN
  AND ep.startDate <= :now AND ep.endDate >= :now
  AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection, Course course
    WHERE selection.offering.period.id = ep.id AND course.id = :courseId
      AND course.institution.id = ep.institution.id AND course.academicYear.id = ep.academicYear.id
      AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
      AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
        OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL))
""")
  List<EnrollmentPeriod> findOpenForCourse(UUID institutionId, UUID courseId, Instant now);
}
