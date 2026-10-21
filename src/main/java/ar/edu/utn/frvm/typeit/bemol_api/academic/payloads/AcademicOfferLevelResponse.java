package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"id", "name", "displayOrder", "description", "spaces"})
public record AcademicOfferLevelResponse(
    UUID id,
    String name,
    int displayOrder,
    @Schema(nullable = true) @Nullable String description,
    List<AcademicOfferSpaceResponse> spaces) {

  public static AcademicOfferLevelResponse from(
      final AcademicLevel level, final List<AcademicOfferSpaceResponse> spaces) {
    return new AcademicOfferLevelResponse(
        level.getId(), level.getName(), level.getDisplayOrder(), level.getDescription(), spaces);
  }
}
