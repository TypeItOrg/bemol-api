package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EnrollmentApplicationAuditSnapshot {
  private EnrollmentApplicationAuditSnapshot() {}

  static AuditSnapshot.Snapshot of(final EnrollmentApplication application) {
    return AuditSnapshot.fields()
        .compare("trainingPathId", "Trayecto formativo", application.getTrainingPathId())
        .compare("customAnswers", "Respuestas del formulario", answers(application))
        .compare(
            "academicBackground",
            "Escolaridad",
            application.getEducationBackground() == null
                ? null
                : application.getEducationBackground().auditSnapshot().values())
        .compare(
            "healthInclusion",
            "Inclusión",
            application.getHealthInclusion() == null
                ? null
                : application.getHealthInclusion().auditSnapshot().values())
        .compare(
            "responsible",
            "Responsable",
            application.getResponsible() == null
                ? null
                : application.getResponsible().auditSnapshot().values())
        .compare(
            "preference",
            "Preferencias",
            application.getPreference() == null
                ? null
                : application.getPreference().auditSnapshot().values())
        .compare("courseSelections", "Cursos seleccionados", courseSelections(application))
        .build();
  }

  static AuditSnapshot.Snapshot admission(final EnrollmentApplication application) {
    final String label =
        switch (application.getStatus()) {
          case DRAFT -> "Borrador";
          case SUBMITTED -> "Enviada";
          case CANCELLED -> "Cancelada";
          case PROVISIONALLY_APPROVED -> "Aprobada provisoriamente";
          case APPROVED -> "Aprobada";
          case REJECTED -> "Rechazada";
        };
    final String status = application.getStatus().name();
    return AuditSnapshot.fields()
        .expose("status", "Estado de solicitud", status, Map.of("code", status, "label", label))
        .build();
  }

  private static Map<String, Object> answers(final EnrollmentApplication application) {
    final var answers = new LinkedHashMap<String, Object>();
    application
        .getFormState()
        .getCustomAnswers()
        .forEach(
            (id, value) ->
                answers.put(
                    id,
                    value instanceof Number number
                        ? new BigDecimal(number.toString()).stripTrailingZeros()
                        : value));
    return answers;
  }

  private static List<String> courseSelections(final EnrollmentApplication application) {
    return application.getCourseSelections().stream()
        .map(
            selection ->
                selection.getCourse().getId()
                    + "/"
                    + (selection.getPreferredTeacher() == null
                        ? ""
                        : selection.getPreferredTeacher().getId()))
        .sorted()
        .toList();
  }
}
