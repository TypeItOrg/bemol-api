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
    name = "platform_sessions",
    indexes =
        @Index(
            name = "platform_sessions_account_active_idx",
            columnList = "platform_account_id, active"))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlatformSession {

  @Id
  @GeneratedUUIDv7
  @Column(name = "platform_session_id")
  private UUID id;

  @Column(name = "platform_account_id", nullable = false)
  private UUID platformAccountId;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_account_id", insertable = false, updatable = false, nullable = false)
  private PlatformAccount platformAccount;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @CreatedDate
  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "remember_me", nullable = false)
  @Builder.Default
  private boolean rememberMe = false;

  public boolean end(final Instant endedAt) {
    if (!active) {
      return false;
    }

    active = false;
    this.endedAt = endedAt;
    return true;
  }
}
