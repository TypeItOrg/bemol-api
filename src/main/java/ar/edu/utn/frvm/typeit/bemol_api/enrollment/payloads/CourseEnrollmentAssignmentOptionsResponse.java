package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import java.util.List;
import java.util.UUID;

public record CourseEnrollmentAssignmentOptionsResponse(
    UUID courseId, String format, List<CourseEnrollmentClassOptionResponse> classes) {}
