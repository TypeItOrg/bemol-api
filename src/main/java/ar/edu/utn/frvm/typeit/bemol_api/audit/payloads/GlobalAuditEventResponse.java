package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "actorKind",
      "actorId",
      "actorName",
      "subjectPersonId",
      "subjectName",
      "institutionId",
      "institutionName",
      "action",
      "entityType",
      "entityId",
      "actedOnBehalf",
      "requestId",
      "origin",
      "details",
      "occurredAt",
      "actionLabel",
      "entityLabel",
      "module",
      "moduleLabel"
    })
public record GlobalAuditEventResponse(
    UUID id,
    AuditActorKind actorKind,
    @Schema(nullable = true) @Nullable UUID actorId,
    @Schema(nullable = true) @Nullable String actorName,
    @Schema(nullable = true) @Nullable UUID subjectPersonId,
    @Schema(nullable = true) @Nullable String subjectName,
    @Schema(nullable = true) @Nullable UUID institutionId,
    @Schema(nullable = true) @Nullable String institutionName,
    String action,
    String entityType,
    UUID entityId,
    boolean actedOnBehalf,
    @Schema(nullable = true) @Nullable String requestId,
    @Schema(nullable = true) @Nullable AuditOrigin origin,
    Map<String, Object> details,
    Instant occurredAt,
    String actionLabel,
    String entityLabel,
    @Schema(nullable = true) @Nullable String module,
    @Schema(nullable = true) @Nullable String moduleLabel) {
  public GlobalAuditEventResponse withActor(
      @Nullable final UUID actorId, @Nullable final String actorName) {
    return new GlobalAuditEventResponse(
        id,
        actorKind,
        actorId,
        actorName,
        subjectPersonId,
        subjectName,
        institutionId,
        institutionName,
        action,
        entityType,
        entityId,
        actedOnBehalf,
        requestId,
        origin,
        details,
        occurredAt,
        actionLabel,
        entityLabel,
        module,
        moduleLabel);
  }

  public GlobalAuditEventResponse withDetails(final Map<String, Object> details) {
    return new GlobalAuditEventResponse(
        id,
        actorKind,
        actorId,
        actorName,
        subjectPersonId,
        subjectName,
        institutionId,
        institutionName,
        action,
        entityType,
        entityId,
        actedOnBehalf,
        requestId,
        origin,
        details,
        occurredAt,
        actionLabel,
        entityLabel,
        module,
        moduleLabel);
  }

  public GlobalAuditEventResponse withoutRequestContext() {
    return new GlobalAuditEventResponse(
        id,
        actorKind,
        actorId,
        actorName,
        subjectPersonId,
        subjectName,
        institutionId,
        institutionName,
        action,
        entityType,
        entityId,
        actedOnBehalf,
        null,
        null,
        details,
        occurredAt,
        actionLabel,
        entityLabel,
        module,
        moduleLabel);
  }
}
