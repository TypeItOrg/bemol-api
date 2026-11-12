package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.PdfSection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

final class EnrollmentPdfFormSections {
  private static final Set<EnrollmentFormNativeBlock> PROTECTED =
      Set.of(
          EnrollmentFormNativeBlock.PERSONAL_DATA,
          EnrollmentFormNativeBlock.RESPONSIBLE,
          EnrollmentFormNativeBlock.ACADEMIC_SELECTION);

  private EnrollmentPdfFormSections() {}

  static List<Section> from(
      final EnrollmentFormDefinition definition,
      final Map<EnrollmentFormNativeBlock, Section> nativeSections,
      final Map<String, Object> answers) {
    final var source =
        definition.sections().stream()
            .collect(Collectors.toMap(section -> section.id(), Function.identity()));
    final var requested =
        definition.pdfSections().isEmpty()
            ? definition.sections().stream()
                .map(section -> new PdfSection(section.id(), List.of()))
                .toList()
            : definition.pdfSections();
    final var selected =
        requested.stream().map(section -> section.sectionId()).collect(Collectors.toSet());
    final var ordered = new LinkedHashMap<UUID, PdfSection>();
    for (final var section : definition.sections()) {
      if (section.nativeBlock() != null
          && PROTECTED.contains(section.nativeBlock())
          && !selected.contains(section.id())) {
        ordered.put(section.id(), new PdfSection(section.id(), List.of()));
      }
    }
    requested.forEach(section -> ordered.put(section.sectionId(), section));

    // The shift is required when the form asks for it, so it travels with the enrollment section.
    final boolean asksShift =
        definition.sections().stream()
            .anyMatch(
                section ->
                    section.nativeBlock() == EnrollmentFormNativeBlock.PREFERRED_SHIFT
                        || section.nativeBlock() == EnrollmentFormNativeBlock.PREFERENCES);
    final var result = new ArrayList<Section>();
    for (final var export : ordered.values()) {
      final var section = requireNonNull(source.get(export.sectionId()));
      if (section.nativeBlock() != null) {
        final var nativeSection = nativeSections.get(section.nativeBlock());
        if (section.nativeBlock() == EnrollmentFormNativeBlock.ACADEMIC_SELECTION
            && nativeSection != null) {
          result.add(
              asksShift
                  ? EnrollmentPdfNativeSections.withShift(nativeSection, nativeSections)
                  : nativeSection);
        } else if (nativeSection != null
            && section.nativeBlock() != EnrollmentFormNativeBlock.PREFERRED_SHIFT) {
          result.add(new Section(section.title(), nativeSection.fields()));
        }
        continue;
      }
      final var questions =
          section.questions().stream()
              .collect(Collectors.toMap(question -> question.id(), Function.identity()));
      final var ids =
          export.questionIds().isEmpty()
              ? section.questions().stream().map(question -> question.id()).toList()
              : export.questionIds();
      final var fields =
          ids.stream()
              .map(
                  id -> {
                    final var question = requireNonNull(questions.get(id));
                    return new Field(
                        question.label(),
                        EnrollmentPdfAnswerFormatter.format(question, answers.get(id.toString())));
                  })
              .toList();
      result.add(new Section(section.title(), fields, true));
    }
    return List.copyOf(result);
  }
}
