package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"entityType", "entityId", "label", "institutionName"})
public record AuditEntityOptionResponse(
    AuditEntityType entityType,
    UUID entityId,
    String label,
    @Schema(nullable = true) @Nullable String institutionName) {}
