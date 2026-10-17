package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateCourseStatusUseCase {
  private final AuditEventRecorder auditRecorder;
  private final CourseTreeReader courseTreeReader;
  private final AcademicAccessGuard accessGuard;
  private final CourseRepository courseRepository;
  private final AcademicYearRepository academicYearRepository;
  private final StudyPlanRepository studyPlanRepository;

  private final CourseClosureService courseClosureService;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;

  @Transactional
  public void execute(final UUID institutionId, final UUID id, final CourseStatusRequest request) {
    accessGuard.require(
        PermissionCode.COURSE_STATUS_UPDATE, institutionId, ScopedResource.COURSE, id);

    enrollmentInstitutionLock.lock(institutionId);
    final var context =
        courseRepository
            .findAcademicContextByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    final var studyPlan =
        studyPlanRepository
            .findByIdAndInstitution_IdForUpdate(context.getStudyPlanId(), institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    final var academicYear =
        academicYearRepository
            .findByIdAndInstitution_IdForUpdate(context.getAcademicYearId(), institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    final var course =
        courseRepository
            .findByIdAndInstitution_IdForUpdate(id, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    if (request.status() == CourseStatus.ACTIVE) {
      if (studyPlan.getStatus() == StudyPlanStatus.DRAFT) {
        throw new AcademicConflictException(AcademicMessages.COURSE_STUDY_PLAN_NOT_ACTIVE);
      }
      if (academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
        throw new AcademicConflictException(AcademicMessages.COURSE_YEAR_NOT_ACTIVE);
      }
      if (course.getAcademicSpace() != null
          && course.getAcademicSpace().isInstrumental()
          && (course.getInstrument() == null || !course.getInstrument().isActive())) {
        throw new AcademicConflictException(AcademicMessages.INSTRUMENT_REQUIRED);
      }
      if (course.getAcademicSpace() != null
          && !course.getAcademicSpace().isInstrumental()
          && course.getInstrument() != null) {
        throw new AcademicConflictException(AcademicMessages.INSTRUMENT_NOT_ALLOWED);
      }
    }
    final var previousStatus = course.getStatus();
    course.updateStatus(request.status());
    courseRepository.flush();
    if (request.status() == CourseStatus.CLOSED) {
      courseClosureService.close(institutionId, id, null);
    }
    if (previousStatus != course.getStatus()) {
      auditRecorder.recordCurrentActor(
          course.getInstitution(),
          null,
          AuditAction.COURSE_STATUS_CHANGED,
          AuditEntityType.COURSE,
          id,
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              AcademicAuditDetails.courseName(course),
              "before",
              Map.of("status", previousStatus.name()),
              "after",
              Map.of("status", course.getStatus().name()),
              "relatedPersonIds",
              AcademicAuditDetails.teacherIds(courseTreeReader.read(id))));
    }
  }
}
