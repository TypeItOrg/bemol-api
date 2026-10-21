package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"courseName", "trainingPathId", "preferredShifts"})
public record CourseWaitlistOptionsResponse(
    String courseName, UUID trainingPathId, List<String> preferredShifts) {}
