package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.PrerequisiteNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
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
public class DeletePrerequisiteUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final PrerequisiteRepository prerequisiteRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;

  @Transactional
  public void execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.PREREQUISITE,
        id);

    final var existing =
        prerequisiteRepository
            .findByIdAndStudyPlan_Institution_Id(id, institutionId)
            .orElseThrow(PrerequisiteNotFoundException::new);
    studyPlanDraftGuard.lock(institutionId, existing.getStudyPlan().getId());
    prerequisiteRepository.delete(existing);
    auditRecorder.recordCurrentActor(
        existing.getStudyPlan().getInstitution(),
        null,
        AcademicAuditAction.STUDY_PLAN_PREREQUISITE_DELETED,
        Prerequisite.AUDIT_TYPE,
        id,
        AuditOrigin.administration(),
        Map.of(
            "entityName",
            existing.auditName(),
            "studyPlanName",
            existing.getStudyPlan().getName(),
            "before",
            existing.auditSnapshot().visibleValues(),
            "fieldLabels",
            existing.auditSnapshot().labels()));
  }
}
