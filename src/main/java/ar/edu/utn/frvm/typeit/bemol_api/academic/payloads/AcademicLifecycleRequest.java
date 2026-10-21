package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import jakarta.validation.constraints.Size;

public record AcademicLifecycleRequest(@Size(max = 500) String reason) {}
