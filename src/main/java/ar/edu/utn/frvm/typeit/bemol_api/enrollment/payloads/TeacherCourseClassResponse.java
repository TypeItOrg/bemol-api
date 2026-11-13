package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.InstrumentIcon;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "courseId",
      "academicSpaceName",
      "instrumentName",
      "instrumentIcon",
      "classLabel",
      "courseClass"
    })
public record TeacherCourseClassResponse(
    UUID courseId,
    String academicSpaceName,
    @Schema(nullable = true) @Nullable String instrumentName,
    @Schema(nullable = true) @Nullable InstrumentIcon instrumentIcon,
    String classLabel,
    CourseClassResponse courseClass) {}
