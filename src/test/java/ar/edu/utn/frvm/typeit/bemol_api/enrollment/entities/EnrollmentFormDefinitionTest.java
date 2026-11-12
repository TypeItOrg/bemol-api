package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.PdfSection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentFormDefinitionTest {
  @Test
  void rejectsTextThatCannotBeStoredOrPreviewedAsJsonb() {
    for (final var invalid : List.of("Pregunta\u0000", "Pregunta\uD800", "Pregunta\uDC00")) {
      assertThatThrownBy(() -> new Section(id(10), invalid, null, List.of()))
          .isInstanceOf(EnrollmentFormException.class);
      assertThatThrownBy(() -> new Option(id(11), invalid))
          .isInstanceOf(EnrollmentFormException.class);
      assertThatThrownBy(
              () ->
                  new Question(
                      id(12),
                      "Pregunta",
                      invalid,
                      EnrollmentFormQuestionType.SHORT_TEXT,
                      false,
                      List.of()))
          .isInstanceOf(EnrollmentFormException.class);
    }
  }

  @Test
  void requiresEssentialBlocksOnlyWhenPublishing() {
    final var definition = new EnrollmentFormDefinition(List.of(), List.of());

    assertThatThrownBy(definition::validateForPublication)
        .isInstanceOf(EnrollmentFormException.class)
        .hasMessage(EnrollmentFormMessages.ESSENTIAL_BLOCKS_REQUIRED);
    assertThatCode(baseDefinition()::validateForPublication).doesNotThrowAnyException();
  }

  @Test
  void copiesEveryCollectionBeforeItCanBePublished() {
    final var options = new ArrayList<>(List.of(new Option(id(12), "Propio")));
    final var question =
        new Question(
            id(11), "Instrumento", null, EnrollmentFormQuestionType.SINGLE_CHOICE, true, options);
    final var questions = new ArrayList<>(List.of(question));
    final var section = new Section(id(10), "Experiencia", null, questions);
    final var sections = new ArrayList<>(baseDefinition().sections());
    sections.add(section);
    final var questionIds = new ArrayList<>(List.of(id(11)));
    final var pdfSections = new ArrayList<>(List.of(new PdfSection(id(10), questionIds)));
    final var definition = new EnrollmentFormDefinition(sections, pdfSections);

    options.clear();
    questions.clear();
    sections.clear();
    questionIds.clear();
    pdfSections.clear();

    assertThat(definition.sections()).hasSize(5);
    assertThat(definition.sections().getLast().questions()).containsExactly(question);
    assertThat(question.options()).containsExactly(new Option(id(12), "Propio"));
    assertThat(definition.pdfSections().getFirst().questionIds()).containsExactly(id(11));
    assertThatThrownBy(() -> definition.sections().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> question.options().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsDuplicateIdentifiersAndNativeBlocks() {
    final var repeatedId = new Section(id(1), "Otro", null, List.of());
    final var repeatedBlock =
        new Section(id(9), "Otro", EnrollmentFormNativeBlock.PERSONAL_DATA, List.of());

    assertThatThrownBy(() -> withSection(repeatedId)).isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(() -> withSection(repeatedBlock))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void rejectsOptionsForTextAndMissingOptionsForChoices() {
    assertThatThrownBy(
            () ->
                new Question(
                    id(10),
                    "Experiencia",
                    null,
                    EnrollmentFormQuestionType.SHORT_TEXT,
                    false,
                    List.of(new Option(id(11), "Uno"))))
        .isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(
            () ->
                new Question(
                    id(10),
                    "Instrumento",
                    null,
                    EnrollmentFormQuestionType.MULTIPLE_CHOICE,
                    false,
                    List.of()))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void rejectsCustomQuestionsInsideNativeBlock() {
    assertThatThrownBy(
            () ->
                new Section(
                    id(10),
                    "Escolaridad",
                    EnrollmentFormNativeBlock.SCHOOLING,
                    List.of(textQuestion(id(11)))))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void allowsReplacingOptionalSchoolingWithoutDuplicatingPreferredShift() {
    assertThatCode(
            () ->
                withSection(
                        new Section(id(10), "Antecedentes", null, List.of(textQuestion(id(11)))))
                    .validateForPublication())
        .doesNotThrowAnyException();
    final var sections = new ArrayList<>(baseDefinition().sections());
    sections.add(
        new Section(id(12), "Preferencias", EnrollmentFormNativeBlock.PREFERENCES, List.of()));
    sections.add(
        new Section(id(13), "Turno", EnrollmentFormNativeBlock.PREFERRED_SHIFT, List.of()));

    assertThatThrownBy(() -> new EnrollmentFormDefinition(sections, List.of()))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void pdfReferencesMustBelongToTheirDeclaredSection() {
    final var definition =
        withSection(new Section(id(10), "Antecedentes", null, List.of(textQuestion(id(11)))));

    assertThatThrownBy(
            () ->
                new EnrollmentFormDefinition(
                    definition.sections(), List.of(new PdfSection(id(1), List.of(id(11))))))
        .isInstanceOf(EnrollmentFormException.class);
    assertThatThrownBy(
            () ->
                new EnrollmentFormDefinition(
                    definition.sections(), List.of(new PdfSection(id(99), List.of()))))
        .isInstanceOf(EnrollmentFormException.class);
  }

  @Test
  void rejectsOversizedTextsAndEmptyCustomSectionAtPublication() {
    assertThatThrownBy(() -> new Section(id(10), "x".repeat(151), null, List.of()))
        .isInstanceOf(EnrollmentFormException.class);
    final var definition = withSection(new Section(id(10), "Vacía", null, List.of()));

    assertThatThrownBy(definition::validateForPublication)
        .isInstanceOf(EnrollmentFormException.class);
  }

  static EnrollmentFormDefinition baseDefinition() {
    return new EnrollmentFormDefinition(
        List.of(
            new Section(id(1), "Persona", EnrollmentFormNativeBlock.PERSONAL_DATA, List.of()),
            new Section(id(2), "Responsable", EnrollmentFormNativeBlock.RESPONSIBLE, List.of()),
            new Section(id(3), "Cursos", EnrollmentFormNativeBlock.ACADEMIC_SELECTION, List.of()),
            new Section(
                id(4), "Documentación", EnrollmentFormNativeBlock.DOCUMENTATION, List.of())),
        List.of());
  }

  private EnrollmentFormDefinition withSection(final Section section) {
    final var sections = new ArrayList<>(baseDefinition().sections());
    sections.add(section);
    return new EnrollmentFormDefinition(sections, List.of());
  }

  private Question textQuestion(final UUID id) {
    return new Question(
        id, "Experiencia", null, EnrollmentFormQuestionType.LONG_TEXT, false, List.of());
  }

  private static UUID id(final long value) {
    return new UUID(0, value);
  }
}
