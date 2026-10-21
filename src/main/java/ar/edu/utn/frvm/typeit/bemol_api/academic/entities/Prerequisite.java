package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(
    name = "prerequisites",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "prerequisites_study_plan_id_id_unique",
          columnNames = {"study_plan_id", "prerequisite_id"}),
      @UniqueConstraint(
          name = "prerequisites_target_required_stage_unique",
          columnNames = {
            "target_study_plan_space_id",
            "required_study_plan_space_id",
            "requirement_stage"
          })
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class Prerequisite extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "prerequisite_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlan studyPlan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_study_plan_space_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlanSpace targetStudyPlanSpace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "required_study_plan_space_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlanSpace requiredStudyPlanSpace;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_stage", nullable = false, length = 20)
  @Audited
  private RequirementStage requirementStage;

  @Enumerated(EnumType.STRING)
  @Column(name = "required_condition", nullable = false, length = 20)
  @Audited
  private RequiredCondition requiredCondition;

  public static Prerequisite create(
      final StudyPlan studyPlan,
      final StudyPlanSpace targetStudyPlanSpace,
      final StudyPlanSpace requiredStudyPlanSpace,
      final RequirementStage requirementStage,
      final RequiredCondition requiredCondition) {
    return Prerequisite.builder()
        .studyPlan(studyPlan)
        .targetStudyPlanSpace(targetStudyPlanSpace)
        .requiredStudyPlanSpace(requiredStudyPlanSpace)
        .requirementStage(requirementStage)
        .requiredCondition(requiredCondition)
        .build();
  }

  public void update(
      final StudyPlanSpace requiredStudyPlanSpace,
      final RequirementStage requirementStage,
      final RequiredCondition requiredCondition) {
    this.requiredStudyPlanSpace = requiredStudyPlanSpace;
    this.requirementStage = requirementStage;
    this.requiredCondition = requiredCondition;
  }
}
