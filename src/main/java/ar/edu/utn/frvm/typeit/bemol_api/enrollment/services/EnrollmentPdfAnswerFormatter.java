package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class EnrollmentPdfAnswerFormatter {
  private EnrollmentPdfAnswerFormatter() {}

  static String format(final Question question, final @Nullable Object value) {
    if (value == null
        || value instanceof String text && text.isBlank()
        || value instanceof List<?> values && values.isEmpty()) {
      return "Sin informar";
    }
    return switch (question.type()) {
      case SHORT_TEXT, LONG_TEXT -> value.toString();
      case NUMBER -> new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
      case DATE ->
          LocalDate.parse(value.toString()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      case BOOLEAN -> EnrollmentPdfDataMapper.answer((Boolean) value);
      case SINGLE_CHOICE -> label(question, value);
      case MULTIPLE_CHOICE ->
          ((List<?>) value)
              .stream().map(option -> label(question, option)).collect(Collectors.joining(", "));
    };
  }

  private static String label(final Question question, final Object id) {
    return question.options().stream()
        .filter(option -> option.id().toString().equals(id))
        .map(option -> option.label())
        .findFirst()
        .orElse("Sin informar");
  }
}
