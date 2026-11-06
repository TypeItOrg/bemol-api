package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record EnrollmentPdfSettingsRequest(
    @NotBlank(message = EnrollmentMessages.PDF_TITLE_REQUIRED)
        @Size(max = 150, message = EnrollmentMessages.PDF_TITLE_MAX)
        String title,
    @Size(max = 500, message = EnrollmentMessages.PDF_HEADER_MAX) @Nullable String headerText,
    @Size(max = 1000, message = EnrollmentMessages.PDF_INTRODUCTION_MAX)
        @Nullable String introductionText,
    @Size(max = 500, message = EnrollmentMessages.PDF_FOOTER_MAX) @Nullable String footerText,
    @NotNull(message = EnrollmentMessages.PDF_SIGNATURE_REQUIRED) Boolean showApplicantSignature,
    @NotNull(message = EnrollmentMessages.PDF_SIGNATURE_REQUIRED)
        Boolean showResponsibleSignature) {}
