package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"entity", "entityId", "operation", "previousKnown", "fields"})
public record AuditEntityChangeResponse(
    String entity,
    String entityId,
    @Schema(allowableValues = {"CREATED", "UPDATED", "DELETED"}) String operation,
    boolean previousKnown,
    List<AuditFieldChangeResponse> fields) {}
