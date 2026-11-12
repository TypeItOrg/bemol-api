package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseIndividualSlotRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplaceCourseClassesUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final CourseRepository courseRepository;
  private final CourseClassRepository courseClassRepository;
  private final CourseClassDayRepository courseClassDayRepository;
  private final CourseClassScheduleRepository courseClassScheduleRepository;
  private final CourseClassTeacherRepository courseClassTeacherRepository;
  private final CourseClassAssembler courseClassAssembler;
  private final CourseTreeReader courseTreeReader;
  private final CourseIndividualSlotRepository slotRepository;
  private final CourseEnrollmentRepository enrollmentRepository;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;

  @Transactional
  public CourseResponse execute(
      final UUID institutionId, final UUID courseId, final ReplaceCourseClassesRequest request) {
    accessGuard.require(
        PermissionCode.COURSE_UPDATE, institutionId, ScopedResource.COURSE, courseId);

    enrollmentInstitutionLock.lock(institutionId);
    final var course =
        courseRepository
            .findByIdAndInstitution_IdForUpdate(courseId, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    if (course.getStatus() == CourseStatus.CLOSED) {
      throw new InvalidAcademicStateException();
    }
    if (enrollmentRepository.existsByCourseIncludingHistorical(institutionId, courseId)) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_CLASSES_HAVE_ENROLLMENTS);
    }
    final var before = courseTreeReader.readWithAudit(course);
    try {
      slotRepository.deleteByCourseId(courseId);
      deleteCurrentClasses(courseId);
      courseClassAssembler.assemble(
          course.getInstitution(),
          course,
          course.getAcademicSpace().getFormat(),
          request.classes());
      courseClassRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    final var after = courseTreeReader.readWithAudit(course);
    if (!before.snapshot().values().equals(after.snapshot().values())) {
      final var details =
          AuditChangeDetails.change(course.auditName(), before.snapshot(), after.snapshot());
      final var relatedPeople =
          java.util.stream.Stream.concat(before.teacherIds().stream(), after.teacherIds().stream())
              .distinct()
              .sorted()
              .toList();
      details.put("relatedPersonIds", relatedPeople);
      auditRecorder.recordCurrentActor(
          course.getInstitution(),
          null,
          AcademicAuditAction.COURSE_CLASSES_CHANGED,
          Course.AUDIT_TYPE,
          courseId,
          AuditOrigin.administration(),
          details);
    }
    return CourseResponse.from(course, after.classes());
  }

  private void deleteCurrentClasses(final UUID courseId) {
    courseClassScheduleRepository.deleteByDay_CourseClass_Course_Id(courseId);
    courseClassTeacherRepository.deleteByCourseClass_Course_Id(courseId);
    courseClassDayRepository.deleteByCourseClass_Course_Id(courseId);
    courseClassRepository.deleteByCourse_Id(courseId);
    // Release class-number uniqueness before inserting the replacement tree.
    courseClassRepository.flush();
  }
}
