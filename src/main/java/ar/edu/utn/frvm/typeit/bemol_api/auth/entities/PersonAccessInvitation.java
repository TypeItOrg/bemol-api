package ar.edu.utn.frvm.typeit.bemol_api.auth.entities;

import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
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
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "person_access_invitations")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersonAccessInvitation {
  @Id
  @GeneratedUUIDv7
  @Column(name = "invitation_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "person_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Person person;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "representation_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable PersonGuardian representation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "institutional_issuer_person_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable Person institutionalIssuer;

  @Column(name = "issuing_institution_id")
  @Audited
  private @Nullable UUID issuingInstitutionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "platform_issuer_account_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable PlatformAccount platformIssuer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  @Builder.Default
  @Audited
  private AccessInvitationPurpose purpose = AccessInvitationPurpose.INITIAL_ACCESS;

  @Column(name = "issued_at", nullable = false)
  @Audited
  private Instant issuedAt;

  @Column(name = "cancelled_at")
  @Audited
  private @Nullable Instant cancelledAt;

  @Column(name = "expected_email", length = 150)
  private @Nullable String expectedEmail;

  @Column(name = "mail_outcome", length = 16)
  private @Nullable String mailOutcome;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false, length = 150)
  private String email;

  @Column(name = "expires_at", nullable = false)
  @Audited
  private Instant expiresAt;

  @Column(name = "consumed_at")
  @Audited
  private @Nullable Instant consumedAt;

  public static PersonAccessInvitation issue(
      final PersonGuardian representation,
      final String email,
      final String tokenHash,
      final Instant expiresAt) {
    return builder()
        .person(representation.getDependentPerson())
        .representation(representation)
        .email(email)
        .expectedEmail(representation.getDependentPerson().getEmail())
        .issuedAt(expiresAt.minus(java.time.Duration.ofHours(24)))
        .tokenHash(tokenHash)
        .expiresAt(expiresAt)
        .build();
  }

  public static PersonAccessInvitation issueInstitutionally(
      final Person person,
      final Person issuer,
      final String email,
      final String tokenHash,
      final Instant expiresAt) {
    return issueAdministratively(
        person,
        issuer,
        null,
        AccessInvitationPurpose.INITIAL_ACCESS,
        email,
        tokenHash,
        expiresAt.minus(java.time.Duration.ofHours(24)),
        expiresAt);
  }

  public static PersonAccessInvitation issueAdministratively(
      final Person person,
      final @Nullable Person issuer,
      final @Nullable PlatformAccount platform,
      final AccessInvitationPurpose purpose,
      final String email,
      final String tokenHash,
      final Instant now,
      final Instant expiresAt) {
    if ((issuer == null) == (platform == null)
        || (issuer != null
            && !person.getInstitution().getId().equals(issuer.getInstitution().getId()))) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    return builder()
        .person(person)
        .institutionalIssuer(issuer)
        .platformIssuer(platform)
        .issuingInstitutionId(person.getInstitution().getId())
        .purpose(purpose)
        .email(email)
        .expectedEmail(person.getEmail())
        .tokenHash(tokenHash)
        .issuedAt(now)
        .expiresAt(expiresAt)
        .build();
  }

  public void cancel(final Instant now) {
    if (consumedAt == null && cancelledAt == null) {
      cancelledAt = now;
    }
  }

  public void recordMailOutcome(final String outcome) {
    mailOutcome = outcome;
  }

  public boolean isInstitutional() {
    return institutionalIssuer != null || platformIssuer != null;
  }

  public boolean hasActiveRepresentation() {
    return representation != null && representation.isActive();
  }

  public boolean isUsableAt(Instant now) {
    return consumedAt == null && cancelledAt == null && expiresAt.isAfter(now);
  }

  public void consume(Instant now) {
    if (!isUsableAt(now)) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    consumedAt = now;
  }
}
