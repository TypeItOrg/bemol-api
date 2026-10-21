package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.PASSWORD_MIN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateOwnAccessRequest(
    @NotBlank @Size(max = 128) String token,
    @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password,
    @NotBlank @Size(max = PASSWORD_MAX) String confirmPassword) {}
