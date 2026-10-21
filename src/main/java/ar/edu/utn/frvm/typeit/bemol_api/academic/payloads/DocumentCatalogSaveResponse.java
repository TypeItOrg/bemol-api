package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"document", "assignments", "affectedTrainingPaths", "affectedDrafts"})
public record DocumentCatalogSaveResponse(
    DocumentDefinitionResponse document,
    List<DocumentRequirementResponse> assignments,
    int affectedTrainingPaths,
    int affectedDrafts) {}
