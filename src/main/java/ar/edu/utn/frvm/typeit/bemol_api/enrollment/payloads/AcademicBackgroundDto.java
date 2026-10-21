package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EducationLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record AcademicBackgroundDto(
    @Schema(nullable = true) @Size(max = 255) @Nullable String secondarySchool,
    @Schema(nullable = true) @Nullable Boolean currentlyStudying,
    @Schema(nullable = true) @Nullable EducationLevel educationLevel,
    @Schema(nullable = true) @Size(max = 150) @Nullable String schoolOrigin,
    @Schema(nullable = true) @Size(max = 50) @Nullable String currentGradeYear,
    @Schema(nullable = true) @Nullable Boolean levelCompleted,
    @Schema(nullable = true) @Nullable Boolean secondaryCompleted,
    @Schema(nullable = true) @Size(max = 150) @Nullable String secondaryDegreeTitle) {
  public AcademicBackgroundDto() {
    this(null, null, null, null, null, null, null, null);
  }

  public @Nullable String getSecondarySchool() {
    return secondarySchool;
  }

  public @Nullable String getSchoolOrigin() {
    return schoolOrigin;
  }

  public @Nullable Boolean getCurrentlyStudying() {
    return currentlyStudying;
  }

  public @Nullable EducationLevel getEducationLevel() {
    return educationLevel;
  }

  public @Nullable String getCurrentGradeYear() {
    return currentGradeYear;
  }

  public @Nullable Boolean getSecondaryCompleted() {
    return secondaryCompleted;
  }

  public @Nullable Boolean getLevelCompleted() {
    return levelCompleted;
  }

  public @Nullable String getSecondaryDegreeTitle() {
    return secondaryDegreeTitle;
  }
}
