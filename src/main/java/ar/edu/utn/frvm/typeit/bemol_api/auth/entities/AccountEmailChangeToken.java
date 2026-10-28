package ar.edu.utn.frvm.typeit.bemol_api.auth.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
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
@Table(name = "account_email_change_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEmailChangeToken {
  @Id @GeneratedUUIDv7 private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_person_id")
  private @Nullable Person actorPerson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_platform_account_id")
  private @Nullable PlatformAccount actorPlatformAccount;

  @Column(name = "previous_email", nullable = false, length = 150)
  private String previousEmail;

  @Column(name = "new_email", nullable = false, length = 150)
  private String newEmail;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private @Nullable Instant usedAt;

  public static AccountEmailChangeToken issue(
      final User user,
      final @Nullable Person actorPerson,
      final @Nullable PlatformAccount actorPlatform,
      final String email,
      final String hash,
      final Instant now,
      final Instant expiresAt) {
    if ((actorPerson == null) == (actorPlatform == null)) {
      throw new IllegalArgumentException("Exactly one account actor is required");
    }
    final var token = new AccountEmailChangeToken();
    token.user = user;
    token.actorPerson = actorPerson;
    token.actorPlatformAccount = actorPlatform;
    token.previousEmail = java.util.Objects.requireNonNull(user.getPerson().getEmail());
    token.newEmail = email;
    token.tokenHash = hash;
    token.issuedAt = now;
    token.expiresAt = expiresAt;
    return token;
  }

  public void consume(final Instant now) {
    if (usedAt != null
        || !expiresAt.isAfter(now)
        || !previousEmail.equals(user.getPerson().getEmail())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    usedAt = now;
  }
}
