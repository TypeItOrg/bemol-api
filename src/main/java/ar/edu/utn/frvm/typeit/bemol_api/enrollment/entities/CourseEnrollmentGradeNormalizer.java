package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

final class CourseEnrollmentGradeNormalizer {

  private static final BigDecimal MIN_VALUE = new BigDecimal("1");
  private static final BigDecimal MAX_VALUE = new BigDecimal("10");

  private CourseEnrollmentGradeNormalizer() {}

  static String normalizeEvaluation(final @Nullable String evaluation) {
    if (evaluation == null) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_REQUIRED);
    }

    final String trimmed = evaluation.strip();

    if (trimmed.isEmpty()) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_REQUIRED);
    }

    if (trimmed.length() > CourseEnrollmentGrade.EVALUATION_MAX_LENGTH) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_TOO_LONG);
    }

    return trimmed;
  }

  static BigDecimal normalizeValue(final @Nullable BigDecimal value) {
    if (value == null) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_REQUIRED);
    }

    // Check bounds before stripTrailingZeros()/toPlainString() so huge positive exponents
    // (e.g. 1E10000000) are rejected without allocating large strings.
    if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_OUT_OF_RANGE);
    }

    final BigDecimal normalized;

    try {
      normalized = value.stripTrailingZeros();
    } catch (final ArithmeticException exception) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_INVALID);
    }

    if (normalized.scale() > 2) {
      throw new EnrollmentValidationException(GradeMessages.VALUE_TOO_MANY_DECIMALS);
    }

    final BigDecimal plain = new BigDecimal(normalized.toPlainString());

    return plain.setScale(Math.max(0, Math.min(2, plain.scale())), RoundingMode.UNNECESSARY);
  }
}
