package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassTeacher;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassDayRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassScheduleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseIndividualSlotFactory;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseClassAssembler {

  private final CourseClassRepository courseClassRepository;
  private final CourseClassDayRepository courseClassDayRepository;
  private final CourseClassScheduleRepository courseClassScheduleRepository;
  private final CourseClassTeacherRepository courseClassTeacherRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final PersonRepository personRepository;
  private final CourseIndividualSlotFactory slotFactory;

  public List<CourseClass> assemble(
      final Institution institution,
      final Course course,
      final AcademicSpaceFormat spaceFormat,
      final List<CourseClassRequest> requests) {
    validateTeacherIds(institution.getId(), requests);
    validateUniqueDays(requests);
    validateSchedules(requests);
    final List<CourseClass> classes = new ArrayList<>(requests.size());
    for (int index = 0; index < requests.size(); index++) {
      classes.add(assembleClass(institution, course, spaceFormat, requests.get(index), index + 1));
    }
    return classes;
  }

  private CourseClass assembleClass(
      final Institution institution,
      final Course course,
      final AcademicSpaceFormat spaceFormat,
      final CourseClassRequest request,
      final int classNumber) {
    final var courseClass =
        courseClassRepository.save(CourseClass.create(institution, course, classNumber));
    persistTeachers(institution, courseClass, request.teacherIds());
    for (final var dayRequest : request.days()) {
      assembleDay(institution, courseClass, spaceFormat, dayRequest);
    }
    return courseClass;
  }

  private void assembleDay(
      final Institution institution,
      final CourseClass courseClass,
      final AcademicSpaceFormat spaceFormat,
      final CourseClassDayRequest request) {
    final boolean individual = spaceFormat == AcademicSpaceFormat.INDIVIDUAL;
    final Integer periodDurationMinutes =
        individual ? requirePeriod(request.periodDurationMinutes()) : null;
    final int totalMinutes = totalMinutes(request.schedules());
    if (totalMinutes <= 0) {
      throw invalid(AcademicMessages.COURSE_SCHEDULE_INVALID);
    }
    if (request.capacity() != null && request.capacity() <= 0) {
      throw invalid(AcademicMessages.COURSE_SCHEDULE_INVALID);
    }
    final Integer capacity =
        periodDurationMinutes != null
            ? Integer.valueOf(totalMinutes / periodDurationMinutes)
            : request.capacity();
    final var day =
        courseClassDayRepository.save(
            CourseClassDay.create(
                institution, courseClass, request.dayOfWeek(), capacity, periodDurationMinutes));
    for (final var scheduleRequest : request.schedules()) {
      final var schedule =
          courseClassScheduleRepository.save(
              CourseClassSchedule.create(
                  institution, day, scheduleRequest.startTime(), scheduleRequest.endTime()));
      slotFactory.createFor(schedule);
    }
  }

  private void persistTeachers(
      final Institution institution, final CourseClass courseClass, final List<UUID> teacherIds) {
    for (final UUID personId : new LinkedHashSet<>(teacherIds)) {
      final var person =
          personRepository
              .findByIdAndInstitution_Id(personId, institution.getId())
              .orElseThrow(PersonNotFoundException::new);
      courseClassTeacherRepository.save(
          CourseClassTeacher.create(institution, courseClass, person));
    }
  }

  private static int totalMinutes(final List<CourseClassScheduleRequest> schedules) {
    return schedules.stream()
        .mapToInt(schedule -> durationMinutes(schedule.startTime(), schedule.endTime()))
        .sum();
  }

  private static int durationMinutes(final LocalTime startTime, final LocalTime endTime) {
    return (int) Duration.between(startTime, endTime).toMinutes();
  }

  private static void validateSchedules(final List<CourseClassRequest> requests) {
    for (final var request : requests) {
      for (final var dayRequest : request.days()) {
        final List<CourseSlot> slots = new ArrayList<>(dayRequest.schedules().size());
        for (final var schedule : dayRequest.schedules()) {
          if (!schedule.startTime().isBefore(schedule.endTime())) {
            throw invalid(AcademicMessages.COURSE_SCHEDULE_INVALID);
          }
          final var slot = new CourseSlot(schedule.startTime(), schedule.endTime());
          for (final var existing : slots) {
            if (slot.overlaps(existing)) {
              throw invalid(AcademicMessages.COURSE_SCHEDULE_OVERLAP);
            }
          }
          slots.add(slot);
        }
      }
    }
  }

  private record CourseSlot(LocalTime startTime, LocalTime endTime) {
    private boolean overlaps(final CourseSlot other) {
      return startTime.isBefore(other.endTime()) && other.startTime().isBefore(endTime);
    }
  }

  private static void validateUniqueDays(final List<CourseClassRequest> requests) {
    for (final var request : requests) {
      final Set<CourseDay> seenDays = new HashSet<>();
      for (final var dayRequest : request.days()) {
        if (!seenDays.add(dayRequest.dayOfWeek())) {
          throw invalid(AcademicMessages.COURSE_DAY_DUPLICATED);
        }
      }
    }
  }

  private static Integer requirePeriod(final @Nullable Integer periodDurationMinutes) {
    if (periodDurationMinutes == null || periodDurationMinutes <= 0) {
      throw invalid(AcademicMessages.COURSE_PERIOD_DURATION_REQUIRED);
    }
    return periodDurationMinutes;
  }

  private static AcademicValidationException invalid(final String message) {
    return new AcademicValidationException(message, Map.of("classes", message));
  }

  private void validateTeacherIds(
      final UUID institutionId, final List<CourseClassRequest> requests) {
    final Set<UUID> allTeacherIds = new HashSet<>();
    for (final var request : requests) {
      allTeacherIds.addAll(request.teacherIds());
    }
    if (allTeacherIds.isEmpty()) {
      throw invalid(AcademicMessages.COURSE_TEACHERS_INVALID);
    }
    final long validTeachers =
        personRoleAssignmentRepository.countDistinctPersonsByInstitutionAndRoleCode(
            institutionId, List.copyOf(allTeacherIds), SystemRoleCode.TEACHER.name());
    if (validTeachers != allTeacherIds.size()) {
      throw invalid(AcademicMessages.COURSE_TEACHERS_INVALID);
    }
  }
}
