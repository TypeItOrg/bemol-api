package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmAccountEmailRequest(@NotBlank @Size(max = 256) String token) {}
