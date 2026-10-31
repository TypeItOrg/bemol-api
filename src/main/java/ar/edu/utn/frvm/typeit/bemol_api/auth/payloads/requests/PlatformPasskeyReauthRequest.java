package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlatformPasskeyReauthRequest(
    @NotBlank @Pattern(regexp = "register|revoke:[a-fA-F0-9-]{36}") String target) {}
