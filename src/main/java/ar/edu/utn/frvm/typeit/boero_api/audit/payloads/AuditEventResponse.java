package ar.edu.utn.frvm.typeit.boero_api.audit.payloads;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuditEventResponse(
    UUID id,
    UUID actorPersonId,
    @Schema(nullable = true) @Nullable String actorName,
    UUID subjectPersonId,
    @Schema(nullable = true) @Nullable String subjectName,
    AuditAction action,
    AuditEntityType entityType,
    UUID entityId,
    boolean actedOnBehalf,
    @Schema(nullable = true) @Nullable String requestId,
    Instant occurredAt) {

  public static AuditEventResponse from(
      final AuditEvent event,
      @Nullable final String actorName,
      @Nullable final String subjectName) {
    return new AuditEventResponse(
        event.getId(),
        event.getActorPersonId(),
        actorName,
        event.getSubjectPersonId(),
        subjectName,
        event.getAction(),
        event.getEntityType(),
        event.getEntityId(),
        event.isActedOnBehalf(),
        event.getRequestId(),
        event.getOccurredAt());
  }
}
