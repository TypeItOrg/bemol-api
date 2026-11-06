package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
public record EnrollmentPdfData(
    InstitutionDetails institution,
    Instant generatedAt,
    @Nullable Instant submittedAt,
    EnrollmentPdfSettingsResponse settings,
    List<Section> sections,
    List<Selection> selections,
    boolean coursesSelected,
    boolean hasResponsible,
    @Nullable String logoDataUri) {
  public record InstitutionDetails(String name, String address, String phone, String email) {}

  public record Field(String label, String value) {}

  public record Section(String title, List<Field> fields) {}

  public record Selection(
      String name, String level, String instrument, String studyPlan, String status) {}
}
