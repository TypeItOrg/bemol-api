package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreateStudyPlanRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.validation.AcademicNameNormalizer;
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
public class CreateStudyPlanUseCase {
  private final AuditEventRecorder auditRecorder;
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanRepository studyPlanRepository;
  private final TrainingPathRepository trainingPathRepository;

  @Transactional
  public StudyPlanResponse execute(
      final UUID institutionId, final UUID trainingPathId, final CreateStudyPlanRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CREATE,
        institutionId,
        ScopedResource.TRAINING_PATH,
        trainingPathId);

    final var trainingPath =
        trainingPathRepository
            .findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(trainingPathId, institutionId)
            .orElseThrow(TrainingPathNotFoundException::new);
    final var name = AcademicNameNormalizer.display(request.name());
    if (studyPlanRepository.existsByNormalizedName(trainingPathId, name)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }
    try {
      final var saved =
          studyPlanRepository.save(
              StudyPlan.create(
                  trainingPath.getInstitution(),
                  trainingPath,
                  name,
                  request.effectiveFrom(),
                  request.effectiveTo()));
      studyPlanRepository.flush();
      auditRecorder.recordCurrentActor(
          saved.getInstitution(),
          null,
          AuditAction.STUDY_PLAN_CREATED,
          AuditEntityType.STUDY_PLAN,
          saved.getId(),
          AuditOrigin.administration(),
          Map.of("entityName", saved.getName(), "after", AcademicAuditDetails.plan(saved)));
      return StudyPlanResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }
}
