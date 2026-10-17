package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.StudyPlanSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.PrerequisiteResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePrerequisiteUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final PrerequisiteRepository prerequisiteRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final StudyPlanDraftGuard studyPlanDraftGuard;
  private final PrerequisiteCycleValidator prerequisiteCycleValidator;

  @Transactional
  public PrerequisiteResponse execute(
      final UUID institutionId, final UUID targetId, final CreatePrerequisiteRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CURRICULUM_UPDATE,
        institutionId,
        ScopedResource.STUDY_PLAN_SPACE,
        targetId);

    final var target = requireSpace(institutionId, targetId);
    final var plan = studyPlanDraftGuard.lock(institutionId, target.getStudyPlan().getId());
    final var required = requireSpace(institutionId, request.requiredStudyPlanSpaceId());
    validateSamePlan(target, required);
    prerequisiteCycleValidator.validate(plan.getId(), targetId, required.getId(), null);
    try {
      final var saved =
          prerequisiteRepository.save(
              Prerequisite.create(
                  plan, target, required, request.requirementStage(), request.requiredCondition()));
      prerequisiteRepository.flush();
      auditRecorder.recordCurrentActor(
          plan.getInstitution(),
          null,
          AuditAction.STUDY_PLAN_PREREQUISITE_CREATED,
          AuditEntityType.PREREQUISITE,
          saved.getId(),
          AuditOrigin.administration(),
          Map.of(
              "entityName",
              AcademicAuditDetails.prerequisiteName(saved),
              "studyPlanName",
              plan.getName(),
              "after",
              AcademicAuditDetails.prerequisite(saved)));
      return PrerequisiteResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  private StudyPlanSpace requireSpace(final UUID institutionId, final UUID id) {
    return studyPlanSpaceRepository
        .findByIdAndInstitution_Id(id, institutionId)
        .orElseThrow(StudyPlanSpaceNotFoundException::new);
  }

  private static void validateSamePlan(final StudyPlanSpace target, final StudyPlanSpace required) {
    if (target.getId().equals(required.getId())
        || !target.getStudyPlan().getId().equals(required.getStudyPlan().getId())) {
      throw new AcademicConflictException(AcademicMessages.INVALID_RELATIONSHIP);
    }
  }
}
