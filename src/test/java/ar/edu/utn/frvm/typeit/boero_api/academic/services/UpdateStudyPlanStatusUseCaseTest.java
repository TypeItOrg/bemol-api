package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateStudyPlanStatusUseCaseTest {
  @Mock private AcademicAccessGuard accessGuard;

  private static final UUID INSTITUTION_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

  @Mock private StudyPlanRepository studyPlanRepository;
  @Mock private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Mock private CourseRepository courseRepository;
  private UpdateStudyPlanStatusUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new UpdateStudyPlanStatusUseCase(
            accessGuard,
            new BusinessDateProvider(
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)),
            studyPlanRepository,
            studyPlanSpaceRepository,
            courseRepository);
  }

  private StudyPlan activePlan() {
    final var institution = Institution.builder().id(INSTITUTION_ID).build();
    final var plan =
        StudyPlan.create(
            institution,
            TrainingPath.create(institution, "Trayecto", null),
            "Plan",
            TODAY.minusMonths(1),
            null);
    plan.activate();
    return plan;
  }

  @Test
  @DisplayName("Should reject deactivating a plan with non-closed courses")
  void rejectsDeactivationWithNonClosedCourses() {
    final var plan = activePlan();
    given(studyPlanRepository.findByIdAndInstitution_IdForUpdate(PLAN_ID, INSTITUTION_ID))
        .willReturn(Optional.of(plan));
    given(
            courseRepository
                .existsByInstitution_IdAndStudyPlan_IdAndStatusNotClosedAndDeletedAtIsNull(
                    INSTITUTION_ID, PLAN_ID))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    INSTITUTION_ID,
                    PLAN_ID,
                    new StudyPlanStatusRequest(StudyPlanStatus.INACTIVE, TODAY.minusDays(1))))
        .isInstanceOf(AcademicConflictException.class)
        .hasMessage(AcademicMessages.STUDY_PLAN_HAS_ACTIVE_COURSES);

    assertThat(plan.getStatus()).isEqualTo(StudyPlanStatus.ACTIVE);
    assertThat(plan.getEffectiveTo()).isNull();
    verify(studyPlanRepository, never()).flush();
  }

  @Test
  @DisplayName("Should deactivate a plan once its courses are closed or deleted")
  void deactivatesWithoutNonClosedCourses() {
    final var plan = activePlan();
    given(studyPlanRepository.findByIdAndInstitution_IdForUpdate(PLAN_ID, INSTITUTION_ID))
        .willReturn(Optional.of(plan));
    given(
            courseRepository
                .existsByInstitution_IdAndStudyPlan_IdAndStatusNotClosedAndDeletedAtIsNull(
                    INSTITUTION_ID, PLAN_ID))
        .willReturn(false);

    useCase.execute(
        INSTITUTION_ID,
        PLAN_ID,
        new StudyPlanStatusRequest(StudyPlanStatus.INACTIVE, TODAY.minusDays(1)));

    verify(studyPlanRepository).flush();
    assertThat(plan.getStatus()).isEqualTo(StudyPlanStatus.INACTIVE);
    assertThat(plan.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 9, 30));
  }
}
