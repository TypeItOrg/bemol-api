package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class StudyPlanDraftGuard {

  private final StudyPlanRepository studyPlanRepository;

  StudyPlan lock(final UUID institutionId, final UUID studyPlanId) {
    final var plan =
        studyPlanRepository
            .findByIdAndInstitution_IdForUpdate(studyPlanId, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    if (plan.getStatus() != StudyPlanStatus.DRAFT) {
      throw new InvalidAcademicStateException(
          AcademicMessages.STUDY_PLAN_CURRICULUM_REQUIRES_DRAFT);
    }
    return plan;
  }
}
