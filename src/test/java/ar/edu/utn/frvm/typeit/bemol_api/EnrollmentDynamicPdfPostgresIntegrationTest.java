package ar.edu.utn.frvm.typeit.bemol_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Option;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Question;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormDefinitionUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormRevisionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormVersionCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ExtractEnrollmentPdfDataUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentDynamicPdfPostgresIntegrationTest extends EnrollmentDynamicFormTestSupport {
  @Autowired private ExtractEnrollmentPdfDataUseCase pdf;

  @Test
  void exportUsesArchivedPinnedLabelsAndAnswersWithoutReadingDocumentMetadata() {
    final var fixture = fixture();
    final var saved = save(fixture, 0, completeAnswers(), true);
    applications.submitApplication(fixture.person(), fixture.application(), saved.draftRevision());
    final var draft =
        versions.createDraft(
            fixture.institution(),
            fixture.form(),
            new EnrollmentFormVersionCreateRequest(fixture.version()));
    final var changed =
        draft.definition().sections().stream()
            .map(
                section ->
                    new Section(
                        section.id(),
                        section.title(),
                        section.nativeBlock(),
                        section.questions().stream()
                            .map(
                                question ->
                                    new Question(
                                        question.id(),
                                        "V2 " + question.label(),
                                        question.help(),
                                        question.type(),
                                        question.required(),
                                        question.options().stream()
                                            .map(
                                                option ->
                                                    new Option(option.id(), "V2 " + option.label()))
                                            .toList()))
                            .toList()))
            .toList();
    final var edited =
        versions.replace(
            fixture.institution(),
            fixture.form(),
            draft.id(),
            new EnrollmentFormDefinitionUpdateRequest(
                new EnrollmentFormDefinition(changed, List.of()), draft.revision()));
    final var published =
        versions.publish(
            fixture.institution(),
            fixture.form(),
            edited.id(),
            new EnrollmentFormRevisionRequest(edited.revision()));
    assignments.replace(
        fixture.institution(),
        fixture.assignment(),
        new EnrollmentFormAssignmentUpdateRequest(published.id(), 0L));
    final var first = versions.get(fixture.institution(), fixture.form(), fixture.version());
    versions.archive(
        fixture.institution(),
        fixture.form(),
        first.id(),
        new EnrollmentFormRevisionRequest(first.revision()));
    final var documentReads =
        count(
            "SELECT count(*) FROM enrollment_document_audit WHERE application_id=?",
            fixture.application());

    final var result = pdf.extract(fixture.institution(), fixture.application(), fixture.person());

    final var fields =
        result.sections().stream().flatMap(section -> section.fields().stream()).toList();
    assertThat(fields)
        .extracting(field -> field.value())
        .contains("Respuesta privada breve", "No", "0", "29/02/2024", "Primera", "Lunes, Martes");
    assertThat(fields)
        .extracting(field -> field.label())
        .noneMatch(label -> label.startsWith("V2 "));
    assertThat(result.sections())
        .extracting(section -> section.title())
        .doesNotContain("Escolaridad", "Preferencias", "Salud e inclusión", "Documentación");
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_document_audit WHERE application_id=?",
                fixture.application()))
        .isEqualTo(documentReads);
  }

  @Test
  void formPreviewUsesOnlyFictitiousAnswersAndDoesNotPersist() {
    final var fixture = fixture();
    save(fixture, 0, completeAnswers(), false);
    final var beforeForms =
        count(
            "SELECT count(*) FROM enrollment_forms WHERE institution_id=?", fixture.institution());
    final var beforeApplications =
        count(
            "SELECT count(*) FROM enrollment_applications WHERE institution_id=?",
            fixture.institution());
    final var beforeAudit =
        count("SELECT count(*) FROM audit_events WHERE institution_id=?", fixture.institution());

    final var result =
        pdf.formExample(fixture.institution(), requireNonNull(read(fixture).formDefinition()));

    assertThat(
            result.sections().stream()
                .flatMap(section -> section.fields().stream())
                .map(field -> field.value()))
        .contains("Persona de ejemplo", "No", "0", "Primera")
        .doesNotContain("Respuesta privada breve", "Detalle privado\ncon otra línea");
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_forms WHERE institution_id=?",
                fixture.institution()))
        .isEqualTo(beforeForms);
    assertThat(
            count(
                "SELECT count(*) FROM enrollment_applications WHERE institution_id=?",
                fixture.institution()))
        .isEqualTo(beforeApplications);
    assertThat(
            count(
                "SELECT count(*) FROM audit_events WHERE institution_id=?", fixture.institution()))
        .isEqualTo(beforeAudit);
  }
}
