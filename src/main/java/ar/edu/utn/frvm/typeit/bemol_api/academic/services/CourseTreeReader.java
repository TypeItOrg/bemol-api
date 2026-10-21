package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassDayResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassScheduleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseTeacherResponse;
import java.util.List;
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

  @Transactional(readOnly = true)
  public List<CourseClassResponse> read(final UUID courseId) {
    return readClasses(courseClassRepository.findByCourse_IdOrderByClassNumberAsc(courseId));
  }

  @Transactional(readOnly = true)
  public List<CourseClassResponse> readClasses(final List<CourseClass> classes) {
    if (classes.isEmpty()) {
      return List.of();
    }
    final var classIds =
        classes.stream().map(mappedCourseClass -> mappedCourseClass.getId()).toList();
    final var days =
        courseClassDayRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(day -> day.getCourseClass().getId()));
    final var schedules =
        courseClassScheduleRepository
            .findByDay_IdIn(
                days.values().stream()
                    .flatMap(mappedList -> mappedList.stream())
                    .map(mappedCourseClassDay -> mappedCourseClassDay.getId())
                    .toList())
            .stream()
            .collect(Collectors.groupingBy(schedule -> schedule.getDay().getId()));
    final var teachers =
        courseClassTeacherRepository.findByCourseClass_IdIn(classIds).stream()
            .collect(Collectors.groupingBy(teacher -> teacher.getCourseClass().getId()));

    return classes.stream()
        .map(
            courseClass ->
                CourseClassResponse.from(
                    courseClass,
                    teachers.getOrDefault(courseClass.getId(), List.of()).stream()
                        .map(CourseTeacherResponse::from)
                        .toList(),
                    days.getOrDefault(courseClass.getId(), List.of()).stream()
                        .map(
                            day ->
                                CourseClassDayResponse.from(
                                    day,
                                    schedules.getOrDefault(day.getId(), List.of()).stream()
                                        .map(CourseClassScheduleResponse::from)
                                        .toList()))
                        .toList()))
        .toList();
  }
}
