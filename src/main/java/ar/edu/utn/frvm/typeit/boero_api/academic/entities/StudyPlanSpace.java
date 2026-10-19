package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
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
public class StudyPlanSpace extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "study_plan_space_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlan studyPlan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_space_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private AcademicSpace academicSpace;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academic_level_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable AcademicLevel academicLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "requirement_type", nullable = false, length = 20)
  @Audited
  private RequirementType requirementType;

  @Column(name = "display_order", nullable = false)
  @Audited
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_mode", nullable = false, length = 30)
  @Audited
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
}
