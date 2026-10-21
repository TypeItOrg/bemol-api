package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceInstrumentOptionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"studyPlanSpaceId", "requiresInstrument", "instruments"})
public record EnrollmentStudyPlanSpaceInstrumentOptionsResponse(
    UUID studyPlanSpaceId,
    boolean requiresInstrument,
    List<StudyPlanSpaceInstrumentOptionResponse> instruments) {}
