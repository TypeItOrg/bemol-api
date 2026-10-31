package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformPasskeySetupRequest(
    @NotBlank @Size(max = 36) String loginAttemptId,
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String code,
    @NotBlank @Size(max = 100) String label) {}
