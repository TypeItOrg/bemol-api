package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassTeacher;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassDayResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassScheduleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseTeacherResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseTreeReader {
  private final CourseClassRepository courseClassRepository;
  private final CourseClassDayRepository courseClassDayRepository;
  private final CourseClassScheduleRepository courseClassScheduleRepository;
  private final CourseClassTeacherRepository courseClassTeacherRepository;

  public record CourseTree(
      List<CourseClassResponse> classes,
      AuditSnapshot.Snapshot snapshot,
      List<String> teacherIds) {}

  @Transactional(readOnly = true)
  public List<CourseClassResponse> read(final UUID courseId) {
    return readClasses(courseClassRepository.findByCourse_IdOrderByClassNumberAsc(courseId));
  }

  @Transactional(readOnly = true)
  public List<CourseClassResponse> readClasses(final List<CourseClass> classes) {
    return load(classes).responses();
  }

  @Transactional(readOnly = true)
  public CourseTree readWithAudit(final Course course) {
    final var data =
        load(courseClassRepository.findByCourse_IdOrderByClassNumberAsc(course.getId()));
    final var teacherIds = data.teacherIds();
    final var snapshot =
        course.auditSnapshot(
            data.snapshots(),
            teacherIds.size(),
            data.days().values().stream().mapToInt(entry -> entry.size()).sum(),
            data.schedules().values().stream().mapToInt(entry -> entry.size()).sum());
    return new CourseTree(data.responses(), snapshot, teacherIds);
  }

  @Transactional(readOnly = true)
  public List<String> teacherIds(final UUID courseId) {
    final var ids =
        courseClassRepository.findByCourse_IdOrderByClassNumberAsc(courseId).stream()
            .map(entry -> entry.getId())
            .toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    return courseClassTeacherRepository.findByCourseClass_IdIn(ids).stream()
        .map(assignment -> assignment.getPerson().getId().toString())
        .distinct()
        .sorted()
        .toList();
  }

  private LoadedClasses load(final List<CourseClass> classes) {
    if (classes.isEmpty()) {
      return new LoadedClasses(List.of(), Map.of(), Map.of(), Map.of());
    }
    final var classIds = classes.stream().map(entry -> entry.getId()).toList();
    final var days =
        courseClassDayRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(day -> day.getCourseClass().getId()));
    final var dayIds =
        days.values().stream()
            .flatMap(entry -> entry.stream())
            .map(entry -> entry.getId())
            .toList();
    final var schedules =
        courseClassScheduleRepository.findByDay_IdIn(dayIds).stream()
            .collect(Collectors.groupingBy(schedule -> schedule.getDay().getId()));
    final var teachers =
        courseClassTeacherRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(teacher -> teacher.getCourseClass().getId()));
    return new LoadedClasses(classes, days, schedules, teachers);
  }

  private record LoadedClasses(
      List<CourseClass> classes,
      Map<UUID, List<CourseClassDay>> days,
      Map<UUID, List<CourseClassSchedule>> schedules,
      Map<UUID, List<CourseClassTeacher>> teachers) {
    List<CourseClassResponse> responses() {
      return classes.stream().map(this::classResponse).toList();
    }

    private CourseClassResponse classResponse(final CourseClass courseClass) {
      final var assignedTeachers =
          teachers.getOrDefault(courseClass.getId(), List.of()).stream()
              .map(CourseTeacherResponse::from)
              .toList();
      final var assignedDays =
          days.getOrDefault(courseClass.getId(), List.of()).stream()
              .map(this::dayResponse)
              .toList();
      return CourseClassResponse.from(courseClass, assignedTeachers, assignedDays);
    }

    private CourseClassDayResponse dayResponse(final CourseClassDay day) {
      final var assignedSchedules =
          schedules.getOrDefault(day.getId(), List.of()).stream()
              .map(CourseClassScheduleResponse::from)
              .toList();
      return CourseClassDayResponse.from(day, assignedSchedules);
    }

    List<AuditSnapshot.Snapshot> snapshots() {
      return classes.stream()
          .sorted(Comparator.comparing(entry -> entry.getClassNumber()))
          .map(this::classSnapshot)
          .toList();
    }

    private AuditSnapshot.Snapshot classSnapshot(final CourseClass courseClass) {
      final var assignedDays =
          days.getOrDefault(courseClass.getId(), List.of()).stream()
              .sorted(Comparator.comparing(entry -> entry.getDayOfWeek()))
              .map(day -> day.auditSnapshot(schedules.getOrDefault(day.getId(), List.of())))
              .toList();
      return courseClass.auditSnapshot(
          teachers.getOrDefault(courseClass.getId(), List.of()), assignedDays);
    }

    List<String> teacherIds() {
      return teachers.values().stream()
          .flatMap(entry -> entry.stream())
          .map(assignment -> assignment.getPerson().getId().toString())
          .distinct()
          .sorted()
          .toList();
    }
  }
}
