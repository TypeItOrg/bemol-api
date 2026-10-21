package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteStudyPlanSpaceUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final PrerequisiteRepository prerequisiteRepository;
  private final StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;
  @Autowired private CourseRepository courseRepository;

  @Transactional
  public void execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.STUDY_PLAN_SPACE,
        id);

    final var existing =
        studyPlanSpaceRepository
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(StudyPlanSpaceNotFoundException::new);
    studyPlanDraftGuard.lock(institutionId, existing.getStudyPlan().getId());

    if (courseRepository != null && courseRepository.existsByStudyPlanSpaceId(id)) {
      throw new AcademicConflictException(AcademicMessages.DELETE_REFERENCED_RESOURCE);
    }

    if (prerequisiteRepository.existsByTargetStudyPlanSpace_IdOrRequiredStudyPlanSpace_Id(id, id)) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_SPACE_HAS_PREREQUISITES);
    }

    studyPlanSpaceInstrumentRepository.deleteByStudyPlanSpace_Id(id);
    studyPlanSpaceInstrumentRepository.flush();
    studyPlanSpaceRepository.delete(existing);
    auditRecorder.recordCurrentActor(
        existing.getInstitution(),
        null,
        AuditAction.STUDY_PLAN_SPACE_DELETED,
        AuditEntityType.STUDY_PLAN_SPACE,
        id,
        AuditOrigin.administration(),
        Map.of(
            "entityName",
            existing.getAcademicSpace().getName(),
            "studyPlanName",
            existing.getStudyPlan().getName()));
  }
}
