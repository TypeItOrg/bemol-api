package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "envers_poc_revisions")
@RevisionEntity(EnversPilotRevisionListener.class)
@Getter
@NoArgsConstructor
public class EnversPilotRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @RevisionNumber
  private long id;

  @RevisionTimestamp
  @Column(name = "occurred_at_epoch_ms", nullable = false)
  private long occurredAtEpochMs;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_kind", nullable = false, length = 20)
  private AuditActorKind actorKind;

  @Column(name = "actor_person_id")
  private @Nullable UUID actorPersonId;

  @Column(name = "actor_platform_account_id")
  private @Nullable UUID actorPlatformAccountId;

  @Column(name = "request_id", length = 100)
  private @Nullable String requestId;

  @Column(name = "transaction_id", updatable = false)
  private @Nullable UUID transactionId;

  void identifyTransaction(@Nullable final UUID transactionId) {
    this.transactionId = transactionId;
  }

  void identifyActor(
      final AuditActorKind actorKind,
      final @Nullable UUID actorPersonId,
      final @Nullable UUID platformAccountId,
      final @Nullable String requestId) {
    this.actorKind = actorKind;
    this.actorPersonId = actorPersonId;
    this.actorPlatformAccountId = platformAccountId;
    this.requestId = requestId;
  }
}
