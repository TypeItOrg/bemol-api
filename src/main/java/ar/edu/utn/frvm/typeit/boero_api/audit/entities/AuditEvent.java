package ar.edu.utn.frvm.typeit.boero_api.audit.entities;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
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
import org.jspecify.annotations.Nullable;

/**
 * Immutable record of something a person did, possibly on behalf of another. People are kept as
 * plain ids, without foreign keys, so the log survives any later change to the people table.
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class AuditEvent {

  @Id
  @GeneratedUUIDv7
  @Column(name = "audit_event_id", updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false, updatable = false)
  private Institution institution;

  @Column(name = "actor_person_id", nullable = false, updatable = false)
  private UUID actorPersonId;

  @Column(name = "subject_person_id", nullable = false, updatable = false)
  private UUID subjectPersonId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 60, updatable = false)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
  private AuditEntityType entityType;

  @Column(name = "entity_id", nullable = false, updatable = false)
  private UUID entityId;

  @Column(name = "acted_on_behalf", nullable = false, updatable = false)
  private boolean actedOnBehalf;

  @Column(name = "request_id", length = 100, updatable = false)
  private @Nullable String requestId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  public static AuditEvent record(
      final Institution institution,
      final UUID actorPersonId,
      final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId,
      @Nullable final String requestId,
      final Instant occurredAt) {
    return AuditEvent.builder()
        .institution(institution)
        .actorPersonId(actorPersonId)
        .subjectPersonId(subjectPersonId)
        .action(action)
        .entityType(entityType)
        .entityId(entityId)
        .actedOnBehalf(!actorPersonId.equals(subjectPersonId))
        .requestId(requestId)
        .occurredAt(occurredAt)
        .build();
  }
}
