package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailVerificationRequest(
    @NotBlank(message = AuthMessages.EMAIL_VERIFICATION_TOKEN_REQUIRED)
        @Pattern(
            regexp = "[A-Za-z0-9_-]{43}",
            message = AuthMessages.EMAIL_VERIFICATION_TOKEN_FORMAT)
        String token) {}
