package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyRenameRequest(
    @NotBlank(message = "El nombre de la passkey es requerido.")
        @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres.")
        String label) {}
