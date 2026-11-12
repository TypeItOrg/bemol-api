package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentAnswerMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EnrollmentCustomAnswersValidatorTest {
  private final EnrollmentCustomAnswersValidator validator = new EnrollmentCustomAnswersValidator();

  @ParameterizedTest
  @MethodSource("validValues")
  void preservesValidTypedValues(final EnrollmentFormQuestionType type, final Object value) {
    final var question = question(1, type, true);

    assertThat(validator.validate(definition(question), Map.of(key(question), value), true))
        .containsEntry(key(question), value);
  }

  static Stream<Arguments> validValues() {
    return Stream.of(
        Arguments.of(EnrollmentFormQuestionType.SHORT_TEXT, "Texto con espacios  "),
        Arguments.of(EnrollmentFormQuestionType.LONG_TEXT, "Texto\nmultilínea"),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, 0),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, Long.MAX_VALUE),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, new BigDecimal("1234567890.123456789")),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, new BigInteger("999999999999999999999999")),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, 3.5d),
        Arguments.of(EnrollmentFormQuestionType.DATE, "2024-02-29"),
        Arguments.of(EnrollmentFormQuestionType.BOOLEAN, false),
        Arguments.of(EnrollmentFormQuestionType.SINGLE_CHOICE, id(101).toString()),
        Arguments.of(
            EnrollmentFormQuestionType.MULTIPLE_CHOICE,
            List.of(id(102).toString(), id(101).toString())));
  }

  @ParameterizedTest
  @MethodSource("invalidTypes")
  void rejectsValuesWithoutCoercion(final EnrollmentFormQuestionType type, final Object value) {
    final var question = question(1, type, false);

    invalid(question, value, EnrollmentAnswerMessages.TYPE_INVALID);
  }

  static Stream<Arguments> invalidTypes() {
    return Stream.of(
        Arguments.of(EnrollmentFormQuestionType.SHORT_TEXT, 42),
        Arguments.of(EnrollmentFormQuestionType.LONG_TEXT, List.of("texto")),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, "42"),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, true),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, new AtomicInteger(4)),
        Arguments.of(EnrollmentFormQuestionType.DATE, LocalDate.of(2026, 11, 6)),
        Arguments.of(EnrollmentFormQuestionType.BOOLEAN, "false"),
        Arguments.of(EnrollmentFormQuestionType.BOOLEAN, 0),
        Arguments.of(EnrollmentFormQuestionType.SINGLE_CHOICE, List.of(id(101).toString())),
        Arguments.of(EnrollmentFormQuestionType.MULTIPLE_CHOICE, id(101).toString()),
        Arguments.of(EnrollmentFormQuestionType.MULTIPLE_CHOICE, List.of(42)),
        Arguments.of(EnrollmentFormQuestionType.SHORT_TEXT, Map.of("value", "texto")),
        Arguments.of(EnrollmentFormQuestionType.LONG_TEXT, new String[] {"texto"}));
  }

  @Test
  void rejectsNonFiniteNumbers() {
    final var question = question(1, EnrollmentFormQuestionType.NUMBER, false);
    for (final var number :
        List.of(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY)) {
      invalid(question, number, EnrollmentAnswerMessages.NUMBER_INVALID);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-02-29",
        "2024-02-30",
        "2024-13-01",
        "2024-1-01",
        "06/11/2026",
        "2026-11-06T12:00:00Z",
        ""
      })
  void rejectsInvalidOrNonIsoDates(final String value) {
    invalid(
        question(1, EnrollmentFormQuestionType.DATE, false),
        value,
        EnrollmentAnswerMessages.DATE_INVALID);
  }

  @Test
  void enforcesTextLimitsWithoutTrimmingTheValue() {
    final var shortText = question(1, EnrollmentFormQuestionType.SHORT_TEXT, false);
    final var longText = question(2, EnrollmentFormQuestionType.LONG_TEXT, false);
    assertThat(
            validator.validate(
                definition(shortText), Map.of(key(shortText), "x".repeat(500)), false))
        .containsEntry(key(shortText), "x".repeat(500));
    assertThat(
            validator.validate(
                definition(longText), Map.of(key(longText), "x".repeat(10000)), false))
        .containsEntry(key(longText), "x".repeat(10000));
    invalid(shortText, "x".repeat(501), EnrollmentAnswerMessages.SHORT_TEXT_TOO_LONG);
    invalid(longText, "x".repeat(10001), EnrollmentAnswerMessages.LONG_TEXT_TOO_LONG);
  }

  @ParameterizedTest
  @EnumSource(
      value = EnrollmentFormQuestionType.class,
      names = {"SHORT_TEXT", "LONG_TEXT"})
  void rejectsNulAndUnpairedSurrogates(final EnrollmentFormQuestionType type) {
    final var question = question(1, type, false);
    for (final var character :
        List.of((char) 0, Character.MIN_HIGH_SURROGATE, Character.MIN_LOW_SURROGATE)) {
      invalid(question, "antes" + character + "después", EnrollmentAnswerMessages.TEXT_INVALID);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EnrollmentFormQuestionType.class,
      names = {"SHORT_TEXT", "LONG_TEXT"})
  void preservesValidSurrogatePairsAndExistingLengthLimits(final EnrollmentFormQuestionType type) {
    final var question = question(1, type, true);
    final var shortText = type == EnrollmentFormQuestionType.SHORT_TEXT;
    final var limit = shortText ? 500 : 10000;
    final var value = "x".repeat(limit - 2) + "🎵";

    assertThat(validator.validate(definition(question), Map.of(key(question), value), true))
        .containsEntry(key(question), value);
    invalid(
        question,
        value + "x",
        shortText
            ? EnrollmentAnswerMessages.SHORT_TEXT_TOO_LONG
            : EnrollmentAnswerMessages.LONG_TEXT_TOO_LONG);
  }

  @Test
  void draftAllowsIncompleteAnswersButSubmissionRequiresNonEmptyAnswers() {
    final var text = question(1, EnrollmentFormQuestionType.SHORT_TEXT, true);
    final var choices = question(2, EnrollmentFormQuestionType.MULTIPLE_CHOICE, true);
    final var number = question(3, EnrollmentFormQuestionType.NUMBER, true);
    final var form = definition(text, choices, number);
    final var answers = new LinkedHashMap<String, @Nullable Object>();
    answers.put(key(text), " \t ");
    answers.put(key(choices), List.of());
    answers.put(key(number), null);

    assertThat(validator.validate(form, answers, false))
        .containsEntry(key(text), " \t ")
        .containsEntry(key(choices), List.of())
        .doesNotContainKey(key(number));
    assertThatThrownBy(() -> validator.validate(form, answers, true))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            exception -> {
              final var errors = requireNonNull(exception.fieldErrors());
              assertThat(errors)
                  .hasSize(3)
                  .containsEntry(field(text), EnrollmentAnswerMessages.REQUIRED)
                  .containsEntry(field(choices), EnrollmentAnswerMessages.REQUIRED)
                  .containsEntry(field(number), EnrollmentAnswerMessages.REQUIRED);
            });
    assertThatThrownBy(() -> validator.validate(form, Map.of(), true))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  void optionalNullValuesBecomeUnansweredInsteadOfFalse() {
    final var bool = question(1, EnrollmentFormQuestionType.BOOLEAN, false);
    final var answers = new LinkedHashMap<String, @Nullable Object>();
    answers.put(key(bool), null);

    assertThat(validator.validate(definition(bool), answers, true)).isEmpty();
    assertThat(validator.validate(definition(bool), Map.of(key(bool), false), true))
        .containsEntry(key(bool), false);
  }

  @Test
  void rejectsUnknownQuestionsEvenWhenTheirValuesAreNull() {
    final var question = question(1, EnrollmentFormQuestionType.SHORT_TEXT, false);
    final var answers = new LinkedHashMap<String, @Nullable Object>();
    answers.put(id(999).toString(), null);

    assertThatThrownBy(() -> validator.validate(definition(question), answers, false))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            exception ->
                assertThat(exception.fieldErrors())
                    .containsEntry("customAnswers", EnrollmentAnswerMessages.UNKNOWN_QUESTION));
    assertThatThrownBy(
            () -> validator.validate(definition(question), Map.of("1-1-1-1-1", "texto"), false))
        .isInstanceOf(EnrollmentValidationException.class);
  }

  @Test
  void usesOnlyTheSuppliedVersionForQuestionsAndOptions() {
    final var oldQuestion = question(1, EnrollmentFormQuestionType.SINGLE_CHOICE, true);
    final var newQuestion =
        new Question(
            id(2),
            "Otra",
            null,
            EnrollmentFormQuestionType.SINGLE_CHOICE,
            false,
            List.of(new Option(id(201), "Nueva opción")));

    invalid(oldQuestion, id(201).toString(), EnrollmentAnswerMessages.OPTION_INVALID);
    assertThatThrownBy(
            () ->
                validator.validate(
                    definition(oldQuestion), Map.of(key(newQuestion), id(201).toString()), false))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(
            validator.validate(
                definition(oldQuestion), Map.of(key(oldQuestion), id(101).toString()), true))
        .containsEntry(key(oldQuestion), id(101).toString());
  }

  @Test
  void choicesUseOptionIdsAndCannotRepeatThem() {
    invalid(
        question(1, EnrollmentFormQuestionType.SINGLE_CHOICE, false),
        "Opción 1",
        EnrollmentAnswerMessages.OPTION_INVALID);
    final var multiple = question(1, EnrollmentFormQuestionType.MULTIPLE_CHOICE, false);
    invalid(
        multiple,
        List.of(id(101).toString(), id(101).toString()),
        EnrollmentAnswerMessages.OPTION_INVALID);
    invalid(multiple, List.of(id(999).toString()), EnrollmentAnswerMessages.OPTION_INVALID);
  }

  @Test
  void returnsImmutableSnapshotsWithoutAliasingInputMapsOrLists() {
    final var question = question(1, EnrollmentFormQuestionType.MULTIPLE_CHOICE, false);
    final var selected = new ArrayList<>(List.of(id(101).toString()));
    final var input = new LinkedHashMap<String, Object>();
    input.put(key(question), selected);
    final var result = validator.validate(definition(question), input, false);

    input.clear();
    selected.clear();

    assertThat(result).containsEntry(key(question), List.of(id(101).toString()));
    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    final var stored = (List<?>) requireNonNull(result.get(key(question)));
    assertThatThrownBy(stored::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void baseFormRejectsCustomAnswersAndOversizedPayloadsAreRejectedBeforeInspection() {
    assertThat(validator.validate(null, Map.of(), true)).isEmpty();
    assertThatThrownBy(() -> validator.validate(null, Map.of(id(1).toString(), false), false))
        .isInstanceOf(EnrollmentValidationException.class);
    final var input = new LinkedHashMap<String, Object>();
    for (int i = 0; i <= 200; i++) {
      input.put(id(i).toString(), "dato privado");
    }
    assertThatThrownBy(() -> validator.validate(null, input, false))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            exception -> {
              assertThat(exception.fieldErrors())
                  .containsEntry("customAnswers", EnrollmentAnswerMessages.TOO_MANY);
              assertThat(exception.getMessage()).doesNotContain("dato privado");
            });
  }

  private void invalid(final Question question, final Object value, final String message) {
    assertThatThrownBy(
            () -> validator.validate(definition(question), Map.of(key(question), value), false))
        .isInstanceOfSatisfying(
            EnrollmentValidationException.class,
            exception ->
                assertThat(exception.fieldErrors()).containsEntry(field(question), message));
  }

  private static Question question(
      final int index, final EnrollmentFormQuestionType type, final boolean required) {
    final var options =
        type == EnrollmentFormQuestionType.SINGLE_CHOICE
                || type == EnrollmentFormQuestionType.MULTIPLE_CHOICE
            ? List.of(
                new Option(id(index * 100 + 1), "Opción 1"),
                new Option(id(index * 100 + 2), "Opción 2"))
            : List.<Option>of();
    return new Question(id(index), "Pregunta " + index, null, type, required, options);
  }

  private static EnrollmentFormDefinition definition(final Question... questions) {
    return new EnrollmentFormDefinition(
        List.of(new Section(id(10000), "Preguntas", null, List.of(questions))), List.of());
  }

  private static String key(final Question question) {
    return question.id().toString();
  }

  private static String field(final Question question) {
    return "customAnswers." + key(question);
  }

  private static UUID id(final int value) {
    return new UUID(0, value);
  }
}
