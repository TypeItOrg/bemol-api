package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidEnrollmentPeriodDatesException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@AuditOverride(forClass = SoftDeletable.class, name = "deletedAt", isAudited = true)
@Table(name = "enrollment_periods")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentPeriod extends SoftDeletable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_period_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private AcademicYear academicYear;

  @Column(name = "name", nullable = false, length = 150)
  @Audited
  private String name;

  @Column(name = "start_date", nullable = false)
  @Audited
  private Instant startDate;

  @Column(name = "end_date", nullable = false)
  @Audited
  private Instant endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Audited
  private EnrollmentPeriodStatus status;

  @OneToMany(mappedBy = "period", cascade = CascadeType.ALL, orphanRemoval = true)
  @org.hibernate.annotations.BatchSize(size = 50)
  @Builder.Default
  private List<EnrollmentPeriodOffering> offerings = new ArrayList<>();

  @Column(name = "scope_configured", nullable = false)
  @Audited
  private boolean scopeConfigured;

  public void markScopeConfigured() {
    scopeConfigured = true;
  }

  public boolean isOpenAt(final Instant now) {
    return getDeletedAt() == null
        && status == EnrollmentPeriodStatus.OPEN
        && !now.isBefore(startDate)
        && !now.isAfter(endDate)
        && scopeConfigured;
  }

  public boolean includes(final StudyPlanSpace space) {
    return scopeConfigured
        && offerings.stream()
            .anyMatch(
                offering ->
                    offering.getStudyPlan().getId().equals(space.getStudyPlan().getId())
                        && offering.includes(
                            space.getAcademicLevel() == null
                                ? null
                                : space.getAcademicLevel().getId()));
  }

  public void updateDetails(final String name, final Instant startDate, final Instant endDate) {
    if (startDate.isAfter(endDate)) {
      throw new InvalidEnrollmentPeriodDatesException();
    }

    this.name = name.trim();
    // Match PostgreSQL/JDBC precision so equal dates do not create false Envers revisions.
    this.startDate = startDate.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    this.endDate = endDate.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
  }

  public void changeStatus(final EnrollmentPeriodStatus status) {
    this.status = Objects.requireNonNull(status);
  }

  public boolean markDeleted(final Instant now) {
    return super.markDeleted(now);
  }
}
