package ar.edu.utn.frvm.typeit.boero_api.auth.entities;

import static ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.AuthMessages.USER_PERSON_INSTITUTION_MISMATCH;

import ar.edu.utn.frvm.typeit.boero_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@NullMarked
@Entity
@Table(
    name = "users",
    uniqueConstraints =
        @UniqueConstraint(
            name = "users_institution_person_unique",
            columnNames = {"institution_id", "person_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends Auditable implements UserDetails {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedUUIDv7
  @Column(name = "user_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "person_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Person person;

  @Column(nullable = false, length = PersonFieldConstraints.PASSWORD_HASH_MAX)
  private String password;

  @Column(nullable = false)
  @Builder.Default
  @Audited
  private boolean enabled = true;

  @Column(name = "self_access_enabled", nullable = false)
  @Builder.Default
  @Audited
  private boolean selfAccessEnabled = true;

  public void updateSelfAccess(final boolean enabled) {
    selfAccessEnabled = enabled;
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "email_verification_status", nullable = false, length = 24)
  @Builder.Default
  @Audited
  private EmailVerificationStatus emailVerificationStatus = EmailVerificationStatus.NOT_REQUIRED;

  @Column(name = "email_verified_at")
  @Audited
  private @Nullable Instant emailVerifiedAt;

  @Column(name = "webauthn_user_handle")
  private byte @Nullable [] webauthnUserHandle;

  @Override
  public String getUsername() {
    return getDocumentNumber();
  }

  public UUID getInstitutionId() {
    return institution.getId();
  }

  public String getName() {
    return person.getFirstName();
  }

  public String getLastName() {
    return person.getLastName();
  }

  public String getDocumentNumber() {
    return person.getDocumentNumber();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public boolean isEnabled() {
    return isAccountActive() && !requiresEmailVerification();
  }

  public boolean isAccountActive() {
    return enabled && selfAccessEnabled && institution.isActive() && !person.isDeleted();
  }

  public boolean requiresEmailVerification() {
    return emailVerificationStatus == EmailVerificationStatus.PENDING;
  }

  public void verifyEmail(final Instant now) {
    if (!isAccountActive() || !requiresEmailVerification()) {
      throw new InvalidEmailVerificationTokenException();
    }
    emailVerificationStatus = EmailVerificationStatus.VERIFIED;
    emailVerifiedAt = now;
  }

  public boolean isAccessEnabled() {
    return enabled;
  }

  public boolean updateAccess(final boolean enabled) {
    if (this.enabled == enabled) {
      return false;
    }

    this.enabled = enabled;
    return true;
  }

  public void changePassword(final String password) {
    this.password = password;
  }

  public byte[] ensureWebAuthnUserHandle(final SecureRandom secureRandom) {
    if (webauthnUserHandle != null) {
      return webauthnUserHandle.clone();
    }
    final byte[] handle = new byte[32];
    secureRandom.nextBytes(handle);
    this.webauthnUserHandle = handle.clone();
    return handle.clone();
  }

  public boolean hasWebAuthnUserHandle() {
    return webauthnUserHandle != null;
  }

  @PrePersist
  @PreUpdate
  private void validateInstitutionConsistency() {
    if (!institution.getId().equals(person.getInstitution().getId())) {
      throw new IllegalStateException(USER_PERSON_INSTITUTION_MISMATCH);
    }
  }
}
