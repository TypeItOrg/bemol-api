package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces.AuditEventRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends to the audit log inside the caller's transaction, so an event exists if and only if the
 * audited change was committed.
 */
@Component
@RequiredArgsConstructor
public class AuditEventRecorder {

  private final AuditEventRepository auditEventRepository;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final UUID actorPersonId,
      final UUID subjectPersonId,
      final AuditOperation action,
      final AuditResource entityType,
      final UUID entityId) {
    record(institution, actorPersonId, subjectPersonId, action, entityType, entityId, Map.of());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final UUID actorPersonId,
      final UUID subjectPersonId,
      final AuditOperation action,
      final AuditResource entityType,
      final UUID entityId,
      final Map<String, Object> details) {
    final var actor = AuditActorContext.current();
    final UUID realActor = actor.personId() == null ? actorPersonId : actor.personId();
    final boolean onBehalf = actor.delegated() || !realActor.equals(subjectPersonId);
    final String requestId = AuditActorContext.currentRequestId();
    final Map<String, Object> eventDetails = identityDetails(actor, details);
    final Instant occurredAt = clock.instant();

    final AuditEvent event =
        AuditEvent.record(
            institution,
            AuditActorKind.INSTITUTION,
            realActor,
            null,
            subjectPersonId,
            action,
            entityType,
            entityId,
            onBehalf,
            requestId,
            null,
            eventDetails,
            occurredAt);

    auditEventRepository.save(event);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordSnapshotChange(
      @Nullable final Institution institution,
      @Nullable final UUID subjectPersonId,
      final AuditOperation action,
      final AuditResource entityType,
      final UUID entityId,
      final String name,
      final AuditSnapshot.Snapshot before,
      final AuditSnapshot.Snapshot after) {
    if (!before.values().equals(after.values())) {
      recordCurrentActor(
          institution,
          subjectPersonId,
          action,
          entityType,
          entityId,
          AuditOrigin.administration(),
          AuditChangeDetails.change(name, before, after));
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCurrentActor(
      @Nullable final Institution institution,
      @Nullable final UUID subjectPersonId,
      final AuditOperation action,
      final AuditResource entityType,
      final UUID entityId,
      final AuditOrigin origin,
      final Map<String, Object> details) {
    final var actor = AuditActorContext.current();
    final boolean onBehalf = actedOnBehalf(actor, origin, subjectPersonId);
    final String requestId = AuditActorContext.currentRequestId();
    final Map<String, Object> eventDetails = identityDetails(actor, details);
    final Instant occurredAt = clock.instant();

    final AuditEvent event =
        AuditEvent.record(
            institution,
            actor.kind(),
            actor.personId(),
            actor.platformAccountId(),
            subjectPersonId,
            action,
            entityType,
            entityId,
            onBehalf,
            requestId,
            origin,
            eventDetails,
            occurredAt);

    auditEventRepository.save(event);
  }

  private static boolean actedOnBehalf(
      final AuditActorContext actor,
      final AuditOrigin origin,
      final @Nullable UUID subjectPersonId) {
    final UUID actorPersonId = actor.personId();
    return (actor.delegated() || origin == AuditOrigin.SELF_SERVICE)
        && actorPersonId != null
        && subjectPersonId != null
        && !actorPersonId.equals(subjectPersonId);
  }

  private static Map<String, Object> identityDetails(
      final AuditActorContext actor, final Map<String, Object> details) {
    if (!actor.delegated()) {
      return details;
    }
    final var result = new HashMap<String, Object>(details);
    result.put("activePersonId", actor.activePersonId());
    return result;
  }
}
