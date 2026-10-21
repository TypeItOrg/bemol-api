package ar.edu.utn.frvm.typeit.bemol_api.auth.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "platform_refresh_tokens",
    indexes = {
      @Index(name = "platform_refresh_tokens_family_idx", columnList = "family_id"),
      @Index(name = "platform_refresh_tokens_session_idx", columnList = "platform_session_id")
    })
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlatformRefreshToken {

  @Id
  @GeneratedUUIDv7
  @Column(name = "platform_refresh_token_id")
  private UUID id;

  @Column(name = "platform_account_id", nullable = false)
  private UUID platformAccountId;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_account_id", insertable = false, updatable = false, nullable = false)
  private PlatformAccount platformAccount;

  @Column(name = "platform_session_id", nullable = false)
  private UUID platformSessionId;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_session_id", insertable = false, updatable = false, nullable = false)
  private PlatformSession platformSession;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Column(nullable = false)
  @Builder.Default
  private boolean revoked = false;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean revoke() {
    if (revoked) {
      return false;
    }

    revoked = true;
    return true;
  }

  public boolean isExpiredAt(final Instant dateTime) {
    return expiresAt.isBefore(dateTime);
  }
}
