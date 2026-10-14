package ar.edu.utn.frvm.typeit.boero_api.academic.payloads;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.DocumentDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "institutionName",
      "name",
      "allowedFormats",
      "active"
    })
public record PlatformDocumentDefinitionResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    String name,
    List<String> allowedFormats,
    boolean active) {
  public static PlatformDocumentDefinitionResponse from(final DocumentDefinition value) {
    return new PlatformDocumentDefinitionResponse(
        value.getId(),
        value.getInstitution().getId(),
        value.getInstitution().getName(),
        value.getName(),
        value.getAllowedFormats(),
        value.isActive());
  }
}
