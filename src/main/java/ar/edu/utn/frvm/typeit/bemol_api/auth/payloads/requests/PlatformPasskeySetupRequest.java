package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformPasskeySetupRequest(
    @NotBlank @Size(max = 36) String loginAttemptId,
    @NotBlank(message = AuthMessages.PASSKEY_CODE_REQUIRED)
        @Pattern(
            regexp = "\\s*([a-f0-9]{64}|[A-Za-z0-9]{4}[\\s-]?[A-Za-z0-9]{4})\\s*",
            message = AuthMessages.PASSKEY_CODE_REQUIRED)
        String code,
    @NotBlank(message = AuthMessages.PASSKEY_LABEL_REQUIRED)
        @Size(min = 1, max = 100, message = AuthMessages.PASSKEY_LABEL_LENGTH)
        String label) {}
