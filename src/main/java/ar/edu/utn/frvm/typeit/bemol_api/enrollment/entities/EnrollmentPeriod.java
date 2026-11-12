package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.InvalidEnrollmentPeriodDatesException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "enrollment_periods")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentPeriod extends SoftDeletable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE =
      new AuditResource("ENROLLMENT_PERIOD", "Período de inscripción");

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_period_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  private AcademicYear academicYear;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "start_date", nullable = false)
  private Instant startDate;

  @Column(name = "end_date", nullable = false)
  private Instant endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EnrollmentPeriodStatus status;

  @OneToMany(mappedBy = "period", cascade = CascadeType.ALL, orphanRemoval = true)
  @org.hibernate.annotations.BatchSize(size = 50)
  @Builder.Default
  private List<EnrollmentPeriodOffering> offerings = new ArrayList<>();

  @Column(name = "scope_configured", nullable = false)
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
    // Match PostgreSQL/JDBC microsecond precision.
    this.startDate = startDate.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    this.endDate = endDate.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
  }

  public void changeStatus(final EnrollmentPeriodStatus status) {
    this.status = Objects.requireNonNull(status);
  }

  public boolean markDeleted(final Instant now) {
    return super.markDeleted(now);
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    final var offeringIds =
        offerings.stream()
            .flatMap(
                offering ->
                    offering.getLevels().stream()
                        .map(
                            level ->
                                offering.getStudyPlan().getId()
                                    + "/"
                                    + (level.levelId() == null ? "unassigned" : level.levelId())))
            .sorted()
            .toList();
    return AuditSnapshot.fields()
        .expose("name", "Nombre", name)
        .expose(
            "academicYearId", "Identificador del ciclo lectivo", academicYear.getId().toString())
        .expose(
            "startDate",
            "Desde",
            auditDate(startDate).toString(),
            AuditSnapshot.dateTime(auditDate(startDate)))
        .expose(
            "endDate",
            "Hasta",
            auditDate(endDate).toString(),
            AuditSnapshot.dateTime(auditDate(endDate)))
        .expose(
            "status",
            "Estado",
            status.name(),
            Map.of("code", status.name(), "label", status.getLabel()))
        .expose("offeringIds", "Ofertas habilitadas (plan / nivel)", offeringIds)
        .build();
  }

  private static Instant auditDate(final Instant value) {
    // PostgreSQL/JDBC rounds timestamps to microseconds; compare at the stored precision.
    return value.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
  }

  public AuditSnapshot.Snapshot statusAuditSnapshot() {
    return AuditSnapshot.fields()
        .expose(
            "status",
            "Estado",
            status.name(),
            Map.of("code", status.name(), "label", status.getLabel()))
        .build();
  }
}
