package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"personId", "label", "institutionName"})
public record AuditPersonOptionResponse(
    UUID personId, String label, @Schema(nullable = true) @Nullable String institutionName) {}
