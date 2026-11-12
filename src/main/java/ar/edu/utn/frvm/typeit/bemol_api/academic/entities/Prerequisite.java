package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementStage;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
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
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
public class Prerequisite extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("PREREQUISITE", "Correlatividad");

  @Id
  @GeneratedUUIDv7
  @Column(name = "prerequisite_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  private StudyPlan studyPlan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_study_plan_space_id", nullable = false)
  private StudyPlanSpace targetStudyPlanSpace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "required_study_plan_space_id", nullable = false)
  private StudyPlanSpace requiredStudyPlanSpace;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_stage", nullable = false, length = 20)
  private RequirementStage requirementStage;

  @Enumerated(EnumType.STRING)
  @Column(name = "required_condition", nullable = false, length = 20)
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

  public String auditName() {
    return requiredStudyPlanSpace.getAcademicSpace().getName()
        + " → "
        + targetStudyPlanSpace.getAcademicSpace().getName();
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .expose(
            "requiredSpaceId",
            "Identificador del espacio requerido",
            requiredStudyPlanSpace.getId().toString())
        .expose(
            "requiredSpaceName",
            "Espacio requerido",
            requiredStudyPlanSpace.getAcademicSpace().getName())
        .expose(
            "targetSpaceName",
            "Espacio de destino",
            targetStudyPlanSpace.getAcademicSpace().getName())
        .expose(
            "requirementStage",
            "Etapa de la correlatividad",
            requirementStage.name(),
            Map.of("code", requirementStage.name(), "label", requirementStage.getLabel()))
        .expose(
            "requiredCondition",
            "Condición requerida",
            requiredCondition.name(),
            Map.of("code", requiredCondition.name(), "label", requiredCondition.getLabel()))
        .build();
  }
}
