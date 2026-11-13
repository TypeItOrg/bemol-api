package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateCourseRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCourseUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final InstitutionRepository institutionRepository;
  private final AcademicYearRepository academicYearRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final InstrumentRepository instrumentRepository;
  private final CourseRepository courseRepository;
  private final CourseClassAssembler courseClassAssembler;
  private final CourseTreeReader courseTreeReader;

  @Transactional
  public CourseResponse execute(final UUID institutionId, final CreateCourseRequest request) {
    accessGuard.require(
        PermissionCode.COURSE_CREATE,
        institutionId,
        ScopedResource.STUDY_PLAN_SPACE,
        request.studyPlanSpaceId());

    final var institution =
        institutionRepository
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final var studyPlanSpace =
        studyPlanSpaceRepository
            .findDetailsByIdAndInstitutionIdForUpdate(request.studyPlanSpaceId(), institutionId)
            .orElseThrow(
                () -> new AcademicConflictException(AcademicMessages.COURSE_SPACE_NOT_IN_PLAN));
    if (studyPlanSpace.getStudyPlan().getStatus() == StudyPlanStatus.DRAFT) {
      throw new AcademicConflictException(AcademicMessages.COURSE_STUDY_PLAN_NOT_ACTIVE);
    }
    final var space = studyPlanSpace.getAcademicSpace();
    final @Nullable Instrument instrument =
        resolveInstrument(institutionId, request.instrumentId());
    validateInstrumentSelection(space.isInstrumental(), instrument);
    final var year =
        academicYearRepository
            .findByIdAndInstitution_IdForUpdate(request.academicYearId(), institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    if (year.getStatus() != AcademicYearStatus.ACTIVE) {
      throw new AcademicConflictException(AcademicMessages.COURSE_YEAR_NOT_ACTIVE);
    }
    try {
      final var course = Course.create(institution, studyPlanSpace, year, instrument);
      courseRepository.save(course);
      courseClassAssembler.assemble(institution, course, space.getFormat(), request.classes());
      courseRepository.flush();
      final var tree = courseTreeReader.readWithAudit(course);
      final var details =
          AuditChangeDetails.change(course.auditName(), AuditSnapshot.empty(), tree.snapshot());
      details.put("relatedPersonIds", tree.teacherIds());
      auditRecorder.recordCurrentActor(
          institution,
          null,
          AcademicAuditAction.COURSE_CREATED,
          Course.AUDIT_TYPE,
          course.getId(),
          AuditOrigin.administration(),
          details);
      return CourseResponse.from(course, tree.classes());
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  private @Nullable Instrument resolveInstrument(
      final UUID institutionId, final @Nullable UUID instrumentId) {
    if (instrumentId == null) {
      return null;
    }

    return instrumentRepository
        .findByIdAndInstitution_Id(instrumentId, institutionId)
        .orElseThrow(() -> new AcademicConflictException(AcademicMessages.INSTRUMENT_NOT_FOUND));
  }

  private static void validateInstrumentSelection(
      final boolean instrumental, final @Nullable Instrument instrument) {
    if (instrumental && instrument == null) {
      throw new AcademicConflictException(AcademicMessages.INSTRUMENT_REQUIRED);
    }
    if (!instrumental && instrument != null) {
      throw new AcademicConflictException(AcademicMessages.INSTRUMENT_NOT_ALLOWED);
    }
    if (instrument != null && !instrument.isActive()) {
      throw new AcademicConflictException(AcademicMessages.INSTRUMENT_NOT_FOUND);
    }
  }
}
