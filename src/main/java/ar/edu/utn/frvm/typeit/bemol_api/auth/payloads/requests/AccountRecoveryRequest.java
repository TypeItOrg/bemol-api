package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountRecoveryRequest(@NotBlank @Size(max = 255) String currentPassword) {}
