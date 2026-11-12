package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicLevelNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcademicLevelUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;

  @Transactional
  public AcademicLevelResponse execute(
      final UUID institutionId, final UUID id, final UpdateAcademicLevelRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.ACADEMIC_LEVEL,
        id);

    final var level =
        academicLevelRepository
            .findByIdAndStudyPlan_Institution_Id(id, institutionId)
            .orElseThrow(AcademicLevelNotFoundException::new);
    final var plan = studyPlanDraftGuard.lock(institutionId, level.getStudyPlan().getId());
    final var name = AcademicLevel.derivedName(request.displayOrder());
    if (academicLevelRepository.existsByStudyPlan_IdAndDisplayOrderAndIdNot(
        plan.getId(), request.displayOrder(), id)) {
      throw AcademicConflictException.forField("displayOrder", AcademicMessages.DUPLICATE_ORDER);
    }
    final var before = level.auditSnapshot();
    level.update(name, request.displayOrder(), request.description());
    try {
      academicLevelRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
    final var after = level.auditSnapshot();
    if (!before.values().equals(after.values())) {
      final var details = AuditChangeDetails.change(level.getName(), before, after);
      details.put("studyPlanName", plan.getName());
      auditRecorder.recordCurrentActor(
          plan.getInstitution(),
          null,
          AcademicAuditAction.STUDY_PLAN_LEVEL_UPDATED,
          AcademicLevel.AUDIT_TYPE,
          id,
          AuditOrigin.administration(),
          details);
    }
    return AcademicLevelResponse.from(level);
  }
}
