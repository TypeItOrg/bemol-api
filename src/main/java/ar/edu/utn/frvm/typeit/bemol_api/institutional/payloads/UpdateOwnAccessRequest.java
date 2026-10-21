package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateOwnAccessRequest(
    @NotNull Boolean enabled, @Email @Size(max = 150) @Nullable String email) {}
