package ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentGradeRepository
    extends JpaRepository<CourseEnrollmentGrade, UUID> {

  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade WHERE grade.id = :id AND grade.institution.id = :institutionId")
  Optional<CourseEnrollmentGrade> findByIdAndInstitutionId(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade WHERE grade.id = :id AND grade.institution.id = :institutionId")
  Optional<CourseEnrollmentGrade> findByIdAndInstitutionIdForUpdate(
      @Param("id") UUID id, @Param("institutionId") UUID institutionId);

  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.id = :enrollmentId"
          + " ORDER BY grade.createdAt DESC, grade.id DESC")
  List<CourseEnrollmentGrade> findByEnrollment(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.id = :enrollmentId")
  List<CourseEnrollmentGrade> findByEnrollmentForUpdate(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);

  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.id = :enrollmentId"
          + " AND grade.publishedEvaluation IS NOT NULL"
          + " ORDER BY grade.publishedAt DESC NULLS LAST, grade.id DESC")
  List<CourseEnrollmentGrade> findPublishedByEnrollment(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.courseClass.id = :classId")
  List<CourseEnrollmentGrade> findByClassForUpdate(
      @Param("institutionId") UUID institutionId, @Param("classId") UUID classId);

  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.courseClass.id = :classId"
          + " AND (grade.pendingDeletion = true OR grade.publishedEvaluation IS NULL"
          + " OR grade.evaluation <> grade.publishedEvaluation OR grade.value <> grade.publishedValue)")
  List<CourseEnrollmentGrade> findPendingByClass(
      @Param("institutionId") UUID institutionId, @Param("classId") UUID classId);

  @Query(
      "SELECT COUNT(grade), COUNT(DISTINCT grade.courseEnrollment.id) FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.courseClass.id = :classId"
          + " AND (grade.pendingDeletion = true OR grade.publishedEvaluation IS NULL"
          + " OR grade.evaluation <> grade.publishedEvaluation OR grade.value <> grade.publishedValue)")
  List<Object[]> pendingSummaryByClass(
      @Param("institutionId") UUID institutionId, @Param("classId") UUID classId);

  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " JOIN FETCH grade.courseEnrollment enrollment"
          + " JOIN FETCH enrollment.courseClass courseClass"
          + " JOIN FETCH courseClass.course course"
          + " WHERE grade.institution.id = :institutionId"
          + " AND (:#{@scopedAuthorization.unrestricted('COURSE_ENROLLMENT_GRADE_PUBLISH')} = true"
          + " OR enrollment.course.studyPlanSpace.studyPlan.trainingPath.id IN :#{@scopedAuthorization.paths('COURSE_ENROLLMENT_GRADE_PUBLISH')})"
          + " AND (grade.pendingDeletion = true OR grade.publishedEvaluation IS NULL"
          + " OR grade.evaluation <> grade.publishedEvaluation OR grade.value <> grade.publishedValue)")
  List<CourseEnrollmentGrade> findPendingDetails(@Param("institutionId") UUID institutionId);

  @Query(
      "SELECT COUNT(grade) > 0 FROM CourseEnrollmentGrade grade"
          + " WHERE grade.courseEnrollment.id = :enrollmentId"
          + " AND LOWER(TRIM(BOTH ' ' FROM grade.evaluation)) = LOWER(TRIM(BOTH ' ' FROM :evaluation))"
          + " AND (:excludedId IS NULL OR grade.id <> :excludedId)")
  boolean existsDuplicateEvaluation(
      @Param("enrollmentId") UUID enrollmentId,
      @Param("evaluation") String evaluation,
      @Param("excludedId") @Nullable UUID excludedId);
}
