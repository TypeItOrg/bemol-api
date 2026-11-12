package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormQuestionType;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(requiredProperties = {"sections", "pdfSections"})
public record EnrollmentFormDefinition(List<Section> sections, List<PdfSection> pdfSections) {
  private static final int MAX_SECTIONS = 40;
  private static final int MAX_QUESTIONS = 200;
  private static final int MAX_OPTIONS = 100;
  private static final Set<EnrollmentFormNativeBlock> ESSENTIAL_BLOCKS =
      Set.of(
          EnrollmentFormNativeBlock.PERSONAL_DATA,
          EnrollmentFormNativeBlock.RESPONSIBLE,
          EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
          EnrollmentFormNativeBlock.DOCUMENTATION);

  public EnrollmentFormDefinition {
    sections = immutable(sections);
    pdfSections = immutable(pdfSections);
    require(sections.size() <= MAX_SECTIONS && pdfSections.size() <= MAX_SECTIONS);

    final var ids = new HashSet<UUID>();
    final var nativeBlocks = EnumSet.noneOf(EnrollmentFormNativeBlock.class);
    final var sectionsById = new HashMap<UUID, Section>();
    int questionCount = 0;
    for (final var section : sections) {
      require(ids.add(section.id()));
      sectionsById.put(section.id(), section);
      if (section.nativeBlock() != null) {
        require(nativeBlocks.add(section.nativeBlock()));
      }
      questionCount += section.questions().size();
      require(questionCount <= MAX_QUESTIONS);
      for (final var question : section.questions()) {
        require(ids.add(question.id()));
        for (final var option : question.options()) {
          require(ids.add(option.id()));
        }
      }
    }
    require(
        !nativeBlocks.contains(EnrollmentFormNativeBlock.PREFERENCES)
            || !nativeBlocks.contains(EnrollmentFormNativeBlock.PREFERRED_SHIFT));

    final var printedSections = new HashSet<UUID>();
    for (final var printed : pdfSections) {
      final var section = sectionsById.get(printed.sectionId());
      if (section == null || !printedSections.add(printed.sectionId())) {
        throw invalid();
      }
      final var available = new HashSet<UUID>();
      for (final var question : section.questions()) {
        available.add(question.id());
      }
      require(available.containsAll(printed.questionIds()));
      require(new HashSet<>(printed.questionIds()).size() == printed.questionIds().size());
    }
  }

  public void validateForPublication() {
    final var nativeBlocks = EnumSet.noneOf(EnrollmentFormNativeBlock.class);
    for (final var section : sections) {
      if (section.nativeBlock() != null) {
        nativeBlocks.add(section.nativeBlock());
      } else {
        require(!section.questions().isEmpty());
      }
    }
    if (!nativeBlocks.containsAll(ESSENTIAL_BLOCKS)) {
      throw new EnrollmentFormException(
          ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.ESSENTIAL_BLOCKS_REQUIRED);
    }
  }

  @Schema(requiredProperties = {"id", "title", "nativeBlock", "questions"})
  public record Section(
      UUID id,
      String title,
      @Schema(nullable = true) @Nullable EnrollmentFormNativeBlock nativeBlock,
      List<Question> questions,
      @Schema(nullable = true) @Nullable EnrollmentFormNativeBlock replacedNativeBlock) {
    public Section(
        final UUID id,
        final String title,
        final @Nullable EnrollmentFormNativeBlock nativeBlock,
        final List<Question> questions) {
      this(id, title, nativeBlock, questions, null);
    }

    public Section {
      require(id != null);
      requireText(title, 150);
      questions = immutable(questions);
      require(questions.size() <= MAX_QUESTIONS);
      require(nativeBlock == null || questions.isEmpty());
      require(nativeBlock == null || replacedNativeBlock == null);
    }
  }

  @Schema(requiredProperties = {"id", "label", "help", "type", "required", "options"})
  public record Question(
      UUID id,
      String label,
      @Schema(nullable = true) @Nullable String help,
      EnrollmentFormQuestionType type,
      boolean required,
      List<Option> options) {
    public Question {
      require(id != null && type != null);
      requireText(label, 500);
      require(help == null || help.length() <= 1000 && validText(help));
      options = immutable(options);
      require(options.size() <= MAX_OPTIONS);
      final boolean choice =
          type == EnrollmentFormQuestionType.SINGLE_CHOICE
              || type == EnrollmentFormQuestionType.MULTIPLE_CHOICE;
      require(choice ? !options.isEmpty() : options.isEmpty());
    }
  }

  @Schema(requiredProperties = {"id", "label"})
  public record Option(UUID id, String label) {
    public Option {
      require(id != null);
      requireText(label, 150);
    }
  }

  @Schema(requiredProperties = {"sectionId", "questionIds"})
  public record PdfSection(UUID sectionId, List<UUID> questionIds) {
    public PdfSection {
      require(sectionId != null);
      questionIds = immutable(questionIds);
      require(questionIds.size() <= MAX_QUESTIONS);
    }
  }

  private static <T> List<T> immutable(final List<T> values) {
    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw invalid();
    }
    return List.copyOf(values);
  }

  private static void requireText(final String value, final int maxLength) {
    require(value != null && !value.isBlank() && value.length() <= maxLength && validText(value));
  }

  private static boolean validText(final String value) {
    return value
        .codePoints()
        .noneMatch(
            point ->
                point == 0 || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE);
  }

  private static void require(final boolean valid) {
    if (!valid) {
      throw invalid();
    }
  }

  private static EnrollmentFormException invalid() {
    return new EnrollmentFormException(
        ErrorCategory.INVALID_INPUT, EnrollmentFormMessages.DEFINITION_INVALID);
  }
}
