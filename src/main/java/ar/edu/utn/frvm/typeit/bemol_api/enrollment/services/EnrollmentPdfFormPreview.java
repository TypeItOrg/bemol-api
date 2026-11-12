package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Selection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EnrollmentPdfFormPreview {
  private EnrollmentPdfFormPreview() {}

  static EnrollmentPdfData from(
      final EnrollmentFormDefinition definition,
      final Institution institution,
      final EnrollmentPdfSettingsResponse settings,
      final Instant now) {
    final var date = LocalDate.ofInstant(now, ZoneOffset.UTC);
    final var nativeSections =
        Map.of(
            EnrollmentFormNativeBlock.PERSONAL_DATA,
                new Section(
                    "Estudiante",
                    List.of(
                        new Field("Apellido y nombre", "Persona de ejemplo"),
                        new Field("Documento nacional de identidad", "Dato ficticio"),
                        new Field("Nacionalidad", "Sin informar"))),
            EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
                new Section(
                    "Inscripción",
                    List.of(
                        new Field("Propuesta académica", "Propuesta de ejemplo"),
                        new Field("Ciclo lectivo", Integer.toString(date.getYear())))),
            EnrollmentFormNativeBlock.RESPONSIBLE,
                new Section(
                    "Responsable",
                    List.of(
                        new Field("Apellido y nombre", "Responsable de ejemplo"),
                        new Field("Documento nacional de identidad", "Dato ficticio"))),
            EnrollmentFormNativeBlock.SCHOOLING,
                new Section(
                    "Escolaridad",
                    List.of(
                        new Field("Asiste actualmente", "Sí"),
                        new Field("Institución educativa", "Institución de ejemplo"))),
            EnrollmentFormNativeBlock.HEALTH_INCLUSION,
                new Section("Inclusión", List.of(new Field("Recibe ajustes razonables", "No"))),
            EnrollmentFormNativeBlock.PREFERENCES,
                new Section(
                    "Preferencias",
                    List.of(
                        new Field("Reinscripción", "No"),
                        new Field("Autoriza el uso de imágenes", "No"))),
            EnrollmentFormNativeBlock.PREFERRED_SHIFT,
                new Section("Turno preferido", List.of(new Field("Turno preferido", "Mañana"))));
    final var answers = new HashMap<String, Object>();
    for (final var section : definition.sections()) {
      for (final var question : section.questions()) {
        final Object value =
            switch (question.type()) {
              case SHORT_TEXT -> "Respuesta de ejemplo";
              case LONG_TEXT ->
                  "Respuesta ficticia de ejemplo para comprobar la distribución del texto en la planilla.";
              case NUMBER -> 0;
              case DATE -> date.toString();
              case BOOLEAN -> false;
              case SINGLE_CHOICE -> question.options().getFirst().id().toString();
              case MULTIPLE_CHOICE ->
                  question.options().stream()
                      .limit(2)
                      .map(option -> option.id().toString())
                      .toList();
            };
        answers.put(question.id().toString(), value);
      }
    }
    return EnrollmentPdfData.builder()
        .institution(EnrollmentPdfDataMapper.institution(institution))
        .generatedAt(now)
        .settings(settings)
        .hasResponsible(true)
        .coursesSelected(false)
        .sections(EnrollmentPdfFormSections.from(definition, nativeSections, answers))
        .selections(
            List.of(
                new Selection(
                    "Espacio académico de ejemplo",
                    "Nivel 1",
                    "Sin informar",
                    "Plan de ejemplo",
                    "Solicitada")))
        .build();
  }
}
