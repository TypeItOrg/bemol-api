package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicLevelNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteAcademicLevelUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;

  @Transactional
  public void execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.ACADEMIC_LEVEL,
        id);

    final var level =
        academicLevelRepository
            .findByIdAndStudyPlan_Institution_Id(id, institutionId)
            .orElseThrow(AcademicLevelNotFoundException::new);
    studyPlanDraftGuard.lock(institutionId, level.getStudyPlan().getId());
    if (studyPlanSpaceRepository.existsByAcademicLevel_Id(id)) {
      throw new AcademicConflictException(AcademicMessages.ACADEMIC_LEVEL_HAS_SPACES);
    }
    academicLevelRepository.delete(level);
    auditRecorder.recordCurrentActor(
        level.getStudyPlan().getInstitution(),
        null,
        AuditAction.STUDY_PLAN_LEVEL_DELETED,
        AuditEntityType.ACADEMIC_LEVEL,
        id,
        AuditOrigin.administration(),
        Map.of(
            "entityName",
            level.getName(),
            "studyPlanName",
            level.getStudyPlan().getName(),
            "before",
            AcademicAuditDetails.level(level)));
  }
}
