package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
public record EnrollmentPdfData(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable UUID institutionId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) InstitutionDetails institution,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant generatedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable Instant submittedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EnrollmentPdfSettingsResponse settings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Section> sections,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Selection> selections,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean coursesSelected,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasResponsible,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable String logoUrl) {
  public record InstitutionDetails(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String phone,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {}

  public record Field(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {}

  public record Section(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Field> fields,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowPageBreaks) {
    public Section(final String title, final List<Field> fields) {
      this(title, fields, false);
    }
  }

  public record Selection(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String level,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String instrument,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String studyPlan,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status) {}
}
