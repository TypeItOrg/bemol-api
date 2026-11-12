package ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AuditSnapshot {
  private AuditSnapshot() {}

  public record Snapshot(
      Map<String, Object> values, Map<String, Object> visibleValues, Map<String, String> labels) {
    public Snapshot(final Map<String, Object> values, final Map<String, Object> visibleValues) {
      this(values, visibleValues, Map.of());
    }

    public Snapshot {
      labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
      values = copy(values);
      visibleValues = copy(visibleValues);
    }

    public Map<String, Object> labeledValues() {
      final var result = new LinkedHashMap<>(visibleValues);
      final var publicLabels = new LinkedHashMap<>(labels);
      publicLabels.keySet().retainAll(visibleValues.keySet());
      result.put("fieldLabels", Collections.unmodifiableMap(publicLabels));
      return Collections.unmodifiableMap(result);
    }

    @Override
    public String toString() {
      return "AuditSnapshot[fields=" + values.keySet() + "]";
    }
  }

  private static Map<String, Object> copy(final Map<String, Object> source) {
    final Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(key, copyValue(value)));
    return Collections.unmodifiableMap(result);
  }

  private static @Nullable Object copyValue(final @Nullable Object value) {
    if (value instanceof Map<?, ?> map) {
      final var result = new LinkedHashMap<Object, Object>();
      map.forEach((key, item) -> result.put(key, copyValue(item)));
      return Collections.unmodifiableMap(result);
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(AuditSnapshot::copyValue).toList();
    }
    return value;
  }

  public static @Nullable Map<String, String> date(final @Nullable LocalDate value) {
    return value == null ? null : Map.of("type", "DATE", "value", value.toString());
  }

  public static @Nullable Map<String, String> dateTime(final @Nullable Instant value) {
    return value == null ? null : Map.of("type", "DATETIME", "value", value.toString());
  }

  public static Snapshot empty() {
    return new Snapshot(Map.of(), Map.of(), Map.of());
  }

  public static Builder fields() {
    return new Builder();
  }

  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> visible = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();

    private Builder() {}

    public Builder expose(final String key, final String label, final @Nullable Object value) {
      return expose(key, label, value, value);
    }

    public Builder expose(
        final String key,
        final String label,
        final @Nullable Object comparisonValue,
        final @Nullable Object visibleValue) {
      compare(key, label, comparisonValue);
      visible.put(key, visibleValue);
      return this;
    }

    public Builder compare(final String key, final String label, final @Nullable Object value) {
      values.put(key, value);
      labels.put(key, label);
      return this;
    }

    public Snapshot build() {
      return new Snapshot(values, visible, labels);
    }
  }

  public static Snapshot of(final AuditSubject entity) {
    return entity.auditSnapshot();
  }
}
