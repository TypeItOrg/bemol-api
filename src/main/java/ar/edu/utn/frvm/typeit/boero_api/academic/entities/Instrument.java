package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.InvalidAcademicStateException;
import ar.edu.utn.frvm.typeit.boero_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.SoftDeletable;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@AuditOverride(forClass = SoftDeletable.class, name = "deletedAt", isAudited = true)
@Table(
    name = "instruments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "instruments_institution_id_id_unique",
            columnNames = {"institution_id", "instrument_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class Instrument extends SoftDeletable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "instrument_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @Column(nullable = false, length = 150)
  @Audited
  private String name;

  @Column(length = 1000)
  private @Nullable String description;

  @Column(nullable = false)
  @Audited
  private boolean active;

  public static Instrument create(
      final Institution institution, final String name, final @Nullable String description) {
    return Instrument.builder()
        .institution(institution)
        .name(AcademicNameNormalizer.display(name))
        .description(description)
        .active(true)
        .build();
  }

  public void update(final String name, final @Nullable String description) {
    this.name = AcademicNameNormalizer.display(name);
    this.description = description;
  }

  public void updateStatus(final boolean active) {
    this.active = active;
  }

  public boolean delete(final Instant deletedAt) {
    if (active) {
      throw new InvalidAcademicStateException();
    }
    return markDeleted(deletedAt);
  }
}
