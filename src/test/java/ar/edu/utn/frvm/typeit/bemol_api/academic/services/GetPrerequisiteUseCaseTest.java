package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.PrerequisiteNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetPrerequisiteUseCaseTest {

  private final PrerequisiteRepository prerequisiteRepository = mock(PrerequisiteRepository.class);
  private final GetPrerequisiteUseCase useCase =
      new GetPrerequisiteUseCase(
          org.mockito.Mockito.mock(AcademicAccessGuard.class), prerequisiteRepository);

  @Test
  @DisplayName("Should return a prerequisite that belongs to the institution")
  void shouldReturnPrerequisite() {
    final var institutionId = UUID.randomUUID();
    final var prerequisiteId = UUID.randomUUID();
    final var plan = mock(StudyPlan.class);
    final var target = mock(StudyPlanSpace.class);
    final var required = mock(StudyPlanSpace.class);
    final var prerequisite = mock(Prerequisite.class);
    when(prerequisiteRepository.findByIdAndStudyPlan_Institution_Id(prerequisiteId, institutionId))
        .thenReturn(Optional.of(prerequisite));
    when(prerequisite.getId()).thenReturn(prerequisiteId);
    when(prerequisite.getStudyPlan()).thenReturn(plan);
    when(prerequisite.getTargetStudyPlanSpace()).thenReturn(target);
    when(prerequisite.getRequiredStudyPlanSpace()).thenReturn(required);
    when(plan.getId()).thenReturn(UUID.randomUUID());
    when(target.getId()).thenReturn(UUID.randomUUID());
    when(required.getId()).thenReturn(UUID.randomUUID());
    when(prerequisite.getRequirementStage()).thenReturn(RequirementStage.TO_ENROLL);
    when(prerequisite.getRequiredCondition()).thenReturn(RequiredCondition.PASSED);

    assertThat(useCase.execute(institutionId, prerequisiteId).id()).isEqualTo(prerequisiteId);
  }

  @Test
  @DisplayName("Should reject a prerequisite outside the institution")
  void shouldRejectUnknownPrerequisite() {
    final var institutionId = UUID.randomUUID();
    final var prerequisiteId = UUID.randomUUID();
    when(prerequisiteRepository.findByIdAndStudyPlan_Institution_Id(prerequisiteId, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(institutionId, prerequisiteId))
        .isInstanceOf(PrerequisiteNotFoundException.class);
  }
}
