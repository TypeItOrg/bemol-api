package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ActiveSessionResponse(
    UUID sessionId,
    String ipAddress,
    String userAgent,
    Instant startedAt,
    boolean currentSession) {}
