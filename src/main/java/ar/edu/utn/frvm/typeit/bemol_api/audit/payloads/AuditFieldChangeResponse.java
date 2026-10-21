package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"field", "label", "valueType", "before", "after"})
public record AuditFieldChangeResponse(
    String field,
    String label,
    @Schema(
            allowableValues = {
              "TEXT",
              "REFERENCE",
              "BOOLEAN",
              "NUMBER",
              "DATE",
              "DATETIME",
              "TIME",
              "JSON"
            })
        String valueType,
    @Schema(nullable = true) @Nullable String before,
    @Schema(nullable = true) @Nullable String after) {}
