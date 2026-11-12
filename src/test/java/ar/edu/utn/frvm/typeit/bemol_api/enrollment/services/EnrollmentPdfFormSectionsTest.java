package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormBaseDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.PdfSection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnrollmentPdfFormSectionsTest {
  private static final UUID OPTION_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OPTION_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @ParameterizedTest
  @MethodSource("values")
  void formatsTypesWithoutConfusingMissingAndNegativeAnswers(
      final EnrollmentFormQuestionType type, final Object value, final String expected) {
    assertThat(EnrollmentPdfAnswerFormatter.format(question(type), value)).isEqualTo(expected);
  }

  @Test
  void preservesExplicitQuestionOrderAndAddsOmittedProtectedSections() {
    final var first = question(EnrollmentFormQuestionType.SHORT_TEXT);
    final var second = question(EnrollmentFormQuestionType.BOOLEAN);
    final var section = new Section(UUID.randomUUID(), "Experiencia", null, List.of(first, second));
    final var sections = new ArrayList<>(EnrollmentFormBaseDefinition.create().sections());
    sections.add(section);
    final var definition =
        new EnrollmentFormDefinition(
            sections, List.of(new PdfSection(section.id(), List.of(second.id(), first.id()))));

    final var result =
        EnrollmentPdfFormSections.from(
            definition,
            nativeSections(),
            Map.of(first.id().toString(), "Respuesta original", second.id().toString(), false));

    assertThat(result)
        .extracting(printed -> printed.title())
        .containsExactly("Datos personales", "Responsable", "Inscripción", "Experiencia");
    assertThat(result.getLast().fields())
        .extracting(field -> field.label())
        .containsExactly(second.label(), first.label());
    assertThat(result.getLast().fields())
        .extracting(field -> field.value())
        .containsExactly("No", "Respuesta original");
    assertThat(result)
        .extracting(printed -> printed.allowPageBreaks())
        .containsExactly(false, false, false, true);
  }

  @Test
  void anEmptyPdfPlanUsesNaturalOrderAndAnEmptyQuestionListMeansAll() {
    final var question = question(EnrollmentFormQuestionType.NUMBER);
    final var section = new Section(UUID.randomUUID(), "Cantidad", null, List.of(question));
    final var sections = new ArrayList<>(EnrollmentFormBaseDefinition.create().sections());
    sections.removeIf(
        value ->
            value.nativeBlock() == EnrollmentFormNativeBlock.SCHOOLING
                || value.nativeBlock() == EnrollmentFormNativeBlock.PREFERENCES
                || value.nativeBlock() == EnrollmentFormNativeBlock.HEALTH_INCLUSION);
    sections.add(0, section);
    final var definition = new EnrollmentFormDefinition(sections, List.of());

    final var result =
        EnrollmentPdfFormSections.from(
            definition, nativeSections(), Map.of(question.id().toString(), 0));

    assertThat(result)
        .extracting(printed -> printed.title())
        .containsExactly("Cantidad", "Datos personales", "Responsable", "Inscripción");
    assertThat(result.getFirst().fields())
        .singleElement()
        .extracting(field -> field.value())
        .isEqualTo("0");
  }

  @Test
  void standaloneShiftDoesNotPrintUnaskedHistoricalPreferenceValues() {
    final var sections = new ArrayList<>(EnrollmentFormBaseDefinition.create().sections());
    sections.removeIf(value -> value.nativeBlock() == EnrollmentFormNativeBlock.PREFERENCES);
    sections.add(
        new Section(
            UUID.randomUUID(),
            "Disponibilidad horaria",
            EnrollmentFormNativeBlock.PREFERRED_SHIFT,
            List.of()));
    final var definition = new EnrollmentFormDefinition(sections, List.of());

    final var result = EnrollmentPdfFormSections.from(definition, nativeSections(), Map.of());

    assertThat(
            result.stream().flatMap(value -> value.fields().stream()).map(field -> field.label()))
        .contains("Turno preferido")
        .doesNotContain("Autoriza imágenes");
  }

  private static Stream<Arguments> values() {
    return Stream.of(
        Arguments.of(EnrollmentFormQuestionType.SHORT_TEXT, "<b>Texto</b>", "<b>Texto</b>"),
        Arguments.of(
            EnrollmentFormQuestionType.LONG_TEXT, "Texto\nmultilínea", "Texto\nmultilínea"),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, new BigDecimal("0.000"), "0"),
        Arguments.of(EnrollmentFormQuestionType.NUMBER, new BigDecimal("1234.50"), "1234.5"),
        Arguments.of(EnrollmentFormQuestionType.BOOLEAN, false, "No"),
        Arguments.of(EnrollmentFormQuestionType.BOOLEAN, true, "Sí"),
        Arguments.of(EnrollmentFormQuestionType.DATE, "2024-02-29", "29/02/2024"),
        Arguments.of(
            EnrollmentFormQuestionType.SINGLE_CHOICE, OPTION_B.toString(), "Segunda original"),
        Arguments.of(
            EnrollmentFormQuestionType.MULTIPLE_CHOICE,
            List.of(OPTION_B.toString(), OPTION_A.toString()),
            "Segunda original, Primera original"),
        Arguments.of(EnrollmentFormQuestionType.LONG_TEXT, " ", "Sin informar"),
        Arguments.of(EnrollmentFormQuestionType.MULTIPLE_CHOICE, List.of(), "Sin informar"));
  }

  private static Question question(final EnrollmentFormQuestionType type) {
    return new Question(
        UUID.randomUUID(),
        "Pregunta " + type,
        null,
        type,
        false,
        type == EnrollmentFormQuestionType.SINGLE_CHOICE
                || type == EnrollmentFormQuestionType.MULTIPLE_CHOICE
            ? List.of(
                new Option(OPTION_A, "Primera original"), new Option(OPTION_B, "Segunda original"))
            : List.of());
  }

  private Map<EnrollmentFormNativeBlock, EnrollmentPdfData.Section> nativeSections() {
    return Map.of(
        EnrollmentFormNativeBlock.PERSONAL_DATA,
            new EnrollmentPdfData.Section(
                "Estudiante", List.of(new EnrollmentPdfData.Field("Nombre", "Persona"))),
        EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
            new EnrollmentPdfData.Section(
                "Inscripción", List.of(new EnrollmentPdfData.Field("Ciclo", "2026"))),
        EnrollmentFormNativeBlock.RESPONSIBLE,
            new EnrollmentPdfData.Section(
                "Responsable", List.of(new EnrollmentPdfData.Field("Nombre", "Responsable"))),
        EnrollmentFormNativeBlock.SCHOOLING,
            new EnrollmentPdfData.Section(
                "Escolaridad histórica", List.of(new EnrollmentPdfData.Field("Nivel", "Primario"))),
        EnrollmentFormNativeBlock.PREFERENCES,
            new EnrollmentPdfData.Section(
                "Preferencias históricas",
                List.of(new EnrollmentPdfData.Field("Autoriza imágenes", "No"))),
        EnrollmentFormNativeBlock.PREFERRED_SHIFT,
            new EnrollmentPdfData.Section(
                "Turno", List.of(new EnrollmentPdfData.Field("Turno preferido", "Tarde"))));
  }
}
