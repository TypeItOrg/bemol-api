package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditEntityChangeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditFieldChangeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.audit.payloads.AuditHistoryResponse;
import jakarta.persistence.EntityManagerFactory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads only explicitly allowed columns; never loads or serializes historical entity graphs. */
@Component
@RequiredArgsConstructor
public class EnversEventHistory {
  private static final Map<String, EnversHistoryTable> TABLES =
      Stream.of(
              AcademicEnversHistoryTables.TABLES,
              EnrollmentEnversHistoryTables.TABLES,
              IdentityEnversHistoryTables.TABLES)
          .flatMap(tables -> tables.stream())
          .collect(Collectors.toUnmodifiableMap(table -> table.name(), table -> table));

  private final NamedParameterJdbcTemplate jdbc;
  private final EntityManagerFactory entityManagerFactory;

  public @Nullable AuditHistoryResponse find(final AuditEvent event) {
    final var transactionId = event.getTransactionId();
    if (transactionId == null) {
      return null;
    }
    final var revisions =
        jdbc.queryForList(
            "SELECT id FROM envers_poc_revisions WHERE transaction_id = :transaction",
            Map.of("transaction", transactionId),
            Long.class);
    if (revisions.isEmpty()) {
      return null;
    }
    final long revision = revisions.getFirst();
    final Map<String, List<HistoryRow>> rows = new HashMap<>();
    final List<AuditEntityChangeResponse> changes = new ArrayList<>();
    for (final var scope : EnversHistoryScope.forEntity(event.getEntityType())) {
      final var table = requireNonNull(TABLES.get(scope.table()));
      final List<UUID> ids;
      final String column;
      if (scope.parent() == null) {
        ids = List.of(event.getEntityId());
        column = table.ids().getFirst();
      } else {
        final var parent = requireNonNull(TABLES.get(scope.parent()));
        final var parentColumn =
            scope.parentColumn() == null ? parent.ids().getFirst() : scope.parentColumn();
        ids =
            rows.getOrDefault(parent.name(), List.of()).stream()
                .map(row -> row.references().get(parentColumn))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        column = scope.column();
      }
      final List<HistoryRow> selected =
          ids.isEmpty()
              ? List.of()
              : jdbc.query(
                  EnversHistoryQueries.query(table, column),
                  Map.of("revision", revision, "ids", ids),
                  (rs, index) -> row(table, rs, revision));
      // A legacy root may have no baseline, but its children can still have history.
      rows.put(
          table.name(),
          selected.isEmpty() && scope.parent() == null
              ? List.of(new HistoryRow(Map.of(column, event.getEntityId()), null))
              : selected);
      selected.stream().map(row -> row.change()).filter(Objects::nonNull).forEach(changes::add);
    }
    return new AuditHistoryResponse(revision, List.copyOf(changes));
  }

  private HistoryRow row(final EnversHistoryTable table, final ResultSet rs, final long revision)
      throws SQLException {
    final Map<String, UUID> references = new LinkedHashMap<>();
    for (final var id : table.ids()) {
      references.put(id, requireNonNull(rs.getObject("ref_" + id, UUID.class)));
    }
    for (final var field : table.fields()) {
      if (field.valueType().equals("REFERENCE")) {
        final var reference = rs.getObject("ref_" + field.column(), UUID.class);
        if (reference != null) {
          references.put(field.column(), reference);
        }
      }
    }
    if (rs.getLong("rev") != revision) {
      return new HistoryRow(references, null);
    }
    final int operation = rs.getInt("revtype");
    final boolean hasPrevious =
        rs.getObject("previous_rev") != null && rs.getInt("previous_type") != 2;
    final boolean previousKnown = operation != 1 || hasPrevious;
    final List<AuditFieldChangeResponse> fields = new ArrayList<>();
    for (final var field : table.fields()) {
      final var after = operation == 2 ? null : readValue(rs, "new_", field);
      final var before =
          hasPrevious
              ? readValue(rs, "old_", field)
              : operation == 2 ? readValue(rs, "new_", field) : null;
      if (!Objects.equals(before, after) || !previousKnown) {
        fields.add(
            new AuditFieldChangeResponse(
                field.column(), field.label(), field.valueType(), before, after));
      }
    }
    final var entityId =
        table.ids().stream()
            .map(id -> requireNonNull(references.get(id)).toString())
            .collect(Collectors.joining(" / "));
    return new HistoryRow(
        references,
        fields.isEmpty()
            ? null
            : new AuditEntityChangeResponse(
                table.label(),
                entityId,
                switch (operation) {
                  case 0 -> "CREATED";
                  case 2 -> "DELETED";
                  default -> "UPDATED";
                },
                previousKnown,
                List.copyOf(fields)));
  }

  private @Nullable String readValue(
      final ResultSet rs, final String prefix, final EnversHistoryField field) throws SQLException {
    final var column = prefix + field.column();
    if (!field.valueType().equals("TIME")) {
      return rs.getString(column);
    }
    // Match Hibernate's TIME extraction: casting the physical column to text shifts local
    // schedules.
    final var configuredZone = entityManagerFactory.getProperties().get("hibernate.jdbc.time_zone");
    final var zone =
        configuredZone instanceof TimeZone timeZone
            ? timeZone
            : configuredZone == null
                ? null
                : TimeZone.getTimeZone(ZoneId.of(configuredZone.toString()));
    final var time =
        zone == null ? rs.getTime(column) : rs.getTime(column, Calendar.getInstance(zone));
    return time == null
        ? null
        : time.toLocalTime()
            .withNano(Math.floorMod(time.getTime(), 1000) * 1_000_000)
            .format(DateTimeFormatter.ISO_LOCAL_TIME);
  }

  private record HistoryRow(
      Map<String, UUID> references, @Nullable AuditEntityChangeResponse change) {}
}
