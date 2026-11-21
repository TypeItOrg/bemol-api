package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.id = :enrollmentId"
          + " ORDER BY grade.id ASC")
  List<CourseEnrollmentGrade> findByEnrollmentForUpdate(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT grade FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.id IN :enrollmentIds"
          + " ORDER BY grade.courseEnrollment.id ASC, grade.id ASC")
  List<CourseEnrollmentGrade> findByEnrollmentIdsForUpdate(
      @Param("institutionId") UUID institutionId,
      @Param("enrollmentIds") Collection<UUID> enrollmentIds);

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
          + " JOIN FETCH grade.courseEnrollment enrollment"
          + " WHERE grade.institution.id = :institutionId AND enrollment.courseClass.id = :classId"
          + " ORDER BY grade.id ASC")
  List<CourseEnrollmentGrade> findByClassForUpdate(
      @Param("institutionId") UUID institutionId, @Param("classId") UUID classId);

  @Query(
      "SELECT new ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository$PendingSummaryProjection("
          + " COUNT(grade.id),"
          + " COUNT(DISTINCT grade.courseEnrollment.id),"
          + " COALESCE(SUM(CASE WHEN grade.pendingDeletion = false AND grade.publishedEvaluation IS NULL THEN 1L ELSE 0L END), 0L),"
          + " COALESCE(SUM(CASE WHEN grade.pendingDeletion = false AND grade.publishedEvaluation IS NOT NULL THEN 1L ELSE 0L END), 0L),"
          + " COALESCE(SUM(CASE WHEN grade.pendingDeletion = true THEN 1L ELSE 0L END), 0L))"
          + " FROM CourseEnrollmentGrade grade"
          + " WHERE grade.institution.id = :institutionId AND grade.courseEnrollment.courseClass.id = :classId"
          + " AND (grade.pendingDeletion = true OR grade.publishedEvaluation IS NULL"
          + " OR grade.evaluation <> grade.publishedEvaluation OR grade.value <> grade.publishedValue)")
  PendingSummaryProjection summarizePendingByClass(
      @Param("institutionId") UUID institutionId, @Param("classId") UUID classId);

  @Query(
      "SELECT new ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository$PendingClassProjection("
          + " course.id,"
          + " academicSpace.name,"
          + " instrument.name,"
          + " academicLevel.name,"
          + " academicYear.year,"
          + " courseClass.id,"
          + " courseClass.classNumber,"
          + " COUNT(grade.id),"
          + " COUNT(DISTINCT enrollment.id))"
          + " FROM CourseEnrollmentGrade grade"
          + " JOIN grade.courseEnrollment enrollment"
          + " JOIN enrollment.courseClass courseClass"
          + " JOIN courseClass.course course"
          + " JOIN course.studyPlanSpace sps"
          + " JOIN sps.academicSpace academicSpace"
          + " LEFT JOIN sps.academicLevel academicLevel"
          + " LEFT JOIN course.instrument instrument"
          + " JOIN course.academicYear academicYear"
          + " WHERE grade.institution.id = :institutionId"
          + " AND (:#{@scopedAuthorization.unrestricted('COURSE_ENROLLMENT_GRADE_PUBLISH')} = true"
          + " OR course.trainingPathId IN :#{@scopedAuthorization.paths('COURSE_ENROLLMENT_GRADE_PUBLISH')})"
          + " AND (grade.pendingDeletion = true OR grade.publishedEvaluation IS NULL"
          + " OR grade.evaluation <> grade.publishedEvaluation OR grade.value <> grade.publishedValue)"
          + " GROUP BY course.id, academicSpace.name, instrument.name, academicLevel.name, academicYear.year,"
          + " courseClass.id, courseClass.classNumber"
          + " ORDER BY LOWER(academicSpace.name) ASC, courseClass.classNumber ASC")
  List<PendingClassProjection> findPendingClasses(@Param("institutionId") UUID institutionId);

  @Query(
      "SELECT COUNT(grade) > 0 FROM CourseEnrollmentGrade grade"
          + " WHERE grade.courseEnrollment.id = :enrollmentId"
          + " AND LOWER(TRIM(BOTH ' ' FROM grade.evaluation)) = LOWER(TRIM(BOTH ' ' FROM :evaluation))"
          + " AND (:excludedId IS NULL OR grade.id <> :excludedId)")
  boolean existsDuplicateEvaluation(
      @Param("enrollmentId") UUID enrollmentId,
      @Param("evaluation") String evaluation,
      @Param("excludedId") @Nullable UUID excludedId);

  record PendingSummaryProjection(
      long pendingCount,
      long affectedStudents,
      long createdCount,
      long modifiedCount,
      long deletedCount) {}

  record PendingClassProjection(
      UUID courseId,
      String academicSpaceName,
      @Nullable String instrumentName,
      @Nullable String academicLevelName,
      int academicYear,
      UUID classId,
      int classNumber,
      long pendingChanges,
      long affectedStudents) {}
}
