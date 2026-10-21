package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"actorKind", "actorId", "label"})
public record AuditActorOptionResponse(AuditActorKind actorKind, UUID actorId, String label) {}
