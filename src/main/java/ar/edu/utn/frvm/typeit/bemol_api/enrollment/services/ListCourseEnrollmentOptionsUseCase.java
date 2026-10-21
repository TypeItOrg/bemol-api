package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseIndividualSlot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseIndividualSlotRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentAssignmentOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentClassOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentDayOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentScheduleOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentTeacherOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseIndividualSlotOptionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCourseEnrollmentOptionsUseCase {
  private final AcademicAccessGuard accessGuard;

  private final CourseRepository courseRepository;
  private final CourseClassRepository courseClassRepository;
  private final CourseClassDayRepository courseClassDayRepository;
  private final CourseClassScheduleRepository courseClassScheduleRepository;
  private final CourseClassTeacherRepository courseClassTeacherRepository;
  private final CourseIndividualSlotRepository courseIndividualSlotRepository;
  private final CourseEnrollmentScheduleRepository assignmentRepository;

  @Transactional(readOnly = true)
  public CourseEnrollmentAssignmentOptionsResponse execute(
      final UUID institutionId, final UUID courseId) {
    accessGuard.requireAny(
        Set.of(
            PermissionCode.COURSE_READ,
            PermissionCode.COURSE_ENROLLMENT_READ,
            PermissionCode.COURSE_ENROLLMENT_CREATE,
            PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL),
        institutionId,
        ScopedResource.COURSE,
        courseId);
    final Course course =
        courseRepository
            .findByIdAndInstitution_Id(courseId, institutionId)
            .orElseThrow(CourseNotFoundException::new);

    final var classes = courseClassRepository.findByCourse_IdOrderByClassNumberAsc(courseId);
    final var classIds = classes.stream().map(value -> value.getId()).toList();
    final var teachers =
        courseClassTeacherRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(value -> value.getCourseClass().getId()));
    final var daysByClass =
        courseClassDayRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(value -> value.getCourseClass().getId()));
    final var allDays = daysByClass.values().stream().flatMap(days -> days.stream()).toList();
    final Map<UUID, List<CourseClassSchedule>> schedulesByDay =
        allDays.isEmpty()
            ? Map.of()
            : courseClassScheduleRepository
                .findByDay_IdIn(allDays.stream().map(value -> value.getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(value -> value.getDay().getId()));
    final Map<UUID, List<CourseIndividualSlot>> slotsBySchedule =
        loadIndividualSlots(course, schedulesByDay);

    final var assignments =
        allDays.isEmpty()
            ? List.<CourseEnrollmentSchedule>of()
            : assignmentRepository.findActiveByDays(
                institutionId, allDays.stream().map(day -> day.getId()).toList());
    final var occupiedSlots =
        assignments.stream()
            .filter(value -> value.getIndividualSlot() != null)
            .map(value -> value.getIndividualSlot().getId())
            .collect(Collectors.toSet());
    final var occupiedByDay =
        assignments.stream()
            .collect(
                Collectors.groupingBy(
                    value -> value.getSchedule().getDay().getId(), Collectors.counting()));
    final List<CourseEnrollmentClassOptionResponse> classOptions = new ArrayList<>();
    for (final var courseClass : classes) {
      final var days = daysByClass.getOrDefault(courseClass.getId(), List.of());
      final var dayOptions =
          dayOptions(days, schedulesByDay, slotsBySchedule, occupiedSlots, occupiedByDay);
      final var classTeachers = teachers.getOrDefault(courseClass.getId(), List.of());
      final var teacherIds =
          classTeachers.stream().map(teacher -> teacher.getPerson().getId()).toList();
      final var teacherOptions =
          classTeachers.stream()
              .map(teacher -> CourseEnrollmentTeacherOptionResponse.from(teacher.getPerson()))
              .toList();
      classOptions.add(
          new CourseEnrollmentClassOptionResponse(
              courseClass.getId(),
              courseClass.displayName(),
              teacherIds,
              teacherOptions,
              dayOptions));
    }

    return new CourseEnrollmentAssignmentOptionsResponse(
        course.getId(), course.getAcademicSpace().getFormat().name(), classOptions);
  }

  private List<CourseEnrollmentDayOptionResponse> dayOptions(
      final List<CourseClassDay> days,
      final Map<UUID, List<CourseClassSchedule>> schedulesByDay,
      final Map<UUID, List<CourseIndividualSlot>> slotsBySchedule,
      final Set<UUID> occupiedSlots,
      final Map<UUID, Long> occupiedByDay) {
    return days.stream()
        .map(
            day ->
                CourseEnrollmentDayOptionResponse.from(
                    day,
                    schedulesByDay.getOrDefault(day.getId(), List.of()).stream()
                        .map(
                            schedule ->
                                toScheduleOption(
                                    schedule,
                                    slotsBySchedule.getOrDefault(schedule.getId(), List.of()),
                                    occupiedSlots))
                        .toList(),
                    occupiedByDay.getOrDefault(day.getId(), 0L)))
        .toList();
  }

  private Map<UUID, List<CourseIndividualSlot>> loadIndividualSlots(
      final Course course, final Map<UUID, List<CourseClassSchedule>> schedulesByDay) {
    if (course.getAcademicSpace().getFormat() != AcademicSpaceFormat.INDIVIDUAL) {
      return Map.of();
    }

    final var schedules =
        schedulesByDay.values().stream().flatMap(daySchedules -> daySchedules.stream()).toList();
    if (schedules.isEmpty()) {
      return Map.of();
    }

    return courseIndividualSlotRepository
        .findBySchedule_IdIn(schedules.stream().map(value -> value.getId()).toList())
        .stream()
        .collect(Collectors.groupingBy(value -> value.getSchedule().getId()));
  }

  private CourseEnrollmentScheduleOptionResponse toScheduleOption(
      final CourseClassSchedule schedule,
      final List<CourseIndividualSlot> slots,
      final Set<UUID> occupiedSlots) {
    return CourseEnrollmentScheduleOptionResponse.from(
        schedule,
        slots.stream()
            .map(
                slot ->
                    CourseIndividualSlotOptionResponse.from(
                        slot, !occupiedSlots.contains(slot.getId())))
            .toList());
  }
}
