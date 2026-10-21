package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"level", "spaces"})
public record AcademicLevelCurriculumResponse(
    AcademicLevelResponse level, List<StudyPlanSpaceResponse> spaces) {}
