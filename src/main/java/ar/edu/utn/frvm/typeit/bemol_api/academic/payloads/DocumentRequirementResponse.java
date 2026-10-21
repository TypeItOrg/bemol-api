package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.DocumentRequirementLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "documentId",
      "revision",
      "definitionRevision",
      "name",
      "instructions",
      "specificInstructions",
      "level",
      "allowedFormats",
      "displayOrder",
      "active",
      "documentActive",
      "trainingPathId",
      "trainingPathName"
    })
public record DocumentRequirementResponse(
    UUID id,
    UUID documentId,
    long revision,
    long definitionRevision,
    String name,
    String instructions,
    @Schema(nullable = true) @Nullable String specificInstructions,
    DocumentRequirementLevel level,
    List<String> allowedFormats,
    int displayOrder,
    boolean active,
    boolean documentActive,
    UUID trainingPathId,
    String trainingPathName) {
  public static DocumentRequirementResponse from(final TrainingPathDocumentRequirement value) {
    return new DocumentRequirementResponse(
        value.getId(),
        value.getDocument().getId(),
        value.getRevision(),
        value.getDocument().getRevision(),
        value.getName(),
        value.getInstructions(),
        value.getSpecificInstructions(),
        value.getLevel(),
        value.getAllowedFormats(),
        value.getDisplayOrder(),
        value.isActive(),
        value.getDocument().isActive(),
        value.getTrainingPath().getId(),
        value.getTrainingPath().getName());
  }
}
