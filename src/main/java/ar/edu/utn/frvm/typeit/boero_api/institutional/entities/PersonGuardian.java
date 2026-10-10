package ar.edu.utn.frvm.typeit.boero_api.institutional.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Legal guardianship of a person (usually a minor) by another person, within an institution. */
@Entity
@Table(name = "person_guardians")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PersonGuardian extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "person_guardian_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tutor_person_id", nullable = false)
  private Person tutorPerson;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dependent_person_id", nullable = false)
  private Person dependentPerson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private GuardianRelationship relationship;

  @Column(name = "is_primary_contact", nullable = false)
  private boolean primaryContact;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private GuardianLinkStatus status = GuardianLinkStatus.PENDING;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolved_by_person_id")
  private @Nullable Person resolvedBy;

  @Column(name = "resolved_at")
  private @Nullable Instant resolvedAt;

  /** Every link starts pending: the institution decides who may represent whom. */
  public static PersonGuardian request(
      final Institution institution,
      final Person tutor,
      final Person dependent,
      final GuardianRelationship relationship,
      final boolean primaryContact) {
    return PersonGuardian.builder()
        .institution(institution)
        .tutorPerson(tutor)
        .dependentPerson(dependent)
        .relationship(relationship)
        .primaryContact(primaryContact)
        .build();
  }

  public boolean isActive() {
    return status == GuardianLinkStatus.ACTIVE;
  }

  public void approve(final @Nullable Person reviewer) {
    resolve(GuardianLinkStatus.ACTIVE, reviewer);
  }

  public void reject(final @Nullable Person reviewer) {
    resolve(GuardianLinkStatus.REJECTED, reviewer);
  }

  public void end() {
    if (status != GuardianLinkStatus.PENDING && status != GuardianLinkStatus.ACTIVE) {
      throw new IllegalStateException("Only a pending or active guardian link can be ended");
    }

    status = GuardianLinkStatus.ENDED;
  }

  private void resolve(final GuardianLinkStatus outcome, final @Nullable Person reviewer) {
    if (status != GuardianLinkStatus.PENDING) {
      throw new GuardianLinkAlreadyResolvedException();
    }

    status = outcome;
    resolvedBy = reviewer;
    resolvedAt = Instant.now();
  }
}
