package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcademicYearStatusUseCase {

  private final AcademicYearRepository academicYearRepository;
  private final CourseRepository courseRepository;
  private final CourseClosureService courseClosureService;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;
  private final AuditEventRecorder auditRecorder;
  private final CourseClassTeacherRepository courseClassTeacherRepository;

  @Transactional
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE)
  public void execute(
      final UUID institutionId, final UUID id, final AcademicYearStatusRequest request) {
    enrollmentInstitutionLock.lock(institutionId);
    final var academicYear =
        academicYearRepository
            .findByIdAndInstitution_IdForUpdate(id, institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    try {
      change(institutionId, academicYear, request.status());
      academicYearRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  // Both entry points hold the institution and year locks before requesting a transition.
  @Transactional(propagation = Propagation.MANDATORY)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE)
  public void change(
      final UUID institutionId,
      final AcademicYear academicYear,
      final AcademicYearStatus targetStatus) {
    if (targetStatus == AcademicYearStatus.ACTIVE
        && (academicYear.getStartDate() == null || academicYear.getEndDate() == null)) {
      throw new AcademicValidationException(AcademicMessages.ACADEMIC_YEAR_DATES_REQUIRED);
    }
    if (targetStatus == AcademicYearStatus.ACTIVE
        && academicYear.getStatus() != AcademicYearStatus.ACTIVE
        && academicYearRepository.existsByInstitution_IdAndStatusAndDeletedAtIsNull(
            institutionId, AcademicYearStatus.ACTIVE)) {
      throw AcademicConflictException.forField(
          "status", AcademicMessages.ACADEMIC_YEAR_ACTIVE_CONFLICT);
    }

    final var before = academicYear.auditSnapshot();
    academicYear.transitionTo(targetStatus);
    auditRecorder.recordSnapshotChange(
        academicYear.getInstitution(),
        null,
        AcademicAuditAction.ACADEMIC_YEAR_STATUS_CHANGED,
        AcademicYear.AUDIT_TYPE,
        academicYear.getId(),
        String.valueOf(academicYear.getYear()),
        before,
        academicYear.auditSnapshot());
    if (targetStatus == AcademicYearStatus.CLOSED) {
      final var courses =
          courseRepository.findByAcademicYear_IdAndInstitution_IdAndDeletedAtIsNull(
              academicYear.getId(), institutionId);
      final var closingCourses =
          courses.stream().filter(course -> course.getStatus() != CourseStatus.CLOSED).toList();
      final var previousSnapshots =
          closingCourses.stream()
              .collect(Collectors.toMap(course -> course.getId(), entry -> entry.auditSnapshot()));
      final var courseIds = closingCourses.stream().map(course -> course.getId()).toList();
      final var auditCourses =
          courseIds.isEmpty()
              ? List.<Course>of()
              : courseRepository.findAuditContextByIds(courseIds);
      final Map<UUID, List<String>> teachersByCourse =
          courseIds.isEmpty()
              ? Map.of()
              : courseClassTeacherRepository
                  .findByInstitution_IdAndCourseClass_Course_IdIn(institutionId, courseIds)
                  .stream()
                  .collect(
                      Collectors.groupingBy(
                          assignment -> assignment.getCourseClass().getCourse().getId(),
                          Collectors.mapping(
                              assignment -> assignment.getPerson().getId().toString(),
                              Collectors.toList())));
      for (final var course : courses) {
        course.close();
        courseClosureService.close(institutionId, course.getId(), null);
      }
      for (final var course : auditCourses) {
        final var details =
            AuditChangeDetails.change(
                course.auditName(),
                java.util.Objects.requireNonNull(previousSnapshots.get(course.getId())),
                course.auditSnapshot());
        details.put(
            "relatedPersonIds",
            teachersByCourse.getOrDefault(course.getId(), List.of()).stream()
                .distinct()
                .sorted()
                .toList());
        auditRecorder.recordCurrentActor(
            course.getInstitution(),
            null,
            AcademicAuditAction.COURSE_STATUS_CHANGED,
            Course.AUDIT_TYPE,
            course.getId(),
            AuditOrigin.administration(),
            details);
      }
    }
  }
}
