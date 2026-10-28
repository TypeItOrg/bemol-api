package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountEmailRequest(
    @NotBlank @Email @Size(max = 150) String email,
    @NotBlank @Size(max = 255) String currentPassword) {}
