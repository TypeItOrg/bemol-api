package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentScheduleRepository
    extends JpaRepository<CourseEnrollmentSchedule, UUID> {

  @Query(
      """
      SELECT assignment FROM CourseEnrollmentSchedule assignment
      WHERE assignment.institution.id = :institutionId
        AND assignment.courseEnrollment.student.person.id = :personId
        AND assignment.createdAt < :until
        AND assignment.courseEnrollment.enrolledAt < :until
        AND (assignment.releasedAt IS NULL OR assignment.releasedAt > :since)
        AND (assignment.courseEnrollment.completedAt IS NULL OR assignment.courseEnrollment.completedAt > :since)
        AND (assignment.courseEnrollment.withdrawnAt IS NULL OR assignment.courseEnrollment.withdrawnAt > :since)
        AND assignment.courseEnrollment.course.academicYear.startDate <= :weekEnd
        AND assignment.courseEnrollment.course.academicYear.endDate >= :weekStart
      ORDER BY assignment.dayOfWeek, assignment.startTime, assignment.id
      """)
  @EntityGraph(
      attributePaths = {
        "courseEnrollment.student.person",
        "courseEnrollment.courseClass",
        "courseEnrollment.course.academicYear",
        "courseEnrollment.course.instrument",
        "courseEnrollment.course.studyPlanSpace.academicSpace",
        "courseEnrollment.course.studyPlanSpace.academicLevel",
        "courseEnrollment.course.studyPlanSpace.studyPlan.trainingPath"
      })
  List<CourseEnrollmentSchedule> findOwnInWeek(
      @Param("institutionId") UUID institutionId,
      @Param("personId") UUID personId,
      @Param("weekStart") LocalDate weekStart,
      @Param("weekEnd") LocalDate weekEnd,
      @Param("since") Instant since,
      @Param("until") Instant until);

  List<CourseEnrollmentSchedule> findByCourseEnrollment_Id(UUID courseEnrollmentId);

  List<CourseEnrollmentSchedule> findByCourseEnrollment_IdIn(List<UUID> courseEnrollmentIds);

  @Query(
      "SELECT assignment FROM CourseEnrollmentSchedule assignment WHERE assignment.institution.id = :institutionId AND assignment.releasedAt IS NULL AND assignment.courseEnrollment.student.id = :studentId")
  @EntityGraph(attributePaths = {"courseEnrollment", "courseEnrollment.course.academicYear"})
  List<CourseEnrollmentSchedule> findActiveByStudent(
      @Param("institutionId") UUID institutionId, @Param("studentId") UUID studentId);

  @Query(
      "SELECT assignment FROM CourseEnrollmentSchedule assignment WHERE assignment.institution.id = :institutionId AND assignment.releasedAt IS NULL AND assignment.schedule.id = :scheduleId")
  List<CourseEnrollmentSchedule> findActiveBySchedule(
      @Param("institutionId") UUID institutionId, @Param("scheduleId") UUID scheduleId);

  @Query(
      "SELECT assignment FROM CourseEnrollmentSchedule assignment WHERE assignment.institution.id = :institutionId AND assignment.releasedAt IS NULL AND assignment.schedule.day.id = :dayId")
  List<CourseEnrollmentSchedule> findActiveByDay(
      @Param("institutionId") UUID institutionId, @Param("dayId") UUID dayId);

  @Query(
      "SELECT assignment FROM CourseEnrollmentSchedule assignment WHERE assignment.institution.id = :institutionId AND assignment.releasedAt IS NULL AND assignment.schedule.day.id IN :dayIds")
  List<CourseEnrollmentSchedule> findActiveByDays(
      @Param("institutionId") UUID institutionId, @Param("dayIds") List<UUID> dayIds);

  @Query(
      "SELECT assignment FROM CourseEnrollmentSchedule assignment WHERE assignment.institution.id = :institutionId AND assignment.releasedAt IS NULL AND assignment.individualSlot.id = :slotId")
  List<CourseEnrollmentSchedule> findActiveByIndividualSlot(
      @Param("institutionId") UUID institutionId, @Param("slotId") UUID slotId);
}
