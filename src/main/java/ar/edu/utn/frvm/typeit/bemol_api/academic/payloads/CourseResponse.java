package ar.edu.utn.frvm.typeit.bemol_api.academic.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "institutionName",
      "studyPlanId",
      "studyPlanName",
      "studyPlanVersion",
      "trainingPathId",
      "trainingPathName",
      "studyPlanSpaceId",
      "academicSpaceId",
      "academicSpaceName",
      "academicSpaceType",
      "academicSpaceFormat",
      "academicSpaceInstrumental",
      "academicLevelId",
      "academicLevelName",
      "instrumentId",
      "instrumentName",
      "academicYearId",
      "year",
      "status",
      "active",
      "classes",
      "deletedAt"
    })
public record CourseResponse(
    UUID id,
    UUID institutionId,
    String institutionName,
    UUID studyPlanId,
    String studyPlanName,
    int studyPlanVersion,
    UUID trainingPathId,
    String trainingPathName,
    @Nullable UUID studyPlanSpaceId,
    UUID academicSpaceId,
    String academicSpaceName,
    String academicSpaceType,
    String academicSpaceFormat,
    boolean academicSpaceInstrumental,
    @Schema(nullable = true) @Nullable UUID academicLevelId,
    @Schema(nullable = true) @Nullable String academicLevelName,
    @Schema(nullable = true) @Nullable UUID instrumentId,
    @Schema(nullable = true) @Nullable String instrumentName,
    UUID academicYearId,
    int year,
    String status,
    boolean active,
    List<CourseClassResponse> classes,
    @Schema(nullable = true) @Nullable Instant deletedAt) {

  public static CourseResponse from(final Course course) {
    return from(course, List.of());
  }

  public static CourseResponse from(final Course course, final List<CourseClassResponse> classes) {
    final var space = course.getAcademicSpace();
    final var plan = course.getStudyPlan();
    final var path = plan.getTrainingPath();
    final var year = course.getAcademicYear();
    final var studyPlanSpace = course.getStudyPlanSpace();
    final var level = studyPlanSpace == null ? null : studyPlanSpace.getAcademicLevel();
    final var instrument = course.getInstrument();
    return new CourseResponse(
        course.getId(),
        course.getInstitution().getId(),
        course.getInstitution().getName(),
        plan.getId(),
        plan.getName(),
        plan.getVersionNumber(),
        path.getId(),
        path.getName(),
        studyPlanSpace == null ? null : studyPlanSpace.getId(),
        space.getId(),
        space.getName(),
        space.getType().name(),
        space.getFormat().name(),
        space.isInstrumental(),
        level == null ? null : level.getId(),
        level == null ? null : level.getName(),
        instrument == null ? null : instrument.getId(),
        instrument == null ? null : instrument.getName(),
        year.getId(),
        year.getYear(),
        course.getStatus().name(),
        course.isActive(),
        classes,
        course.getDeletedAt());
  }
}
