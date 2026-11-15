package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyRenameRequest(
    @NotBlank(message = AuthMessages.PASSKEY_LABEL_REQUIRED)
        @Size(min = 1, max = 100, message = AuthMessages.PASSKEY_LABEL_LENGTH)
        String label) {}
