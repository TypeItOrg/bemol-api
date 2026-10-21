package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseIndividualSlotRepository;
import java.util.HashSet;
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
@Transactional(readOnly = true)
public class CourseCapacityService {
  private final CourseClassRepository courseClassRepository;
  private final CourseClassDayRepository courseClassDayRepository;
  private final CourseClassScheduleRepository courseClassScheduleRepository;
  private final CourseEnrollmentScheduleRepository enrollmentScheduleRepository;
  private final CourseIndividualSlotRepository slotRepository;

  public boolean hasCapacity(final Course course) {
    return findCoursesWithCapacity(course.getInstitution().getId(), List.of(course))
        .contains(course.getId());
  }

  public Set<UUID> findCoursesWithCapacity(final UUID institutionId, final List<Course> courses) {
    final var activeCourses =
        courses.stream().filter(mappedCourse -> mappedCourse.isActive()).toList();
    if (activeCourses.isEmpty()) {
      return Set.of();
    }

    final var groupCourseIds =
        activeCourses.stream()
            .filter(course -> course.getAcademicSpace().getFormat() == AcademicSpaceFormat.GRUPAL)
            .map(mappedCourse -> mappedCourse.getId())
            .collect(Collectors.toSet());
    final var classes =
        courseClassRepository.findByInstitution_IdAndCourse_IdIn(
            institutionId,
            activeCourses.stream().map(mappedCourse -> mappedCourse.getId()).toList());
    final var classIds = classes.stream().map(value -> value.getId()).toList();
    final var days = courseClassDayRepository.findByCourseClass_IdIn(classIds);
    final var dayIds =
        days.stream().map(mappedCourseClassDay -> mappedCourseClassDay.getId()).toList();
    if (dayIds.isEmpty()) {
      return Set.of();
    }
    final var schedules = courseClassScheduleRepository.findByDay_IdIn(dayIds);
    final var activeAssignments =
        enrollmentScheduleRepository.findActiveByDays(institutionId, dayIds);
    final Map<UUID, Long> occupiedByDay =
        activeAssignments.stream()
            .collect(
                Collectors.groupingBy(
                    value -> value.getSchedule().getDay().getId(), Collectors.counting()));
    final var occupiedSlotIds =
        activeAssignments.stream()
            .filter(value -> value.getIndividualSlot() != null)
            .map(value -> value.getIndividualSlot().getId())
            .collect(Collectors.toSet());
    final var individualScheduleIds =
        schedules.stream()
            .filter(
                schedule ->
                    !groupCourseIds.contains(
                        schedule.getDay().getCourseClass().getCourse().getId()))
            .map(schedule -> schedule.getId())
            .toList();
    final var availableScheduleIds =
        individualScheduleIds.isEmpty()
            ? Set.<UUID>of()
            : slotRepository.findBySchedule_IdIn(individualScheduleIds).stream()
                .filter(slot -> !occupiedSlotIds.contains(slot.getId()))
                .map(slot -> slot.getSchedule().getId())
                .collect(Collectors.toSet());
    final var schedulesByDay =
        schedules.stream().collect(Collectors.groupingBy(value -> value.getDay().getId()));

    final Set<UUID> coursesWithCapacity = new HashSet<>();
    for (final var day : days) {
      final var daySchedules = schedulesByDay.getOrDefault(day.getId(), List.of());
      if (daySchedules.isEmpty()
          || (day.getCapacity() != null
              && occupiedByDay.getOrDefault(day.getId(), 0L) >= day.getCapacity())) {
        continue;
      }
      final var courseId = day.getCourseClass().getCourse().getId();
      if (groupCourseIds.contains(courseId)
          || daySchedules.stream()
              .anyMatch(schedule -> availableScheduleIds.contains(schedule.getId()))) {
        coursesWithCapacity.add(courseId);
      }
    }

    return coursesWithCapacity;
  }
}
