package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateAcademicLevelUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;

  @Transactional
  public AcademicLevelResponse execute(
      final UUID institutionId, final UUID studyPlanId, final CreateAcademicLevelRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.STUDY_PLAN,
        studyPlanId);

    final var plan = studyPlanDraftGuard.lock(institutionId, studyPlanId);
    final var name = AcademicLevel.derivedName(request.displayOrder());
    if (academicLevelRepository.existsByStudyPlan_IdAndDisplayOrder(
        studyPlanId, request.displayOrder())) {
      throw AcademicConflictException.forField("displayOrder", AcademicMessages.DUPLICATE_ORDER);
    }
    try {
      final var saved =
          academicLevelRepository.save(
              AcademicLevel.create(plan, name, request.displayOrder(), request.description()));
      academicLevelRepository.flush();
      auditRecorder.recordCurrentActor(
          plan.getInstitution(),
          null,
          AuditAction.STUDY_PLAN_LEVEL_CREATED,
          AuditEntityType.ACADEMIC_LEVEL,
          saved.getId(),
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              saved.getName(),
              "studyPlanName",
              plan.getName(),
              "after",
              AcademicAuditDetails.level(saved),
              "changedFields",
              saved.getDescription() == null
                  ? List.of("name", "displayOrder")
                  : List.of("name", "displayOrder", "description")));
      return AcademicLevelResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }
}
