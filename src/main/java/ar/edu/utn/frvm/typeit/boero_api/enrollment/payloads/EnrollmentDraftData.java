package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@Schema(
    requiredProperties = {
      "personalData",
      "academicBackground",
      "healthInclusion",
      "responsible",
      "preference",
      "careerSelection",
      "academicSpaceSelection",
      "instrumentSelection",
      "courses",
      "attachments"
    })
public record EnrollmentDraftData(
    @Schema(nullable = true) @Nullable PersonalDataDto personalData,
    @Schema(
            nullable = true,
            description =
                "Si se incluye, la escolaridad adaptable se reemplaza como una fotografía completa: los campos nulos eliminan respuestas anteriores. Si se omite, se conserva sin cambios.")
        @Valid
        @Nullable AcademicBackgroundDto academicBackground,
    @Schema(nullable = true) @Nullable HealthInclusionDto healthInclusion,
    @Schema(nullable = true) @Nullable ResponsibleDto responsible,
    @Schema(nullable = true) @Nullable PreferenceDto preference,
    @Schema(nullable = true) @Nullable CareerSelectionDto careerSelection,
    @Schema(nullable = true) @Nullable AcademicSpaceSelectionDto academicSpaceSelection,
    @Schema(nullable = true) @Nullable InstrumentSelectionDto instrumentSelection,
    @Schema(nullable = true) @Nullable List<@NotNull @Valid CourseSelectionDto> courses,
    @Schema(nullable = true) @Nullable List<AttachmentDto> attachments) {
  public EnrollmentDraftData() {
    this(null, null, null, null, null, null, null, null, new ArrayList<>(), new ArrayList<>());
  }

  public EnrollmentDraftData(
      final PersonalDataDto personalData,
      final AcademicBackgroundDto academicBackground,
      final HealthInclusionDto healthInclusion,
      final ResponsibleDto responsible,
      final PreferenceDto preference,
      final CareerSelectionDto careerSelection,
      final AcademicSpaceSelectionDto academicSpaceSelection,
      final InstrumentSelectionDto instrumentSelection,
      final List<AttachmentDto> attachments) {
    this(
        personalData,
        academicBackground,
        healthInclusion,
        responsible,
        preference,
        careerSelection,
        academicSpaceSelection,
        instrumentSelection,
        new ArrayList<>(),
        attachments);
  }

  public EnrollmentDraftData {
    attachments = attachments == null ? new ArrayList<>() : attachments;
  }

  public @Nullable PersonalDataDto getPersonalData() {
    return personalData;
  }

  public @Nullable AcademicBackgroundDto getAcademicBackground() {
    return academicBackground;
  }

  public @Nullable HealthInclusionDto getHealthInclusion() {
    return healthInclusion;
  }

  public @Nullable ResponsibleDto getResponsible() {
    return responsible;
  }

  public @Nullable PreferenceDto getPreference() {
    return preference;
  }

  public @Nullable CareerSelectionDto getCareerSelection() {
    return careerSelection;
  }

  public @Nullable AcademicSpaceSelectionDto getAcademicSpaceSelection() {
    return academicSpaceSelection;
  }

  public @Nullable InstrumentSelectionDto getInstrumentSelection() {
    return instrumentSelection;
  }

  public @Nullable List<CourseSelectionDto> getCourses() {
    return courses;
  }

  public @Nullable List<AttachmentDto> getAttachments() {
    return attachments;
  }
}
