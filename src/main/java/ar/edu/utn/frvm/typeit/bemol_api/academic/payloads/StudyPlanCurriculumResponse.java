package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"studyPlan", "levels", "unassignedSpaces", "prerequisites"})
public record StudyPlanCurriculumResponse(
    StudyPlanResponse studyPlan,
    List<AcademicLevelCurriculumResponse> levels,
    List<StudyPlanSpaceResponse> unassignedSpaces,
    List<PrerequisiteResponse> prerequisites) {}
