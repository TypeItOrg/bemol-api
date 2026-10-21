package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPathDocumentRequirement;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class DocumentAuditDetails {
  private DocumentAuditDetails() {}

  static Map<String, Object> snapshot(
      final DocumentDefinition document, final List<TrainingPathDocumentRequirement> assignments) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("name", document.getName());
    values.put("institutionId", document.getInstitution().getId());
    values.put("institutionName", document.getInstitution().getName());
    values.put("instructions", document.getInstructions());
    values.put("allowedFormats", document.getAllowedFormats());
    values.put("active", document.isActive());
    values.put("requirementsCount", assignments.size());
    values.put(
        "requirements",
        assignments.stream()
            .sorted(Comparator.comparing(value -> value.getTrainingPath().getId()))
            .map(DocumentAuditDetails::requirement)
            .toList());
    return values;
  }

  private static Map<String, Object> requirement(final TrainingPathDocumentRequirement assignment) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("trainingPathId", assignment.getTrainingPath().getId());
    values.put("trainingPathName", assignment.getTrainingPath().getName());
    values.put("level", assignment.getLevel().name());
    values.put("displayOrder", assignment.getDisplayOrder());
    values.put("active", assignment.isActive());
    values.put("specificInstructions", assignment.getSpecificInstructions());
    return values;
  }

  static boolean definitionChanged(
      final Map<String, Object> before, final Map<String, Object> after) {
    return List.of("name", "instructions", "allowedFormats", "active").stream()
        .anyMatch(field -> !Objects.equals(before.get(field), after.get(field)));
  }

  static Map<String, Object> changed(
      final String entityName, final Map<String, Object> before, final Map<String, Object> after) {
    final var fields =
        after.keySet().stream()
            .filter(key -> !Objects.equals(before.get(key), after.get(key)))
            .toList();
    return Map.of(
        "entityName",
        entityName,
        "changedFields",
        fields,
        "before",
        publicValues(before),
        "after",
        publicValues(after));
  }

  // Free-form instructions are only compared, not copied into the audit log.
  private static Map<String, Object> publicValues(final Map<String, Object> snapshot) {
    final var values = new LinkedHashMap<>(snapshot);
    values.remove("institutionId");
    values.remove("instructions");
    if (values.get("requirements") instanceof List<?> requirements) {
      values.put(
          "requirements",
          requirements.stream()
              .map(
                  value -> {
                    final var requirement = new LinkedHashMap<>((Map<?, ?>) value);
                    requirement.remove("trainingPathId");
                    requirement.remove("specificInstructions");
                    return requirement;
                  })
              .toList());
    }
    return values;
  }

  static void recordChange(
      final AuditEventRecorder auditEventRecorder,
      final DocumentDefinition document,
      final AuditAction action,
      final Map<String, Object> before,
      final Map<String, Object> after) {
    if (before.equals(after)) {
      return;
    }
    auditEventRecorder.recordCurrentActor(
        document.getInstitution(),
        null,
        action,
        AuditEntityType.DOCUMENT_DEFINITION,
        document.getId(),
        AuditOrigin.administration(),
        DocumentAuditDetails.changed(document.getName(), before, after));
  }
}
