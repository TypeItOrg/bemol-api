package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.LocalDate;
import java.util.UUID;

public final class AcademicEntityTestFactory {

  private AcademicEntityTestFactory() {}

  public static StudyPlan studyPlan(final Institution institution) {
    return StudyPlan.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .trainingPath(TrainingPath.create(institution, "Path", null))
        .name("Plan")
        .effectiveFrom(LocalDate.of(2026, 1, 1))
        .effectiveTo(LocalDate.of(2030, 12, 31))
        .status(StudyPlanStatus.DRAFT)
        .build();
  }

  public static StudyPlan studyPlan(
      final Institution institution, final UUID planId, final UUID pathId) {
    return StudyPlan.builder()
        .id(planId)
        .institution(institution)
        .trainingPath(
            TrainingPath.builder()
                .id(pathId)
                .institution(institution)
                .name("Path")
                .description("Programa")
                .build())
        .name("Plan")
        .effectiveFrom(LocalDate.of(2026, 1, 1))
        .effectiveTo(LocalDate.of(2030, 12, 31))
        .status(StudyPlanStatus.ACTIVE)
        .build();
  }

  public static AcademicLevel academicLevel(final StudyPlan plan, final UUID id, final int order) {
    return AcademicLevel.builder()
        .id(id)
        .studyPlan(plan)
        .name("Nivel " + order)
        .displayOrder(order)
        .description(null)
        .build();
  }

  public static StudyPlanSpace studyPlanSpace(final StudyPlan plan, final UUID id) {
    final var academicSpace =
        AcademicSpace.builder()
            .id(UUID.randomUUID())
            .institution(plan.getInstitution())
            .name(id.toString())
            .type(AcademicSpaceType.SUBJECT)
            .format(AcademicSpaceFormat.INDIVIDUAL)
            .active(true)
            .build();
    return StudyPlanSpace.builder()
        .id(id)
        .institution(plan.getInstitution())
        .studyPlan(plan)
        .academicSpace(academicSpace)
        .requirementType(RequirementType.REQUIRED)
        .displayOrder(1)
        .approvalMode(ApprovalMode.PROMOTION)
        .build();
  }

  public static Prerequisite prerequisite(
      final StudyPlan plan, final StudyPlanSpace target, final StudyPlanSpace required) {
    return Prerequisite.builder()
        .id(UUID.randomUUID())
        .studyPlan(plan)
        .targetStudyPlanSpace(target)
        .requiredStudyPlanSpace(required)
        .requirementStage(RequirementStage.TO_ENROLL)
        .requiredCondition(RequiredCondition.PASSED)
        .build();
  }
}
