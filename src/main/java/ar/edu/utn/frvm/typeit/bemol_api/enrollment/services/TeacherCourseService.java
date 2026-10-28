package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;
import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CourseTreeReader;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherCourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherWeeklySchedulesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherCourseService {
  private final CourseClassTeacherRepository assignments;
  private final PersonRoleAssignmentRepository roles;
  private final CourseTreeReader treeReader;
  private final CourseEnrollmentService enrollments;
  private final BusinessDateProvider businessDateProvider;

  public PaginatedResponse<TeacherCourseClassResponse> list(
      final UUID institutionId, final UUID personId, final Pageable pageable) {
    requireTeacher(institutionId, personId);
    final var page = assignments.findAssignedClasses(institutionId, personId, pageable);
    final var responses = toResponses(page.getContent());

    return PaginatedResponse.<TeacherCourseClassResponse>builder()
        .items(responses)
        .page(page.getNumber())
        .size(page.getSize())
        .totalItems(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }

  public TeacherWeeklySchedulesResponse weeklySchedules(
      final UUID institutionId, final UUID personId, final LocalDate requestedDate) {
    requireTeacher(institutionId, personId);
    final var date = requestedDate == null ? businessDateProvider.today() : requestedDate;
    if (date.getYear() < 1900 || date.getYear() > 2100) {
      throw new EnrollmentValidationException(EnrollmentMessages.SCHEDULE_WEEK_INVALID);
    }

    final var weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    final var weekEnd = weekStart.plusDays(6);
    final var classes =
        assignments.findAssignedClassesInWeek(institutionId, personId, weekStart, weekEnd);

    final var classesById =
        classes.stream()
            .collect(
                Collectors.toMap(mappedCourseClass -> mappedCourseClass.getId(), value -> value));
    final var responses =
        toResponses(classes).stream()
            .map(response -> restrictToWeek(response, classesById, weekStart))
            .toList();

    return new TeacherWeeklySchedulesResponse(weekStart, weekEnd, responses);
  }

  public PaginatedResponse<CourseEnrollmentResponse> listEnrollments(
      final UUID institutionId, final UUID personId, final UUID classId, final Pageable pageable) {
    requireTeacher(institutionId, personId);
    if (!assignments.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
        institutionId, personId, classId)) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASS_INVALID);
    }
    return enrollments.listForTeacherClass(institutionId, personId, classId, pageable);
  }

  public TeacherCourseClassResponse getClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    requireTeacher(institutionId, personId);
    final var assignment =
        assignments
            .findByInstitution_IdAndPerson_IdAndCourseClass_Id(institutionId, personId, classId)
            .orElseThrow(
                () -> new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASS_INVALID));
    return toResponses(List.of(assignment.getCourseClass())).getFirst();
  }

  public CourseEnrollmentResponse getEnrollment(
      final UUID institutionId, final UUID personId, final UUID classId, final UUID enrollmentId) {
    requireAssignedClass(institutionId, personId, classId);
    return enrollments.getForTeacherClass(institutionId, classId, enrollmentId);
  }

  public List<CourseEnrollmentHistoryResponse> enrollmentHistory(
      final UUID institutionId, final UUID personId, final UUID classId, final UUID enrollmentId) {
    requireAssignedClass(institutionId, personId, classId);
    return enrollments.historyForTeacherClass(institutionId, classId, enrollmentId);
  }

  @Transactional
  public CourseEnrollmentResponse updateEnrollmentResult(
      final UUID institutionId,
      final UUID personId,
      final UUID classId,
      final UUID enrollmentId,
      final UpdateAcademicEnrollmentStatusRequest request) {
    requireAssignedClass(institutionId, personId, classId);
    return enrollments.updateForTeacherClass(
        institutionId, classId, enrollmentId, request, personId);
  }

  private void requireAssignedClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    requireTeacher(institutionId, personId);
    if (!assignments.existsByInstitution_IdAndPerson_IdAndCourseClass_Id(
        institutionId, personId, classId)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  private void requireTeacher(final UUID institutionId, final UUID personId) {
    if (!roles.existsByPerson_IdAndInstitution_IdAndRole_Code(
        personId, institutionId, SystemRoleCode.TEACHER.name())) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
  }

  private List<TeacherCourseClassResponse> toResponses(final List<CourseClass> classes) {
    final var details =
        treeReader.readClasses(classes).stream()
            .collect(
                Collectors.toMap(
                    mappedCourseClassResponse -> mappedCourseClassResponse.id(), value -> value));

    return classes.stream()
        .map(
            courseClass -> {
              final var course = courseClass.getCourse();
              final var detail = requireNonNull(details.get(courseClass.getId()));

              return new TeacherCourseClassResponse(
                  course.getId(),
                  course.getAcademicSpace().getName(),
                  course.getInstrument() == null ? null : course.getInstrument().getName(),
                  courseClass.displayName(),
                  detail);
            })
        .toList();
  }

  private TeacherCourseClassResponse restrictToWeek(
      final TeacherCourseClassResponse response,
      final Map<UUID, CourseClass> classesById,
      final LocalDate weekStart) {
    final var courseClass = requireNonNull(classesById.get(response.courseClass().id()));
    final var academicYear = courseClass.getCourse().getAcademicYear();
    final var applicableDays =
        response.courseClass().days().stream()
            .filter(
                day -> {
                  final var date =
                      weekStart.with(
                          TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(day.dayOfWeek().name())));

                  return !date.isBefore(academicYear.getStartDate())
                      && !date.isAfter(academicYear.getEndDate());
                })
            .toList();
    final var detail =
        new CourseClassResponse(
            response.courseClass().id(),
            response.courseClass().classNumber(),
            response.courseClass().teachers(),
            applicableDays);

    return new TeacherCourseClassResponse(
        response.courseId(),
        response.academicSpaceName(),
        response.instrumentName(),
        response.classLabel(),
        detail);
  }
}
