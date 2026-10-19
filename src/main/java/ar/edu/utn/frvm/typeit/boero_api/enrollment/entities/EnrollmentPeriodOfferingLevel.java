package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_period_offering_levels")
@Getter
@NoArgsConstructor
public class EnrollmentPeriodOfferingLevel {
  @Id
  @GeneratedUUIDv7
  @Column(name = "offering_level_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "offering_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentPeriodOffering offering;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "academic_level_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable AcademicLevel academicLevel;

  public static EnrollmentPeriodOfferingLevel create(
      final EnrollmentPeriodOffering offering, final @Nullable AcademicLevel level) {
    final var selection = new EnrollmentPeriodOfferingLevel();
    selection.offering = offering;
    selection.academicLevel = level;
    return selection;
  }

  public @Nullable UUID levelId() {
    return academicLevel == null ? null : academicLevel.getId();
  }
}
