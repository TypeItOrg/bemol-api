package ar.edu.utn.frvm.typeit.boero_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_period_offerings")
@Getter
@NoArgsConstructor
public class EnrollmentPeriodOffering {
  @Id
  @GeneratedUUIDv7
  @Column(name = "offering_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_period_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentPeriod period;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "study_plan_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private StudyPlan studyPlan;

  @org.hibernate.annotations.BatchSize(size = 50)
  @OneToMany(mappedBy = "offering", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<EnrollmentPeriodOfferingLevel> levels = new ArrayList<>();

  public static EnrollmentPeriodOffering create(
      final EnrollmentPeriod period, final StudyPlan plan) {
    final var offering = new EnrollmentPeriodOffering();
    offering.period = period;
    offering.studyPlan = plan;
    return offering;
  }

  public void selectLevels(final List<AcademicLevel> selected, final boolean includeUnassigned) {
    if (selected.isEmpty() && !includeUnassigned
        || selected.stream()
            .anyMatch(level -> !level.getStudyPlan().getId().equals(studyPlan.getId()))) {
      throw new ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions
          .EnrollmentValidationException(
          ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages
              .PERIOD_SCOPE_INVALID);
    }
    final var ids = new HashSet<UUID>();
    selected.forEach(level -> ids.add(level.getId()));
    if (includeUnassigned) {
      ids.add(null);
    }
    levels.removeIf(level -> !ids.contains(level.levelId()));
    for (final var level : selected) {
      if (levels.stream().noneMatch(existing -> level.getId().equals(existing.levelId()))) {
        levels.add(EnrollmentPeriodOfferingLevel.create(this, level));
      }
    }
    if (includeUnassigned && levels.stream().noneMatch(level -> level.levelId() == null)) {
      levels.add(EnrollmentPeriodOfferingLevel.create(this, null));
    }
  }

  public boolean includes(final @Nullable UUID levelId) {
    return levels.stream().anyMatch(level -> Objects.equals(level.levelId(), levelId));
  }
}
