package ar.edu.utn.frvm.typeit.boero_api.academic.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface CourseLifecycleQueries extends Repository<Course, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT course FROM Course course WHERE course.id = :id AND course.institution.id = :institutionId AND course.deletedAt IS NULL")
  Optional<Course> findByIdAndInstitution_IdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT course FROM Course course WHERE course.id = :id AND course.institution.id = :institutionId")
  Optional<Course> findByIdAndInstitution_IdForLifecycle(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      """
      SELECT course.academicYear.id AS academicYearId, course.studyPlanSpace.studyPlan.id AS studyPlanId
      FROM Course course
      WHERE course.id = :courseId AND course.institution.id = :institutionId
      """)
  Optional<CourseAcademicContext> findAcademicContextByIdAndInstitution_Id(
      @Param("courseId") UUID courseId, @Param("institutionId") UUID institutionId);

  interface CourseAcademicContext {
    UUID getAcademicYearId();

    UUID getStudyPlanId();
  }

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM courses WHERE institution_id = :institutionId AND academic_space_id = :academicSpaceId AND academic_year_id = :academicYearId AND deleted_at IS NULL)",
      nativeQuery = true)
  boolean existsByInstitutionAndSpaceAndYear(
      @Param("institutionId") UUID institutionId,
      @Param("academicSpaceId") UUID academicSpaceId,
      @Param("academicYearId") UUID academicYearId);

  @Query(
      """
      SELECT COUNT(course) > 0 FROM Course course
      WHERE course.institution.id = :institutionId
        AND course.instrument.id = :instrumentId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL
      """)
  boolean existsActiveByInstrument(
      @Param("institutionId") UUID institutionId, @Param("instrumentId") UUID instrumentId);

  @Query(
      """
      SELECT COUNT(course) > 0 FROM Course course
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.trainingPath.id = :trainingPathId
        AND course.status = ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus.ACTIVE
        AND course.deletedAt IS NULL
      """)
  boolean existsActiveByTrainingPath(
      @Param("institutionId") UUID institutionId, @Param("trainingPathId") UUID trainingPathId);

  @Query(
      "SELECT COUNT(course) > 0 FROM Course course WHERE course.studyPlanSpace.id = :studyPlanSpaceId")
  boolean existsByStudyPlanSpaceId(@Param("studyPlanSpaceId") UUID studyPlanSpaceId);

  @Query(
      """
      SELECT COUNT(course) > 0 FROM Course course
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.id = :studyPlanId
        AND course.status = :status
        AND course.deletedAt IS NULL
      """)
  boolean existsByInstitution_IdAndStudyPlan_IdAndStatusAndDeletedAtIsNull(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanId") UUID studyPlanId,
      @Param("status") CourseStatus status);

  @Query(
      """
      SELECT COUNT(course) > 0 FROM Course course
      WHERE course.institution.id = :institutionId
        AND course.studyPlanSpace.studyPlan.id = :studyPlanId
        AND course.status <> :status
        AND course.deletedAt IS NULL
      """)
  boolean existsByInstitution_IdAndStudyPlan_IdAndStatusNotAndDeletedAtIsNull(
      @Param("institutionId") UUID institutionId,
      @Param("studyPlanId") UUID studyPlanId,
      @Param("status") CourseStatus status);

  default boolean existsByInstitution_IdAndStudyPlan_IdAndActiveTrueAndDeletedAtIsNull(
      final UUID institutionId, final UUID studyPlanId) {
    return existsByInstitution_IdAndStudyPlan_IdAndStatusAndDeletedAtIsNull(
        institutionId, studyPlanId, CourseStatus.ACTIVE);
  }

  default boolean existsByInstitution_IdAndStudyPlan_IdAndStatusNotClosedAndDeletedAtIsNull(
      final UUID institutionId, final UUID studyPlanId) {
    return existsByInstitution_IdAndStudyPlan_IdAndStatusNotAndDeletedAtIsNull(
        institutionId, studyPlanId, CourseStatus.CLOSED);
  }

  List<Course> findByAcademicYear_IdAndInstitution_Id(UUID academicYearId, UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<Course> findByAcademicYear_IdAndInstitution_IdAndDeletedAtIsNull(
      UUID academicYearId, UUID institutionId);

  List<Course> findByAcademicYear_Id(UUID academicYearId);

  @Query(
      "SELECT COUNT(c) FROM Course c WHERE c.institution.id = :institutionId AND c.academicYear.id = :academicYearId AND c.deletedAt IS NULL AND c.status != :excludedStatus AND (:#{@scopedAuthorization.unrestricted('COURSE_READ')} = true OR c.studyPlanSpace.studyPlan.trainingPath.id IN :#{@scopedAuthorization.paths('COURSE_READ')})")
  long countByInstitutionIdAndAcademicYearIdAndStatusNot(
      @Param("institutionId") UUID institutionId,
      @Param("academicYearId") UUID academicYearId,
      @Param("excludedStatus") CourseStatus excludedStatus);

  @Query("SELECT c FROM Course c WHERE c.academicYear.id = :academicYearId")
  List<Course> findAllByAcademicYearId(@Param("academicYearId") UUID academicYearId);
}
