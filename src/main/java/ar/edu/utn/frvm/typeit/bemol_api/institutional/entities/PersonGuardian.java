package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
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
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tutor_person_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Person tutorPerson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dependent_person_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable Person dependentPerson;

  @Column(name = "declared_document_number", nullable = false, length = 8)
  private String declaredDocumentNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_mode", nullable = false, length = 20)
  @Builder.Default
  private GuardianDependentRequestMode requestMode = GuardianDependentRequestMode.NEW;

  // Requester-provided identity, never copied from an existing person's protected data.
  @Column(name = "declared_first_name", length = 255)
  private @Nullable String declaredFirstName;

  @Column(name = "declared_last_name", length = 255)
  private @Nullable String declaredLastName;

  @Column(name = "declared_birth_date")
  private @Nullable LocalDate declaredBirthDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @Audited
  private GuardianRelationship relationship;

  @Column(name = "own_access_requested", nullable = false)
  @Audited
  private boolean ownAccessRequested;

  @Column(name = "declared_email", length = 150)
  private @Nullable String declaredEmail;

  public void requestOwnAccess(final boolean requested, final @Nullable String email) {
    ownAccessRequested = requested;
    declaredEmail = email;
  }

  @Column(name = "is_primary_contact", nullable = false)
  @Audited
  private boolean primaryContact;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  @Audited
  private GuardianLinkStatus status = GuardianLinkStatus.PENDING;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolved_by_person_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable Person resolvedBy;

  @Column(name = "resolved_at")
  @Audited
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
        .declaredDocumentNumber(dependent.getDocumentNumber())
        .relationship(relationship)
        .primaryContact(primaryContact)
        .build();
  }

  public static PersonGuardian request(
      final Institution institution,
      final Person tutor,
      final Person dependent,
      final GuardianRelationship relationship,
      final boolean primaryContact,
      final String declaredFirstName,
      final String declaredLastName) {
    return request(
        institution,
        tutor,
        dependent,
        relationship,
        primaryContact,
        declaredFirstName,
        declaredLastName,
        null);
  }

  public static PersonGuardian request(
      final Institution institution,
      final Person tutor,
      final Person dependent,
      final GuardianRelationship relationship,
      final boolean primaryContact,
      final @Nullable String declaredFirstName,
      final @Nullable String declaredLastName,
      final @Nullable LocalDate declaredBirthDate) {
    final PersonGuardian link =
        request(institution, tutor, dependent, relationship, primaryContact);
    link.declaredFirstName = declaredFirstName == null ? null : declaredFirstName.trim();
    link.declaredLastName = declaredLastName == null ? null : declaredLastName.trim();
    link.declaredBirthDate = declaredBirthDate;
    return link;
  }

  public static PersonGuardian requestUnmaterialized(
      final Institution institution,
      final Person tutor,
      final String documentNumber,
      final GuardianDependentRequestMode mode,
      final GuardianRelationship relationship,
      final boolean primaryContact,
      final @Nullable String firstName,
      final @Nullable String lastName,
      final @Nullable LocalDate birthDate) {
    return PersonGuardian.builder()
        .institution(institution)
        .tutorPerson(tutor)
        .declaredDocumentNumber(documentNumber)
        .requestMode(mode)
        .relationship(relationship)
        .primaryContact(primaryContact)
        .declaredFirstName(firstName)
        .declaredLastName(lastName)
        .declaredBirthDate(birthDate)
        .build();
  }

  public Person getDependentPerson() {
    return java.util.Objects.requireNonNull(dependentPerson, "The request is not materialized");
  }

  public @Nullable UUID getDependentPersonId() {
    return dependentPerson == null ? null : dependentPerson.getId();
  }

  public String getDocumentNumber() {
    return declaredDocumentNumber != null
        ? declaredDocumentNumber
        : getDependentPerson().getDocumentNumber();
  }

  @PrePersist
  void prepareDocument() {
    if (declaredDocumentNumber == null && dependentPerson != null) {
      declaredDocumentNumber = dependentPerson.getDocumentNumber();
    }
  }

  public void materialize(final Person person) {
    if (status != GuardianLinkStatus.PENDING
        || !person.getInstitution().getId().equals(institution.getId())
        || !person.getDocumentNumber().equals(getDocumentNumber())
        || person.getId().equals(tutorPerson.getId())) {
      throw new IllegalStateException("Invalid representation subject");
    }
    dependentPerson = person;
  }

  public boolean isActive() {
    return status == GuardianLinkStatus.ACTIVE;
  }

  public void approve(final @Nullable Person reviewer) {
    getDependentPerson();
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
