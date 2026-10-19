package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "academic_levels",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "academic_levels_study_plan_id_id_unique",
          columnNames = {"study_plan_id", "academic_level_id"}),
      @UniqueConstraint(
          name = "academic_levels_study_plan_order_unique",
          columnNames = {"study_plan_id", "display_order"})
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class AcademicLevel extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "academic_level_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlan studyPlan;

  @Column(nullable = false, length = 150)
  @Audited
  private String name;

  @Column(name = "display_order", nullable = false)
  @Audited
  private int displayOrder;

  @Column(length = 1000)
  private @Nullable String description;

  public static String derivedName(final int displayOrder) {
    return AcademicNameNormalizer.display("Nivel " + displayOrder);
  }

  public static AcademicLevel create(
      final StudyPlan studyPlan,
      final String name,
      final int displayOrder,
      final @Nullable String description) {
    return AcademicLevel.builder()
        .studyPlan(studyPlan)
        .name(AcademicNameNormalizer.display(name))
        .displayOrder(displayOrder)
        .description(description)
        .build();
  }

  public void update(
      final String name, final int displayOrder, final @Nullable String description) {
    this.name = AcademicNameNormalizer.display(name);
    this.displayOrder = displayOrder;
    this.description = description;
  }
}
