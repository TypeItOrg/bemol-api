package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentHistory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentHistoryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationCourseRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseClosureService {

  private final CourseRepository courseRepository;
  private final EnrollmentApplicationCourseRepository applicationCourseRepository;
  private final CourseEnrollmentRepository enrollmentRepository;
  private final CourseEnrollmentScheduleRepository scheduleRepository;
  private final CourseEnrollmentHistoryRepository historyRepository;
  private final Clock clock;
  private final CourseEnrollmentAudit audit;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;

  @Transactional
  public void close(
      final UUID institutionId, final UUID courseId, final @Nullable UUID authorityPersonId) {
    enrollmentInstitutionLock.lock(institutionId);
    final var course =
        courseRepository
            .findByIdAndInstitution_IdForLifecycle(courseId, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    for (final var selection :
        applicationCourseRepository.findByCourseIdAndInstitutionId(courseId, institutionId)) {
      if (selection.getEnrollmentApplication().getStatus() == EnrollmentApplicationStatus.DRAFT) {
        applicationCourseRepository.delete(selection);
        audit.recordSelection(selection, selection.getStatus(), true);
      } else if (selection.getStatus() == EnrollmentApplicationCourseStatus.PENDING
          || selection.getStatus() == EnrollmentApplicationCourseStatus.WAITLISTED) {
        final var previousStatus = selection.getStatus();
        selection.reject(
            "COURSE_FINISHED", "El curso fue finalizado.", clock.instant(), authorityPersonId);
        audit.recordSelection(selection, previousStatus, false);
      }
    }

    final var enrollments =
        enrollmentRepository.findByCourseAndStatus(
            institutionId, courseId, CourseEnrollmentStatus.ENROLLED);
    final Map<UUID, List<CourseEnrollmentSchedule>> schedulesByEnrollment =
        enrollments.isEmpty()
            ? Map.of()
            : scheduleRepository
                .findByCourseEnrollment_IdIn(
                    enrollments.stream()
                        .map(mappedCourseEnrollment -> mappedCourseEnrollment.getId())
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getCourseEnrollment().getId()));
    final List<CourseEnrollmentHistory> histories = new ArrayList<>();
    for (final CourseEnrollment enrollment : enrollments) {
      final var previousAcademicStatus = enrollment.getAcademicStatus();
      enrollment.complete(clock.instant());
      audit.record(enrollment, CourseEnrollmentStatus.ENROLLED, previousAcademicStatus);
      for (final CourseEnrollmentSchedule schedule :
          schedulesByEnrollment.getOrDefault(enrollment.getId(), List.of())) {
        if (schedule.getReleasedAt() == null) {
          schedule.release(clock.instant());
        }
      }
      histories.add(
          CourseEnrollmentHistory.create(
              course.getInstitution(),
              enrollment,
              CourseEnrollmentStatus.ENROLLED,
              CourseEnrollmentStatus.COMPLETED,
              previousAcademicStatus,
              enrollment.getAcademicStatus(),
              "COURSE_FINISHED",
              "El curso fue finalizado.",
              authorityPersonId,
              clock.instant()));
    }
    historyRepository.saveAll(histories);
    enrollmentRepository.flush();
  }
}
