package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"id", "status", "occurredAt", "actorId", "accountType"})
public record EnrollmentAdmissionHistoryResponse(
    UUID id,
    String status,
    Instant occurredAt,
    @Schema(nullable = true) @Nullable UUID actorId,
    String accountType) {}
