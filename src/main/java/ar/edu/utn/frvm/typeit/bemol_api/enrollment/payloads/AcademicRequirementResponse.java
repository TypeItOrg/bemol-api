package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequiredCondition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "prerequisiteId",
      "studyPlanSpaceId",
      "academicSpaceName",
      "requiredCondition",
      "evidence",
      "satisfied"
    })
public record AcademicRequirementResponse(
    UUID prerequisiteId,
    UUID studyPlanSpaceId,
    String academicSpaceName,
    RequiredCondition requiredCondition,
    List<AcademicEnrollmentStatus> evidence,
    boolean satisfied) {}
