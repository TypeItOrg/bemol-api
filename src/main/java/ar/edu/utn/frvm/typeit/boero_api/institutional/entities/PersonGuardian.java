package ar.edu.utn.frvm.typeit.boero_api.institutional.entities;

import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
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

/** Legal guardianship of a person (usually a minor) by another person, within an institution. */
@Entity
@Table(
    name = "person_guardians",
    uniqueConstraints =
        @UniqueConstraint(
            name = "person_guardians_unique",
            columnNames = {"institution_id", "tutor_person_id", "dependent_person_id"}))
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
}
