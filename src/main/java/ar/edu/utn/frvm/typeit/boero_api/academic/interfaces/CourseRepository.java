package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository
    extends JpaRepository<Course, UUID>, CourseLifecycleQueries, CourseEnrollmentQueries {
  @EntityGraph(
      attributePaths = {
        "institution",
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
      LEFT JOIN course.studyPlanSpace.academicLevel level
      LEFT JOIN course.instrument instrument
      WHERE (:#{@scopedAuthorization.unrestricted('COURSE_READ')} = true OR course.studyPlanSpace.studyPlan.trainingPath.id IN :#{@scopedAuthorization.paths('COURSE_READ')})
        AND (:institutionId IS NULL OR course.institution.id = :institutionId)
        AND ((:deleted = true AND course.deletedAt IS NOT NULL) OR (:deleted = false AND course.deletedAt IS NULL))
        AND (:status IS NULL OR course.status = :status)
        AND (:academicSpaceId IS NULL OR course.academicSpaceId = :academicSpaceId)
        AND (:trainingPathId IS NULL OR course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId)
        AND (:studyPlanId IS NULL OR course.studyPlanSpace.studyPlan.id = :studyPlanId)
        AND (:year IS NULL OR course.academicYear.year = :year)
        AND (:academicLevelId IS NULL OR level.id = :academicLevelId)
        AND (:type IS NULL OR course.studyPlanSpace.academicSpace.type = :type)
        AND (:format IS NULL OR course.studyPlanSpace.academicSpace.format = :format)
        AND (:instrumentId IS NULL OR instrument.id = :instrumentId)
        AND (:withoutInstrument = false OR instrument.id IS NULL)
        AND (:search IS NULL OR UNACCENT_LOWER(course.studyPlanSpace.academicSpace.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(course.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<Course> findByFilters(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("status") @Nullable CourseStatus status,
      @Param("academicSpaceId") @Nullable UUID academicSpaceId,
      @Param("trainingPathId") @Nullable UUID trainingPathId,
      @Param("studyPlanId") @Nullable UUID studyPlanId,
      @Param("year") @Nullable Integer year,
      @Param("academicLevelId") @Nullable UUID academicLevelId,
      @Param("type") @Nullable AcademicSpaceType type,
      @Param("format") @Nullable AcademicSpaceFormat format,
      @Param("instrumentId") @Nullable UUID instrumentId,
      @Param("withoutInstrument") boolean withoutInstrument,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "institution",
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
      WHERE (:#{@scopedAuthorization.enrollmentOptionsUnrestricted()} = true OR course.studyPlanSpace.studyPlan.trainingPath.id IN :#{@scopedAuthorization.enrollmentOptionsPaths()})
        AND (:institutionId IS NULL OR course.institution.id = :institutionId)
        AND ((:deleted = true AND course.deletedAt IS NOT NULL) OR (:deleted = false AND course.deletedAt IS NULL))
        AND (:status IS NULL OR course.status = :status)
        AND (:academicSpaceId IS NULL OR course.academicSpaceId = :academicSpaceId)
        AND (:trainingPathId IS NULL OR course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId)
        AND (:studyPlanId IS NULL OR course.studyPlanSpace.studyPlan.id = :studyPlanId)
        AND (:year IS NULL OR course.academicYear.year = :year)
        AND (:search IS NULL OR UNACCENT_LOWER(course.studyPlanSpace.academicSpace.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(course.institution.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      """)
  Page<Course> findEnrollmentOptions(
      @Param("institutionId") @Nullable UUID institutionId,
      @Param("search") @Nullable String search,
      @Param("status") @Nullable CourseStatus status,
      @Param("academicSpaceId") @Nullable UUID academicSpaceId,
      @Param("trainingPathId") @Nullable UUID trainingPathId,
      @Param("studyPlanId") @Nullable UUID studyPlanId,
      @Param("year") @Nullable Integer year,
      @Param("deleted") boolean deleted,
      Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "institution",
        "studyPlanSpace",
        "studyPlanSpace.studyPlan",
        "studyPlanSpace.studyPlan.trainingPath",
        "studyPlanSpace.academicSpace",
        "academicYear",
        "instrument"
      })
  @Query(
      "SELECT course FROM Course course WHERE course.id = :id AND course.institution.id = :institutionId AND course.deletedAt IS NULL")
  Optional<Course> findByIdAndInstitution_Id(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

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
      LEFT JOIN course.studyPlanSpace.academicLevel level
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.academicYear.id = :academicYearId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL
      ORDER BY level.displayOrder NULLS LAST,
               course.studyPlanSpace.academicSpace.name,
               course.id
      """)
  List<Course> findActiveByTrainingPathAndAcademicYear(
      @Param("institutionId") UUID institutionId,
      @Param("trainingPathId") UUID trainingPathId,
      @Param("academicYearId") UUID academicYearId);

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
  // An explicit LEFT JOIN keeps courses without an instrument in unfiltered searches.
  // Navigating course.instrument.name would silently introduce an INNER JOIN instead.
  @Query(
      """
      SELECT course FROM Course course
      LEFT JOIN course.instrument instrument
      LEFT JOIN course.studyPlanSpace.academicLevel level
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.academicYear.id = :academicYearId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL
        AND (:search IS NULL OR UNACCENT_LOWER(course.studyPlanSpace.academicSpace.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(course.studyPlanSpace.studyPlan.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(COALESCE(instrument.name, '')) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%')))
      ORDER BY level.displayOrder NULLS LAST,
               course.studyPlanSpace.academicSpace.name,
               course.id
      """)
  Page<Course> findActiveByTrainingPathAndAcademicYear(
      @Param("institutionId") UUID institutionId,
      @Param("trainingPathId") UUID trainingPathId,
      @Param("academicYearId") UUID academicYearId,
      @Param("search") @Nullable String search,
      Pageable pageable);
}
