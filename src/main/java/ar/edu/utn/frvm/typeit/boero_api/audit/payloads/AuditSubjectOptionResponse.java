package ar.edu.utn.frvm.typeit.boero_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"subjectPersonId", "label", "institutionName"})
public record AuditSubjectOptionResponse(
    UUID subjectPersonId,
    String label,
    @Schema(nullable = true) @Nullable String institutionName) {}
