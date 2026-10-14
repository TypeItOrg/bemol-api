package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentScheduleResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentTeacherOptionResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
public class CourseEnrollmentResponseFactory {
  private final CourseEnrollmentScheduleRepository schedules;
  private final CourseClassTeacherRepository teachers;

  public CourseEnrollmentResponse from(final CourseEnrollment enrollment) {
    final var assignments =
        schedules.findByCourseEnrollment_Id(enrollment.getId()).stream()
            .map(CourseEnrollmentScheduleResponse::from)
            .toList();
    final var teacherOptions =
        teachers.findByCourseClass_IdIn(List.of(enrollment.getCourseClass().getId())).stream()
            .map(assignment -> CourseEnrollmentTeacherOptionResponse.from(assignment.getPerson()))
            .toList();

    return CourseEnrollmentResponse.from(enrollment, assignments, teacherOptions);
  }

  public PaginatedResponse<CourseEnrollmentResponse> from(
      final Page<CourseEnrollment> enrollments) {
    final var ids =
        enrollments.getContent().stream().map(enrollment -> enrollment.getId()).toList();
    if (ids.isEmpty()) {
      return PaginatedResponse.from(
          enrollments.map(enrollment -> CourseEnrollmentResponse.from(enrollment, List.of())));
    }

    final var classIds =
        enrollments.getContent().stream()
            .map(enrollment -> enrollment.getCourseClass().getId())
            .distinct()
            .toList();
    final var teachersByClass =
        teachers.findByCourseClass_IdIn(classIds).stream()
            .collect(
                Collectors.groupingBy(
                    assignment -> assignment.getCourseClass().getId(),
                    Collectors.mapping(
                        assignment ->
                            CourseEnrollmentTeacherOptionResponse.from(assignment.getPerson()),
                        Collectors.toList())));
    final var schedulesByEnrollment =
        schedules.findByCourseEnrollment_IdIn(ids).stream()
            .collect(
                Collectors.groupingBy(
                    schedule -> schedule.getCourseEnrollment().getId(),
                    Collectors.mapping(
                        CourseEnrollmentScheduleResponse::from, Collectors.toList())));

    return PaginatedResponse.from(
        enrollments.map(
            enrollment ->
                CourseEnrollmentResponse.from(
                    enrollment,
                    schedulesByEnrollment.getOrDefault(enrollment.getId(), List.of()),
                    teachersByClass.getOrDefault(enrollment.getCourseClass().getId(), List.of()))));
  }
}
