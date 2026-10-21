package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record IdentifyLoginRequest(
    @NotNull(message = ValidationMessages.INSTITUTION_REQUIRED) UUID institutionId,
    @NotBlank(message = ValidationMessages.DOCUMENT_REQUIRED)
        @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
        String documentNumber) {}
