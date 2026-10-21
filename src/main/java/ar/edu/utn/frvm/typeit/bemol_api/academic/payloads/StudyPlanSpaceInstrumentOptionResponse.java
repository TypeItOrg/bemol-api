package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"instrumentId", "name"})
public record StudyPlanSpaceInstrumentOptionResponse(UUID instrumentId, String name) {}
