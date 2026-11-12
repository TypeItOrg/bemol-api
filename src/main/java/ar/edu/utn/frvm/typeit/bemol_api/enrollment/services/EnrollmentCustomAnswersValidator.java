package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentAnswerMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentCustomAnswersValidator {
  public Map<String, Object> validate(
      final @Nullable EnrollmentFormDefinition definition,
      final Map<String, ? extends @Nullable Object> answers,
      final boolean complete) {
    if (answers.size() > 200) {
      throw new EnrollmentValidationException(
          EnrollmentAnswerMessages.INVALID,
          Map.of("customAnswers", EnrollmentAnswerMessages.TOO_MANY));
    }

    final var questions = new LinkedHashMap<String, Question>();
    if (definition != null) {
      for (final var section : definition.sections()) {
        for (final var question : section.questions()) {
          questions.put(question.id().toString(), question);
        }
      }
    }

    final var errors = new LinkedHashMap<String, String>();
    final var normalized = new LinkedHashMap<String, Object>();
    for (final var entry : answers.entrySet()) {
      final var question = questions.get(entry.getKey());
      if (question == null) {
        errors.put("customAnswers", EnrollmentAnswerMessages.UNKNOWN_QUESTION);
        continue;
      }
      final var value = entry.getValue();
      if (value == null) {
        continue;
      }
      final var error = error(question, value);
      if (error != null) {
        errors.put(field(question), error);
        continue;
      }
      normalized.put(entry.getKey(), value instanceof List<?> values ? List.copyOf(values) : value);
    }

    if (complete) {
      for (final var question : questions.values()) {
        final var value = normalized.get(question.id().toString());
        if (question.required() && missing(value)) {
          errors.putIfAbsent(field(question), EnrollmentAnswerMessages.REQUIRED);
        }
      }
    }
    if (!errors.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentAnswerMessages.INVALID, errors);
    }

    return Map.copyOf(normalized);
  }

  private static @Nullable String error(final Question question, final Object value) {
    return switch (question.type()) {
      case SHORT_TEXT -> textError(value, 500, EnrollmentAnswerMessages.SHORT_TEXT_TOO_LONG);
      case LONG_TEXT -> textError(value, 10000, EnrollmentAnswerMessages.LONG_TEXT_TOO_LONG);
      case NUMBER -> numberError(value);
      case DATE -> dateError(value);
      case BOOLEAN -> value instanceof Boolean ? null : EnrollmentAnswerMessages.TYPE_INVALID;
      case SINGLE_CHOICE ->
          value instanceof String option
              ? (hasOption(question, option) ? null : EnrollmentAnswerMessages.OPTION_INVALID)
              : EnrollmentAnswerMessages.TYPE_INVALID;
      case MULTIPLE_CHOICE -> choicesError(question, value);
    };
  }

  private static @Nullable String textError(
      final Object value, final int limit, final String tooLong) {
    if (!(value instanceof String text)) {
      return EnrollmentAnswerMessages.TYPE_INVALID;
    }
    if (text.codePoints()
        .anyMatch(
            point ->
                point == 0
                    || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)) {
      return EnrollmentAnswerMessages.TEXT_INVALID;
    }
    return text.length() > limit ? tooLong : null;
  }

  private static @Nullable String numberError(final Object value) {
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof BigInteger
        || value instanceof BigDecimal) {
      return null;
    }
    if (value instanceof Double number) {
      return Double.isFinite(number) ? null : EnrollmentAnswerMessages.NUMBER_INVALID;
    }
    if (value instanceof Float number) {
      return Float.isFinite(number) ? null : EnrollmentAnswerMessages.NUMBER_INVALID;
    }
    return EnrollmentAnswerMessages.TYPE_INVALID;
  }

  private static @Nullable String dateError(final Object value) {
    if (!(value instanceof String date)) {
      return EnrollmentAnswerMessages.TYPE_INVALID;
    }
    if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
      return EnrollmentAnswerMessages.DATE_INVALID;
    }
    try {
      LocalDate.parse(date);
      return null;
    } catch (DateTimeParseException exception) {
      return EnrollmentAnswerMessages.DATE_INVALID;
    }
  }

  private static @Nullable String choicesError(final Question question, final Object value) {
    if (!(value instanceof List<?> choices)) {
      return EnrollmentAnswerMessages.TYPE_INVALID;
    }
    if (choices.size() > question.options().size()) {
      return EnrollmentAnswerMessages.OPTION_INVALID;
    }
    final var selected = new HashSet<String>();
    for (final var choice : choices) {
      if (!(choice instanceof String option)) {
        return EnrollmentAnswerMessages.TYPE_INVALID;
      }
      if (!hasOption(question, option) || !selected.add(option)) {
        return EnrollmentAnswerMessages.OPTION_INVALID;
      }
    }
    return null;
  }

  private static boolean hasOption(final Question question, final String value) {
    return question.options().stream().anyMatch(option -> option.id().toString().equals(value));
  }

  private static boolean missing(final @Nullable Object value) {
    return value == null
        || value instanceof String text && text.isBlank()
        || value instanceof List<?> values && values.isEmpty();
  }

  private static String field(final Question question) {
    return "customAnswers." + question.id();
  }
}
