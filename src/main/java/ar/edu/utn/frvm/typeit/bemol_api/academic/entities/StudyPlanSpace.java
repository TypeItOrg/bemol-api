package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "study_plan_spaces",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "study_plan_spaces_institution_id_id_unique",
          columnNames = {"institution_id", "study_plan_space_id"}),
      @UniqueConstraint(
          name = "study_plan_spaces_plan_id_id_unique",
          columnNames = {"study_plan_id", "study_plan_space_id"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class StudyPlanSpace extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("STUDY_PLAN_SPACE", "Espacio de plan");

  @Id
  @GeneratedUUIDv7
  @Column(name = "study_plan_space_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  private StudyPlan studyPlan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_space_id", nullable = false)
  private AcademicSpace academicSpace;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academic_level_id")
  private @Nullable AcademicLevel academicLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_type", nullable = false, length = 20)
  private RequirementType requirementType;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_mode", nullable = false, length = 30)
  private ApprovalMode approvalMode;

  public static StudyPlanSpace create(
      final Institution institution,
      final StudyPlan studyPlan,
      final AcademicSpace academicSpace,
      final @Nullable AcademicLevel academicLevel,
      final RequirementType requirementType,
      final int displayOrder,
      final ApprovalMode approvalMode) {
    return StudyPlanSpace.builder()
        .institution(institution)
        .studyPlan(studyPlan)
        .academicSpace(academicSpace)
        .academicLevel(academicLevel)
        .requirementType(requirementType)
        .displayOrder(displayOrder)
        .approvalMode(approvalMode)
        .build();
  }

  public void update(
      final AcademicSpace academicSpace,
      final @Nullable AcademicLevel academicLevel,
      final RequirementType requirementType,
      final int displayOrder,
      final ApprovalMode approvalMode) {
    this.academicSpace = academicSpace;
    this.academicLevel = academicLevel;
    this.requirementType = requirementType;
    this.displayOrder = displayOrder;
    this.approvalMode = approvalMode;
  }

  private AuditSnapshot.Builder auditFields() {
    return AuditSnapshot.fields()
        .expose(
            "academicSpaceId",
            "Identificador del espacio académico",
            academicSpace.getId().toString())
        .expose("name", "Nombre", academicSpace.getName())
        .expose(
            "academicLevelId",
            "Identificador del nivel académico",
            academicLevel == null ? null : academicLevel.getId().toString())
        .expose(
            "academicLevelName",
            "Nivel académico",
            academicLevel == null ? null : academicLevel.getName())
        .expose(
            "requirementType",
            "Tipo de requisito",
            requirementType.name(),
            Map.of("code", requirementType.name(), "label", requirementType.getLabel()))
        .expose("displayOrder", "Orden", displayOrder)
        .expose(
            "approvalMode",
            "Modo de aprobación",
            approvalMode.name(),
            Map.of("code", approvalMode.name(), "label", approvalMode.getLabel()));
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return auditFields().build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(final List<UUID> instrumentIds) {
    return auditFields()
        .expose(
            "instrumentIds",
            "Identificadores de instrumentos",
            instrumentIds.stream().map(entry -> entry.toString()).sorted().toList())
        .expose("instrumentCount", "Instrumentos", instrumentIds.size())
        .build();
  }
}
