package ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentGradeHistoryRepository
    extends JpaRepository<CourseEnrollmentGradeHistory, UUID> {

  @Query(
      "SELECT history FROM CourseEnrollmentGradeHistory history"
          + " WHERE history.institution.id = :institutionId"
          + " AND history.courseEnrollment.id = :enrollmentId"
          + " AND history.gradeId = :gradeId")
  Page<CourseEnrollmentGradeHistory> findByGrade(
      @Param("institutionId") UUID institutionId,
      @Param("enrollmentId") UUID enrollmentId,
      @Param("gradeId") UUID gradeId,
      Pageable pageable);

  @Query(
      "SELECT history FROM CourseEnrollmentGradeHistory history"
          + " WHERE history.institution.id = :institutionId"
          + " AND history.courseEnrollment.id = :enrollmentId"
          + " AND history.gradeId = :gradeId"
          + " ORDER BY history.changedAt DESC, history.id DESC")
  List<CourseEnrollmentGradeHistory> findByGradeOrdered(
      @Param("institutionId") UUID institutionId,
      @Param("enrollmentId") UUID enrollmentId,
      @Param("gradeId") UUID gradeId);

  @Query(
      "SELECT history FROM CourseEnrollmentGradeHistory history"
          + " WHERE history.institution.id = :institutionId"
          + " AND history.courseEnrollment.id = :enrollmentId"
          + " ORDER BY history.changedAt DESC, history.id DESC")
  List<CourseEnrollmentGradeHistory> findByEnrollmentOrdered(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);

  @Query(
      "SELECT DISTINCT history.gradeId FROM CourseEnrollmentGradeHistory history"
          + " WHERE history.institution.id = :institutionId"
          + " AND history.courseEnrollment.id = :enrollmentId")
  List<UUID> findGradeIdsByEnrollment(
      @Param("institutionId") UUID institutionId, @Param("enrollmentId") UUID enrollmentId);
}
