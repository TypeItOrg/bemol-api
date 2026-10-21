package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import java.time.Instant;
import java.util.UUID;

public record LoginAttempt(
    String id, UUID userId, UUID institutionId, boolean hasActivePasskeys, Instant createdAt) {}
