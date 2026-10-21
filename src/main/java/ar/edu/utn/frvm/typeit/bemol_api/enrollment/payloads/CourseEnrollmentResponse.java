package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "studentId",
      "studentName",
      "courseId",
      "studyPlanSpaceId",
      "academicSpaceName",
      "academicLevelName",
      "studyPlanName",
      "studyPlanVersion",
      "trainingPathName",
      "trainingPathId",
      "instrumentId",
      "instrumentName",
      "courseClassId",
      "courseClassLabel",
      "teachers",
      "source",
      "status",
      "academicStatus",
      "enrolledAt",
      "completedAt",
      "withdrawnAt",
      "version",
      "schedules"
    })
public record CourseEnrollmentResponse(
    UUID id,
    UUID institutionId,
    UUID studentId,
    String studentName,
    UUID courseId,
    UUID studyPlanSpaceId,
    String academicSpaceName,
    @Schema(nullable = true) @Nullable String academicLevelName,
    String studyPlanName,
    int studyPlanVersion,
    String trainingPathName,
    UUID trainingPathId,
    @Schema(nullable = true) @Nullable UUID instrumentId,
    @Schema(nullable = true) @Nullable String instrumentName,
    UUID courseClassId,
    String courseClassLabel,
    List<CourseEnrollmentTeacherOptionResponse> teachers,
    CourseEnrollmentSource source,
    CourseEnrollmentStatus status,
    AcademicEnrollmentStatus academicStatus,
    Instant enrolledAt,
    @Schema(nullable = true) @Nullable Instant completedAt,
    @Schema(nullable = true) @Nullable Instant withdrawnAt,
    Long version,
    List<CourseEnrollmentScheduleResponse> schedules) {

  public static CourseEnrollmentResponse from(
      final CourseEnrollment enrollment, final List<CourseEnrollmentScheduleResponse> schedules) {
    return from(enrollment, schedules, List.of());
  }

  public static CourseEnrollmentResponse from(
      final CourseEnrollment enrollment,
      final List<CourseEnrollmentScheduleResponse> schedules,
      final List<CourseEnrollmentTeacherOptionResponse> teachers) {
    final var course = enrollment.getCourse();
    final var metadata = CourseEnrollmentCourseMetadata.from(course);
    final var student = enrollment.getStudent();
    final var person = student.getPerson();
    return new CourseEnrollmentResponse(
        enrollment.getId(),
        enrollment.getInstitution().getId(),
        student.getId(),
        person.getFirstName() + " " + person.getLastName(),
        metadata.courseId(),
        metadata.studyPlanSpaceId(),
        metadata.academicSpaceName(),
        metadata.academicLevelName(),
        metadata.studyPlanName(),
        metadata.studyPlanVersion(),
        metadata.trainingPathName(),
        course.getStudyPlan().getTrainingPath().getId(),
        metadata.instrumentId(),
        metadata.instrumentName(),
        enrollment.getCourseClass().getId(),
        enrollment.getCourseClass().displayName(),
        teachers,
        enrollment.getSource(),
        enrollment.getStatus(),
        enrollment.getAcademicStatus(),
        enrollment.getEnrolledAt(),
        enrollment.getCompletedAt(),
        enrollment.getWithdrawnAt(),
        enrollment.getVersion(),
        schedules);
  }
}
