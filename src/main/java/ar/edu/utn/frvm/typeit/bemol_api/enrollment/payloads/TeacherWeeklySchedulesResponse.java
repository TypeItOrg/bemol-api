package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(requiredProperties = {"weekStart", "weekEnd", "classes"})
public record TeacherWeeklySchedulesResponse(
    LocalDate weekStart, LocalDate weekEnd, List<TeacherCourseClassResponse> classes) {}
