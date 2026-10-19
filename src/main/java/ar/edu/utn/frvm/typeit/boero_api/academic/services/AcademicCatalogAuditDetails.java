package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditChangeDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AcademicCatalogAuditDetails {
  private AcademicCatalogAuditDetails() {}

  static Map<String, Object> snapshot(final Shift value) {
    return named(value.getName(), value.getDescription(), value.isActive());
  }

  static Map<String, Object> snapshot(final Instrument value) {
    return named(value.getName(), value.getDescription(), value.isActive());
  }

  static Map<String, Object> snapshot(final TrainingPath value) {
    return named(value.getName(), value.getDescription(), value.isActive());
  }

  static Map<String, Object> snapshot(final AcademicSpace value) {
    final var result = named(value.getName(), value.getDescription(), value.isActive());
    result.put("type", value.getType().name());
    result.put("format", value.getFormat().name());
    result.put("instrumental", value.isInstrumental());
    return result;
  }

  static Map<String, Object> snapshot(final AcademicYear value) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("year", value.getYear());
    result.put("status", value.getStatus().name());
    result.put("startDate", value.getStartDate() == null ? null : value.getStartDate().toString());
    result.put("endDate", value.getEndDate() == null ? null : value.getEndDate().toString());
    return result;
  }

  static Map<String, Object> lifecycle(final String name, final boolean deleted) {
    return AuditChangeDetails.change(name, Map.of("deleted", !deleted), Map.of("deleted", deleted));
  }

  private static Map<String, Object> named(
      final String name, final @Nullable String description, final boolean active) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);
    result.put("description", description);
    result.put("active", active);
    return result;
  }
}
