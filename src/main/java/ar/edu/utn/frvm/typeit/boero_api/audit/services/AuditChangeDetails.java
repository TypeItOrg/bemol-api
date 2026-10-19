package ar.edu.utn.frvm.typeit.boero_api.audit.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Only catalog/state metadata is persisted; private values only participate in change detection.
 */
public final class AuditChangeDetails {
  private static final Set<String> VISIBLE_FIELDS =
      Set.of(
          "name",
          "active",
          "deleted",
          "year",
          "startDate",
          "endDate",
          "status",
          "type",
          "format",
          "instrumental",
          "academicYearId",
          "offeringIds",
          "academicStatus",
          "courseId");

  private AuditChangeDetails() {}

  public static Map<String, Object> change(
      final String name, final Map<String, Object> before, final Map<String, Object> after) {
    return Map.of(
        "entityName",
        name,
        "changedFields",
        Stream.concat(before.keySet().stream(), after.keySet().stream())
            .distinct()
            .filter(key -> !Objects.equals(before.get(key), after.get(key)))
            .toList(),
        "before",
        visible(before),
        "after",
        visible(after));
  }

  private static Map<String, Object> visible(final Map<String, Object> values) {
    final Map<String, Object> result = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> {
          if (VISIBLE_FIELDS.contains(key)) {
            result.put(key, value);
          }
        });
    return result;
  }
}
