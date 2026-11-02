package ar.edu.utn.frvm.typeit.bemol_api.auth.entities;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "platform_email_change_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformEmailChangeToken {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_account_id", nullable = false, unique = true)
  private PlatformAccount platformAccount;

  @Column(name = "previous_email", nullable = false, length = 150)
  private String previousEmail;

  @Column(name = "new_email", nullable = false, length = 150)
  private String newEmail;

  @Column(name = "password_fingerprint", nullable = false, length = 64)
  private String passwordFingerprint;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private @Nullable Instant usedAt;

  public static PlatformEmailChangeToken issue(
      final PlatformAccount account,
      final String email,
      final String passwordFingerprint,
      final String tokenHash,
      final Instant now,
      final Instant expiresAt) {
    final var token = new PlatformEmailChangeToken();
    token.platformAccount = account;
    token.previousEmail = account.getEmail();
    token.newEmail = email;
    token.passwordFingerprint = passwordFingerprint;
    token.tokenHash = tokenHash;
    token.issuedAt = now;
    token.expiresAt = expiresAt;
    return token;
  }

  public void consume(final Instant now, final String currentPasswordFingerprint) {
    if (usedAt != null
        || !expiresAt.isAfter(now)
        || !platformAccount.isEnabled()
        || !previousEmail.equals(platformAccount.getEmail())
        || !passwordFingerprint.equals(currentPasswordFingerprint)) {
      throw new InvalidEmailVerificationTokenException();
    }
    usedAt = now;
  }
}
