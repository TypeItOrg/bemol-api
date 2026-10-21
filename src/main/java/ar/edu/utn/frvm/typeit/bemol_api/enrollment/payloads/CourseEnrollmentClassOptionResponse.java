package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "label", "teacherIds", "teachers", "days"})
public record CourseEnrollmentClassOptionResponse(
    UUID id,
    String label,
    List<UUID> teacherIds,
    List<CourseEnrollmentTeacherOptionResponse> teachers,
    List<CourseEnrollmentDayOptionResponse> days) {}
