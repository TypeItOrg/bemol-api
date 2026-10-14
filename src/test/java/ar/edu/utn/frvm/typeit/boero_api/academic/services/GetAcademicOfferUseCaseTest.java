package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicEntityTestFactory;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicOfferDetailResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicOfferLevelResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicOfferSpaceResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicOfferSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAcademicOfferUseCaseTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T02:00:00Z"), ZoneOffset.UTC);
  private static final UUID INSTITUTION_ID = id(1);
  private static final UUID PLAN_ID = id(2);
  @Mock private StudyPlanRepository plans;
  @Mock private EnrollmentPeriodRepository periods;
  @Mock private AcademicLevelRepository levels;
  @Mock private StudyPlanSpaceRepository spaces;
  @Mock private AcademicAccessGuard access;

  @Test
  void groupsRealCurriculumSpacesByLevelAndPreservesUnassignedSpacesAndEmptyLevels() {
    var institution = Institution.builder().id(INSTITUTION_ID).build();
    var plan = AcademicEntityTestFactory.studyPlan(institution, PLAN_ID, id(3));
    var firstLevel = AcademicEntityTestFactory.academicLevel(plan, id(4), 1);
    var emptyLevel = AcademicEntityTestFactory.academicLevel(plan, id(5), 2);
    var assigned = AcademicEntityTestFactory.studyPlanSpace(plan, id(6));
    assigned
        .getAcademicSpace()
        .update(
            "Lenguaje Musical", "Lectura", AcademicSpaceType.SUBJECT, AcademicSpaceFormat.GRUPAL);
    assigned.update(
        assigned.getAcademicSpace(),
        firstLevel,
        RequirementType.REQUIRED,
        2,
        ApprovalMode.PROMOTION);
    var unassigned = AcademicEntityTestFactory.studyPlanSpace(plan, id(7));
    unassigned
        .getAcademicSpace()
        .update(
            "Taller institucional",
            null,
            AcademicSpaceType.WORKSHOP,
            AcademicSpaceFormat.INDIVIDUAL);
    unassigned.update(
        unassigned.getAcademicSpace(), null, RequirementType.OPTIONAL, 3, ApprovalMode.FINAL_EXAM);
    when(plans.findAvailableOfferById(INSTITUTION_ID, PLAN_ID, LocalDate.of(2026, 9, 8)))
        .thenReturn(Optional.of(plan));
    when(spaces.findActiveByStudyPlanIdWithDetails(PLAN_ID))
        .thenReturn(List.of(unassigned, assigned));
    when(levels.findByStudyPlan_IdOrderByDisplayOrderAsc(PLAN_ID))
        .thenReturn(List.of(firstLevel, emptyLevel));
    when(periods.findStudyPlanIdsWithOpenEnrollment(
            INSTITUTION_ID, List.of(PLAN_ID), CLOCK.instant()))
        .thenReturn(Set.of(PLAN_ID));

    var response =
        new GetAcademicOfferUseCase(
                access, new BusinessDateProvider(CLOCK), CLOCK, periods, plans, levels, spaces)
            .execute(INSTITUTION_ID, PLAN_ID);

    assertThat(response)
        .isEqualTo(
            new AcademicOfferDetailResponse(
                new AcademicOfferSummaryResponse(
                    PLAN_ID,
                    "Plan",
                    1,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2030, 12, 31),
                    id(3),
                    "Path",
                    "Programa",
                    true),
                List.of(
                    new AcademicOfferLevelResponse(
                        id(4),
                        "Nivel 1",
                        1,
                        null,
                        List.of(
                            new AcademicOfferSpaceResponse(
                                id(6),
                                assigned.getAcademicSpace().getId(),
                                id(4),
                                "Lenguaje Musical",
                                "Lectura",
                                AcademicSpaceType.SUBJECT,
                                AcademicSpaceFormat.GRUPAL,
                                RequirementType.REQUIRED,
                                2,
                                ApprovalMode.PROMOTION))),
                    new AcademicOfferLevelResponse(id(5), "Nivel 2", 2, null, List.of())),
                List.of(
                    new AcademicOfferSpaceResponse(
                        id(7),
                        unassigned.getAcademicSpace().getId(),
                        null,
                        "Taller institucional",
                        null,
                        AcademicSpaceType.WORKSHOP,
                        AcademicSpaceFormat.INDIVIDUAL,
                        RequirementType.OPTIONAL,
                        3,
                        ApprovalMode.FINAL_EXAM))));
  }

  private static UUID id(int value) {
    return new UUID(0, value);
  }
}
