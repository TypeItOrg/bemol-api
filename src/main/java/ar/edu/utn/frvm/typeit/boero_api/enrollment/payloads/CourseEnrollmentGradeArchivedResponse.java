package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"gradeId", "lastEvaluation", "deletedAt", "deletionType"})
public record CourseEnrollmentGradeArchivedResponse(
    UUID gradeId,
    String lastEvaluation,
    @Schema(nullable = true) @Nullable BigDecimal lastValue,
    Instant deletedAt,
    CourseEnrollmentGradeHistoryOperation deletionType) {}
