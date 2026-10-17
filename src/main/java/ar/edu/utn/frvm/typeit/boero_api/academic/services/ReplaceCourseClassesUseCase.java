package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseIndividualSlotRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.Map;
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
    final var beforeClasses = courseTreeReader.read(courseId);
    final var before = AcademicAuditDetails.course(course, beforeClasses);
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
    final var afterClasses = courseTreeReader.read(courseId);
    if (!AcademicAuditDetails.classState(beforeClasses)
        .equals(AcademicAuditDetails.classState(afterClasses))) {
      auditRecorder.recordCurrentActor(
          course.getInstitution(),
          null,
          AuditAction.COURSE_CLASSES_CHANGED,
          AuditEntityType.COURSE,
          courseId,
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              AcademicAuditDetails.courseName(course),
              "before",
              before,
              "after",
              AcademicAuditDetails.course(course, afterClasses),
              "changedFields",
              java.util.List.of("classes"),
              "relatedPersonIds",
              AcademicAuditDetails.relatedTeachers(beforeClasses, afterClasses)));
    }
    return CourseResponse.from(course, afterClasses);
  }

  private void deleteCurrentClasses(final UUID courseId) {
    courseClassRepository.deleteSchedulesByCourseId(courseId);
    courseClassRepository.deleteTeachersByCourseId(courseId);
    courseClassRepository.deleteDaysByCourseId(courseId);
    courseClassRepository.deleteByCourseId(courseId);
  }
}
