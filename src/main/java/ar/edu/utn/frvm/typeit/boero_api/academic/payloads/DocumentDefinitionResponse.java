package ar.edu.utn.frvm.typeit.boero_api.academic.payloads;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "name",
      "instructions",
      "allowedFormats",
      "active",
      "revision",
      "affectedTrainingPaths",
      "affectedDrafts",
      "canChangeInstitution"
    })
public record DocumentDefinitionResponse(
    UUID id,
    UUID institutionId,
    String name,
    String instructions,
    List<String> allowedFormats,
    boolean active,
    long revision,
    @Schema(nullable = true) @Nullable Long affectedTrainingPaths,
    @Schema(nullable = true) @Nullable Long affectedDrafts,
    boolean canChangeInstitution) {
  public DocumentDefinitionResponse withImpact(final long paths, final long drafts) {
    return new DocumentDefinitionResponse(
        id,
        institutionId,
        name,
        instructions,
        allowedFormats,
        active,
        revision,
        paths,
        drafts,
        canChangeInstitution);
  }

  public static DocumentDefinitionResponse from(final DocumentDefinition value) {
    return from(value, false);
  }

  public static DocumentDefinitionResponse from(
      final DocumentDefinition value, final boolean canChangeInstitution) {
    return new DocumentDefinitionResponse(
        value.getId(),
        value.getInstitution().getId(),
        value.getName(),
        value.getInstructions(),
        value.getAllowedFormats(),
        value.isActive(),
        value.getRevision(),
        null,
        null,
        canChangeInstitution);
  }
}
