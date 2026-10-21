package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CourseEnrollmentCourseMetadata(
    UUID courseId,
    UUID studyPlanSpaceId,
    String academicSpaceName,
    @Nullable String academicLevelName,
    String studyPlanName,
    int studyPlanVersion,
    String trainingPathName,
    String format,
    @Nullable UUID instrumentId,
    @Nullable String instrumentName,
    @Nullable String requirementType,
    @Nullable String approvalMode,
    boolean instrumental) {

  public static CourseEnrollmentCourseMetadata from(final Course course) {
    final var space = course.getStudyPlanSpace();
    final var academicSpace = space.getAcademicSpace();
    final var level = space.getAcademicLevel();
    final var plan = space.getStudyPlan();
    final var instrument = course.getInstrument();
    return new CourseEnrollmentCourseMetadata(
        course.getId(),
        space.getId(),
        academicSpace.getName(),
        level == null ? null : level.getName(),
        plan.getName(),
        plan.getVersionNumber(),
        plan.getTrainingPath().getName(),
        academicSpace.getFormat().name(),
        instrument == null ? null : instrument.getId(),
        instrument == null ? null : instrument.getName(),
        space.getRequirementType() == null ? null : space.getRequirementType().name(),
        space.getApprovalMode() == null ? null : space.getApprovalMode().name(),
        academicSpace.isInstrumental());
  }
}
