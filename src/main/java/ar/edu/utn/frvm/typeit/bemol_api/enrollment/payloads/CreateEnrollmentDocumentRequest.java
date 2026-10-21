package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateEnrollmentDocumentRequest(
    @NotBlank @Size(max = 2000) String reason,
    @NotEmpty @Size(max = 50)
        List<@jakarta.validation.constraints.NotNull @Valid EnrollmentDocumentRequestItem>
            documents) {}
