package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.PrerequisiteNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
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
        AuditAction.STUDY_PLAN_PREREQUISITE_DELETED,
        AuditEntityType.PREREQUISITE,
        id,
        AuditOrigin.administration(),
        Map.of(
            "entityName",
            AcademicAuditDetails.prerequisiteName(existing),
            "studyPlanName",
            existing.getStudyPlan().getName(),
            "before",
            AcademicAuditDetails.prerequisite(existing)));
  }
}
