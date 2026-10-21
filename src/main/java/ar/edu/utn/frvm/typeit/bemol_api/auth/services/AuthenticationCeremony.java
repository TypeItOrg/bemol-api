package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationCeremony(
    String ceremonyId, String loginAttemptId, UUID userId, String optionsJson, Instant createdAt) {}
