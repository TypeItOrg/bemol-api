package ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SelectActiveIdentityRequest(
    @Schema(
            nullable = true,
            description = "Persona seleccionada; null vuelve a quien inició sesión.")
        @Nullable UUID personId) {}
