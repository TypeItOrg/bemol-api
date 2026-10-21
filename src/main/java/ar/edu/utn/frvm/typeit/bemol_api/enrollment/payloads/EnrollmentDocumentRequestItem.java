package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EnrollmentDocumentRequestItem(
    @NotNull UUID documentId, @NotNull DocumentRequirementLevel level) {}
