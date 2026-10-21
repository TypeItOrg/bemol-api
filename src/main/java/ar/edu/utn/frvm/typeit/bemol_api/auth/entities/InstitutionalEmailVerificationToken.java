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
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "institutional_email_verification_tokens",
    uniqueConstraints = {
      @UniqueConstraint(name = "email_verification_user_unique", columnNames = "user_id"),
      @UniqueConstraint(name = "email_verification_hash_unique", columnNames = "token_hash")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstitutionalEmailVerificationToken {
  @Id
  @GeneratedUUIDv7
  @Column(name = "email_verification_token_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "recipient_email", nullable = false, length = 150)
  private String recipientEmail;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private @Nullable Instant usedAt;

  public static InstitutionalEmailVerificationToken issue(
      final User user, final String hash, final Instant now, final Duration expiration) {
    final var token = new InstitutionalEmailVerificationToken();
    token.user = user;
    token.replace(hash, now, expiration);
    return token;
  }

  public void replace(final String hash, final Instant now, final Duration expiration) {
    tokenHash = hash;
    recipientEmail = user.getPerson().getEmail();
    issuedAt = now;
    expiresAt = now.plus(expiration);
    usedAt = null;
  }

  public boolean canResendAt(final Instant now, final Duration interval) {
    return !issuedAt.plus(interval).isAfter(now);
  }

  public boolean isUsableAt(final Instant now) {
    return usedAt == null
        && expiresAt.isAfter(now)
        && recipientEmail.equals(user.getPerson().getEmail());
  }

  public void consume(final Instant now) {
    if (!isUsableAt(now)) {
      throw new InvalidEmailVerificationTokenException();
    }
    usedAt = now;
  }
}
