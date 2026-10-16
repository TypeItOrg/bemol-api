package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"evaluation", "value"})
public record UpdateCourseEnrollmentGradeRequest(
    @NotBlank @Size(max = 150) String evaluation,
    @NotNull @DecimalMin("1") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal value,
    @Schema(nullable = true) @Nullable Long expectedVersion) {}
