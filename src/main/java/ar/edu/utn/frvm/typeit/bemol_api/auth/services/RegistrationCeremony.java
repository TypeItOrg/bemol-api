package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import java.time.Instant;
import java.util.UUID;

public record RegistrationCeremony(
    String ceremonyId,
    UUID userId,
    UUID sessionId,
    String label,
    String optionsJson,
    Instant createdAt) {}
