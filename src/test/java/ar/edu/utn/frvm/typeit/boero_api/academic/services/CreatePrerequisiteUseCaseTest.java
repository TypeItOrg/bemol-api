package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicEntityTestFactory.prerequisite;
import static ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicEntityTestFactory.studyPlan;
import static ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicEntityTestFactory.studyPlanSpace;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.PrerequisiteCycleException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CreatePrerequisiteRequest;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePrerequisiteUseCaseTest {
  @Mock private AuditEventRecorder auditRecorder;

  @Mock private PrerequisiteRepository prerequisiteRepository;
  @Mock private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Mock private StudyPlanRepository studyPlanRepository;

  @Test
  @DisplayName("Should reject an indirect prerequisite cycle")
  void rejectsIndirectCycle() {
    final UUID institutionId = UUID.randomUUID();
    final var institution = Institution.builder().id(institutionId).build();
    final var plan = studyPlan(institution);
    final var a = studyPlanSpace(plan, UUID.randomUUID());
    final var b = studyPlanSpace(plan, UUID.randomUUID());
    final var c = studyPlanSpace(plan, UUID.randomUUID());
    final var aRequiresB = prerequisite(plan, a, b);
    final var bRequiresC = prerequisite(plan, b, c);
    final var useCase =
        new CreatePrerequisiteUseCase(
            auditRecorder,
            org.mockito.Mockito.mock(AcademicAccessGuard.class),
            prerequisiteRepository,
            studyPlanSpaceRepository,
            new StudyPlanDraftGuard(studyPlanRepository),
            new PrerequisiteCycleValidator(prerequisiteRepository));

    given(studyPlanSpaceRepository.findByIdAndInstitution_Id(c.getId(), institutionId))
        .willReturn(Optional.of(c));
    given(studyPlanSpaceRepository.findByIdAndInstitution_Id(a.getId(), institutionId))
        .willReturn(Optional.of(a));
    given(studyPlanRepository.findByIdAndInstitution_IdForUpdate(plan.getId(), institutionId))
        .willReturn(Optional.of(plan));
    given(prerequisiteRepository.findByStudyPlan_Id(plan.getId()))
        .willReturn(List.of(aRequiresB, bRequiresC));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    institutionId,
                    c.getId(),
                    new CreatePrerequisiteRequest(
                        a.getId(), RequirementStage.TO_ENROLL, RequiredCondition.PASSED)))
        .isInstanceOf(PrerequisiteCycleException.class);
  }
}
