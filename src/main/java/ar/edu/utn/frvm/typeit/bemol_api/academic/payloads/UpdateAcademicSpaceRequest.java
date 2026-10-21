package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateAcademicSpaceRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 1000) @Nullable String description,
    @NotNull AcademicSpaceType type,
    @NotNull AcademicSpaceFormat format,
    boolean instrumental) {

  public UpdateAcademicSpaceRequest(
      final String name,
      final @Nullable String description,
      final AcademicSpaceType type,
      final AcademicSpaceFormat format) {
    this(name, description, type, format, false);
  }
}
