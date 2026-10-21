package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateAcademicLevelRequest;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcademicLevelUseCasesTest {
  @Mock private AuditEventRecorder auditRecorder;
  @org.mockito.Mock private AcademicAccessGuard accessGuard;

  private static final UUID INSTITUTION_ID =
      UUID.fromString("66666666-6666-6666-6666-666666666666");
  private static final UUID PLAN_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
  private static final UUID LEVEL_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

  @Mock private AcademicLevelRepository academicLevelRepository;
  @Mock private StudyPlanDraftGuard studyPlanDraftGuard;
  @InjectMocks private CreateAcademicLevelUseCase createUseCase;
  @InjectMocks private UpdateAcademicLevelUseCase updateUseCase;

  @Test
  @DisplayName("Should derive the level name from the display order on creation")
  void createsLevelWithDerivedName() {
    final var plan = mock(StudyPlan.class);
    given(plan.getName()).willReturn("Plan");
    given(plan.getId()).willReturn(PLAN_ID);
    given(studyPlanDraftGuard.lock(INSTITUTION_ID, PLAN_ID)).willReturn(plan);
    given(academicLevelRepository.existsByStudyPlan_IdAndDisplayOrder(PLAN_ID, 2))
        .willReturn(false);
    given(academicLevelRepository.save(any(AcademicLevel.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    createUseCase.execute(INSTITUTION_ID, PLAN_ID, new CreateAcademicLevelRequest(2, "Desc"));

    final var saved = ArgumentCaptor.forClass(AcademicLevel.class);
    verify(academicLevelRepository).save(saved.capture());
    assertThat(saved.getValue().getName()).isEqualTo("Nivel 2");
    assertThat(saved.getValue().getDisplayOrder()).isEqualTo(2);
    assertThat(saved.getValue().getDescription()).isEqualTo("Desc");
  }

  @Test
  @DisplayName("Should reject a duplicated display order on creation")
  void rejectsDuplicatedOrderOnCreation() {
    final var plan = mock(StudyPlan.class);
    given(plan.getName()).willReturn("Plan");
    given(studyPlanDraftGuard.lock(INSTITUTION_ID, PLAN_ID)).willReturn(plan);
    given(academicLevelRepository.existsByStudyPlan_IdAndDisplayOrder(PLAN_ID, 1)).willReturn(true);

    assertThatThrownBy(
            () ->
                createUseCase.execute(
                    INSTITUTION_ID, PLAN_ID, new CreateAcademicLevelRequest(1, null)))
        .isInstanceOf(AcademicConflictException.class);
  }

  @Test
  @DisplayName("Should rename the level when its display order changes")
  void renamesLevelOnOrderChange() {
    final var plan = mock(StudyPlan.class);
    given(plan.getName()).willReturn("Plan");
    given(plan.getId()).willReturn(PLAN_ID);
    final var level = AcademicLevel.create(plan, "Nivel 1", 1, null);
    given(academicLevelRepository.findByIdAndStudyPlan_Institution_Id(LEVEL_ID, INSTITUTION_ID))
        .willReturn(Optional.of(level));
    given(studyPlanDraftGuard.lock(INSTITUTION_ID, PLAN_ID)).willReturn(plan);
    given(academicLevelRepository.existsByStudyPlan_IdAndDisplayOrderAndIdNot(PLAN_ID, 3, LEVEL_ID))
        .willReturn(false);

    updateUseCase.execute(INSTITUTION_ID, LEVEL_ID, new UpdateAcademicLevelRequest(3, null));

    assertThat(level.getName()).isEqualTo("Nivel 3");
    assertThat(level.getDisplayOrder()).isEqualTo(3);
  }

  @Test
  @DisplayName("Should reject a duplicated display order on update")
  void rejectsDuplicatedOrderOnUpdate() {
    final var plan = mock(StudyPlan.class);
    given(plan.getName()).willReturn("Plan");
    given(plan.getId()).willReturn(PLAN_ID);
    final var level = AcademicLevel.create(plan, "Nivel 1", 1, null);
    given(academicLevelRepository.findByIdAndStudyPlan_Institution_Id(LEVEL_ID, INSTITUTION_ID))
        .willReturn(Optional.of(level));
    given(studyPlanDraftGuard.lock(INSTITUTION_ID, PLAN_ID)).willReturn(plan);
    given(academicLevelRepository.existsByStudyPlan_IdAndDisplayOrderAndIdNot(PLAN_ID, 2, LEVEL_ID))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                updateUseCase.execute(
                    INSTITUTION_ID, LEVEL_ID, new UpdateAcademicLevelRequest(2, null)))
        .isInstanceOf(AcademicConflictException.class);
  }
}
