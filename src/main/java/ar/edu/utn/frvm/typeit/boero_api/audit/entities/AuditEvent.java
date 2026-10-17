package ar.edu.utn.frvm.typeit.boero_api.audit.entities;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditMessages;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditValidationException;
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
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "institution_id", updatable = false)
  private @Nullable Institution institution;

  @Column(name = "actor_person_id", updatable = false)
  private @Nullable UUID actorPersonId;

  @Column(name = "subject_person_id", updatable = false)
  private @Nullable UUID subjectPersonId;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_kind", nullable = false, length = 20, updatable = false)
  private AuditActorKind actorKind;

  @Column(name = "actor_platform_account_id", updatable = false)
  private @Nullable UUID actorPlatformAccountId;

  @Enumerated(EnumType.STRING)
  @Column(length = 40, updatable = false)
  private @Nullable AuditOrigin origin;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private Map<String, Object> details;

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
    return record(
        institution,
        AuditActorKind.INSTITUTION,
        actorPersonId,
        null,
        subjectPersonId,
        action,
        entityType,
        entityId,
        !actorPersonId.equals(subjectPersonId),
        requestId,
        null,
        Map.of(),
        occurredAt);
  }

  public static AuditEvent record(
      @Nullable final Institution institution,
      final AuditActorKind actorKind,
      @Nullable final UUID actorPersonId,
      @Nullable final UUID actorPlatformAccountId,
      @Nullable final UUID subjectPersonId,
      final AuditAction action,
      final AuditEntityType entityType,
      final UUID entityId,
      final boolean actedOnBehalf,
      @Nullable final String requestId,
      @Nullable final AuditOrigin origin,
      final Map<String, Object> details,
      final Instant occurredAt) {
    final boolean validActor =
        switch (actorKind) {
          case INSTITUTION ->
              institution != null && actorPersonId != null && actorPlatformAccountId == null;
          case PLATFORM -> actorPersonId == null && actorPlatformAccountId != null;
          case ANONYMOUS -> actorPersonId == null && actorPlatformAccountId == null;
        };
    if (!validActor) {
      throw new AuditValidationException(AuditMessages.INVALID_ACTOR);
    }
    if (actedOnBehalf
        && (actorKind != AuditActorKind.INSTITUTION
            || subjectPersonId == null
            || subjectPersonId.equals(actorPersonId))) {
      throw new AuditValidationException(AuditMessages.INVALID_REPRESENTATION);
    }
    return AuditEvent.builder()
        .institution(institution)
        .actorKind(actorKind)
        .actorPersonId(actorPersonId)
        .actorPlatformAccountId(actorPlatformAccountId)
        .subjectPersonId(subjectPersonId)
        .action(action)
        .entityType(entityType)
        .entityId(entityId)
        .actedOnBehalf(actedOnBehalf)
        .requestId(requestId)
        .origin(origin)
        .details(Map.copyOf(details))
        .occurredAt(occurredAt)
        .build();
  }

  public @Nullable UUID getActorId() {
    return actorKind == AuditActorKind.PLATFORM ? actorPlatformAccountId : actorPersonId;
  }
}
