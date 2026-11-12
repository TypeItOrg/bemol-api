package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class AuditChangeDetails {
  private AuditChangeDetails() {}

  private static Map<String, Object> change(
      final String name, final Map<String, Object> before, final Map<String, Object> after) {
    return details(name, before, after, changedFields(before, after));
  }

  public static Map<String, Object> change(
      final String name, final AuditSnapshot.Snapshot before, final AuditSnapshot.Snapshot after) {
    final var result =
        details(
            name,
            before.visibleValues(),
            after.visibleValues(),
            changedFields(before.values(), after.values()));
    final var labels = new LinkedHashMap<>(before.labels());
    labels.putAll(after.labels());
    if (!labels.isEmpty()) {
      result.put("fieldLabels", labels);
    }
    return result;
  }

  public static Map<String, Object> lifecycle(final String name, final boolean deleted) {
    final var result = change(name, Map.of("deleted", !deleted), Map.of("deleted", deleted));
    result.put("fieldLabels", Map.of("deleted", "Eliminado"));
    return result;
  }

  private static Map<String, Object> details(
      final String name,
      final Map<String, Object> before,
      final Map<String, Object> after,
      final List<String> changedFields) {
    final Map<String, Object> details = new LinkedHashMap<>();
    details.put("entityName", name);
    details.put("changedFields", changedFields);
    details.put("before", before);
    details.put("after", after);
    return details;
  }

  private static List<String> changedFields(
      final Map<String, Object> before, final Map<String, Object> after) {
    return Stream.concat(before.keySet().stream(), after.keySet().stream())
        .distinct()
        .filter(key -> !Objects.equals(before.get(key), after.get(key)))
        .toList();
  }
}
