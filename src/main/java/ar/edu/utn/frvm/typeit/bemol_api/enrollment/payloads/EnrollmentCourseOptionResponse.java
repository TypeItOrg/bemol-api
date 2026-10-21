package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "courseId",
      "studyPlanSpaceId",
      "academicSpaceName",
      "academicLevelName",
      "studyPlanName",
      "studyPlanVersion",
      "trainingPathName",
      "format",
      "instrumentId",
      "instrumentName",
      "requirementType",
      "approvalMode",
      "instrumental",
      "hasCapacity",
      "eligibility",
      "academicYear"
    })
public record EnrollmentCourseOptionResponse(
    UUID courseId,
    UUID studyPlanSpaceId,
    String academicSpaceName,
    @Schema(nullable = true) @Nullable String academicLevelName,
    String studyPlanName,
    int studyPlanVersion,
    String trainingPathName,
    String format,
    @Schema(nullable = true) @Nullable UUID instrumentId,
    @Schema(nullable = true) @Nullable String instrumentName,
    @Nullable String requirementType,
    @Nullable String approvalMode,
    boolean instrumental,
    boolean hasCapacity,
    AcademicEligibilityResponse eligibility,
    int academicYear) {

  public static EnrollmentCourseOptionResponse from(
      final Course course,
      final boolean hasCapacity,
      final AcademicEligibilityResponse eligibility) {
    final var metadata = CourseEnrollmentCourseMetadata.from(course);
    return new EnrollmentCourseOptionResponse(
        metadata.courseId(),
        metadata.studyPlanSpaceId(),
        metadata.academicSpaceName(),
        metadata.academicLevelName(),
        metadata.studyPlanName(),
        metadata.studyPlanVersion(),
        metadata.trainingPathName(),
        metadata.format(),
        metadata.instrumentId(),
        metadata.instrumentName(),
        metadata.requirementType(),
        metadata.approvalMode(),
        metadata.instrumental(),
        hasCapacity,
        eligibility,
        course.getAcademicYear().getYear());
  }
}
