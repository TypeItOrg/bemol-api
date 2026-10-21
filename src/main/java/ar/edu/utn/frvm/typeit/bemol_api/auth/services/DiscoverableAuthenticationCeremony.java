package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DiscoverableAuthenticationCeremony(
    UUID institutionId,
    String hostOrigin,
    Set<String> trustedOrigins,
    String optionsJson,
    Instant createdAt) {}
