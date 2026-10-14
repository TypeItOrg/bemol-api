package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentSchedule;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseIndividualSlot;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseIndividualSlotRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseScheduleAssignmentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentSchedules {
  private final CourseClassDayRepository days;
  private final CourseClassScheduleRepository schedules;
  private final CourseIndividualSlotRepository slots;
  private final CourseEnrollmentScheduleRepository assignments;
  private final Clock clock;

  public record Assignment(
      CourseClassSchedule schedule,
      CourseClassDay day,
      @Nullable CourseIndividualSlot slot,
      CourseDay dayOfWeek,
      LocalTime startTime,
      LocalTime endTime) {}

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public List<Assignment> resolve(
      final Course course,
      final CourseClass courseClass,
      final List<CourseScheduleAssignmentRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_REQUIRED);
    }

    final var classDays = days.findByCourseClass_IdIn(List.of(courseClass.getId()));
    final var byId =
        schedules.findByDay_IdIn(classDays.stream().map(day -> day.getId()).toList()).stream()
            .collect(Collectors.toMap(schedule -> schedule.getId(), Function.identity()));
    final var selectedDays = new HashSet<UUID>();
    final var selectedSlots = new HashSet<UUID>();
    final var resolved = new ArrayList<Assignment>();
    final boolean individual =
        course.getAcademicSpace().getFormat() == AcademicSpaceFormat.INDIVIDUAL;

    for (final var request : requests) {
      final var schedule = byId.get(request.classScheduleId());
      if (schedule == null || !selectedDays.add(schedule.getDay().getId())) {
        throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_INVALID);
      }
      final var slot = individual ? resolveSlot(schedule, request.individualSlotId()) : null;
      if (slot != null && !selectedSlots.add(slot.getId())
          || !individual && request.individualSlotId() != null) {
        throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_INVALID);
      }

      final var day = schedule.getDay();
      resolved.add(
          new Assignment(
              schedule,
              day,
              slot,
              day.getDayOfWeek(),
              slot == null ? schedule.getStartTime() : slot.getStartTime(),
              slot == null ? schedule.getEndTime() : slot.getEndTime()));
    }

    return resolved;
  }

  private CourseIndividualSlot resolveSlot(
      final CourseClassSchedule schedule, final @Nullable UUID id) {
    if (id == null) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_INVALID);
    }

    return slots.findBySchedule_IdOrderByStartTime(schedule.getId()).stream()
        .filter(slot -> slot.getId().equals(id))
        .findFirst()
        .orElseThrow(
            () -> new EnrollmentValidationException(EnrollmentMessages.COURSE_ASSIGNMENT_INVALID));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void persist(
      final Institution institution,
      final CourseEnrollment enrollment,
      final List<Assignment> resolved) {
    for (final var assignment : resolved) {
      assignments.save(
          CourseEnrollmentSchedule.create(
              institution,
              enrollment,
              assignment.schedule(),
              assignment.slot(),
              assignment.dayOfWeek(),
              assignment.startTime(),
              assignment.endTime()));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void release(final CourseEnrollment enrollment) {
    for (final var schedule : assignments.findByCourseEnrollment_Id(enrollment.getId())) {
      if (schedule.getReleasedAt() == null) {
        schedule.release(clock.instant());
      }
    }
  }
}
