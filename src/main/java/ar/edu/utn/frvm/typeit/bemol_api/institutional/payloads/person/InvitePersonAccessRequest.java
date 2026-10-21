package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitePersonAccessRequest(@NotBlank @Email @Size(max = 150) String email) {}
