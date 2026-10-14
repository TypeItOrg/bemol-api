package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface CourseEnrollmentQueries extends Repository<Course, UUID> {
  @Query(
      """
      SELECT COUNT(course) > 0 FROM Course course
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection
          WHERE selection.offering.period.id = :periodId
            AND selection.offering.period.scopeConfigured = true
            AND selection.offering.studyPlan.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.active = true
            AND selection.offering.period.academicYear.id = course.academicYear.id
            AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
            AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
              OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL))
      """)
  boolean existsOfferedForPeriod(UUID institutionId, UUID trainingPathId, UUID periodId);

  @EntityGraph(
      attributePaths = {
        "studyPlanSpace",
        "studyPlanSpace.studyPlan",
        "studyPlanSpace.studyPlan.trainingPath",
        "studyPlanSpace.academicSpace",
        "studyPlanSpace.academicLevel",
        "academicYear",
        "instrument"
      })
  @Query(
      """
      SELECT course FROM Course course
      LEFT JOIN course.instrument instrument
      LEFT JOIN course.studyPlanSpace.academicLevel level
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection
          WHERE selection.offering.period.id = :periodId
            AND selection.offering.period.scopeConfigured = true
            AND selection.offering.studyPlan.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.active = true
            AND selection.offering.period.academicYear.id = course.academicYear.id
            AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
            AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
              OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL))
        AND (:search IS NULL OR UNACCENT_LOWER(course.studyPlanSpace.academicSpace.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(course.studyPlanSpace.studyPlan.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(COALESCE(instrument.name, '')) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      ORDER BY level.displayOrder NULLS LAST, course.studyPlanSpace.academicSpace.name, course.id
      """)
  Page<Course> findOfferedForPeriod(
      UUID institutionId,
      UUID trainingPathId,
      UUID periodId,
      @Nullable String search,
      Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "studyPlanSpace",
        "studyPlanSpace.studyPlan",
        "studyPlanSpace.studyPlan.trainingPath",
        "studyPlanSpace.academicSpace",
        "studyPlanSpace.academicLevel",
        "academicYear",
        "instrument"
      })
  @Query(
      """
      SELECT course FROM Course course
      LEFT JOIN course.instrument instrument
      LEFT JOIN course.studyPlanSpace.academicLevel level
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL AND EXISTS (SELECT selection.id FROM EnrollmentPeriodOfferingLevel selection
          WHERE (:periodId IS NULL OR selection.offering.period.id = :periodId)
            AND selection.offering.period.status = ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus.OPEN
            AND selection.offering.period.deletedAt IS NULL
            AND selection.offering.period.startDate <= :now AND selection.offering.period.endDate >= :now
            AND selection.offering.period.scopeConfigured = true
            AND selection.offering.studyPlan.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.deletedAt IS NULL
            AND selection.offering.studyPlan.trainingPath.active = true
            AND selection.offering.period.academicYear.id = course.academicYear.id
            AND selection.offering.studyPlan.id = course.studyPlanSpace.studyPlan.id
            AND (selection.academicLevel.id = course.studyPlanSpace.academicLevel.id
              OR selection.academicLevel IS NULL AND course.studyPlanSpace.academicLevel IS NULL))
        AND (:studyPlanSpaceId IS NULL OR course.studyPlanSpace.id = :studyPlanSpaceId)
        AND (:academicYear IS NULL OR course.academicYear.year = :academicYear)
        AND NOT EXISTS (SELECT requested.id FROM EnrollmentApplicationCourse requested
          WHERE requested.institution.id = :institutionId
            AND requested.course.id = course.id
            AND requested.enrollmentApplication.id <> :applicationId
            AND requested.enrollmentApplication.applicantPerson.id = :personId
            AND requested.enrollmentApplication.deletedAt IS NULL
            AND requested.enrollmentApplication.status NOT IN (
              ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus.CANCELLED,
              ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus.REJECTED)
            AND requested.status IN (
              ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus.PENDING,
              ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationCourseStatus.WAITLISTED))
        AND NOT EXISTS (SELECT enrollment.id FROM CourseEnrollment enrollment
          WHERE enrollment.institution.id = :institutionId
            AND enrollment.course.id = course.id
            AND enrollment.student.person.id = :personId
            AND enrollment.status = ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus.ENROLLED)
        AND (:search IS NULL OR UNACCENT_LOWER(course.studyPlanSpace.academicSpace.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(course.studyPlanSpace.studyPlan.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(COALESCE(instrument.name, '')) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      ORDER BY level.displayOrder NULLS LAST, course.studyPlanSpace.academicSpace.name, course.id
      """)
  Page<Course> findOpenForEnrollment(
      UUID institutionId,
      UUID trainingPathId,
      UUID applicationId,
      UUID personId,
      @Nullable UUID periodId,
      Instant now,
      @Nullable UUID studyPlanSpaceId,
      @Nullable Integer academicYear,
      @Nullable String search,
      Pageable pageable);
}
