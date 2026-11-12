package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "title",
      "headerText",
      "introductionText",
      "footerText",
      "showApplicantSignature",
      "showResponsibleSignature"
    })
public record EnrollmentPdfSettingsResponse(
    String title,
    @Schema(nullable = true) @Nullable String headerText,
    @Schema(nullable = true) @Nullable String introductionText,
    @Schema(nullable = true) @Nullable String footerText,
    boolean showApplicantSignature,
    boolean showResponsibleSignature) {
  public static EnrollmentPdfSettingsResponse from(final EnrollmentPdfSettings settings) {
    return new EnrollmentPdfSettingsResponse(
        settings.getTitle(),
        settings.getHeaderText(),
        settings.getIntroductionText(),
        settings.getFooterText(),
        settings.isShowApplicantSignature(),
        settings.isShowResponsibleSignature());
  }

  public static EnrollmentPdfSettingsResponse defaults() {
    return new EnrollmentPdfSettingsResponse(
        "Solicitud de inscripción", null, null, null, true, true);
  }
}
