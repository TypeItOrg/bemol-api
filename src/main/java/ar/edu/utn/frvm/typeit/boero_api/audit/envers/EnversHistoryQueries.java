package ar.edu.utn.frvm.typeit.boero_api.audit.envers;

import java.util.stream.Collectors;
import java.util.stream.Stream;

final class EnversHistoryQueries {
  private EnversHistoryQueries() {}

  static String query(final EnversHistoryTable table, final String column) {
    final var keys = String.join(", ", table.ids());
    final var columns =
        Stream.concat(table.ids().stream(), table.fields().stream().map(field -> field.column()))
            .distinct()
            .toList();
    final var selectedColumns =
        columns.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(", "));
    final var matches =
        table.ids().stream()
            .map(id -> "older.\"" + id + "\" = current.\"" + id + "\"")
            .collect(Collectors.joining(" AND "));
    final var values =
        table.fields().stream()
            .map(
                field ->
                    value("current", field)
                        + " AS new_"
                        + field.column()
                        + ", "
                        + value("previous", field)
                        + " AS old_"
                        + field.column())
            .collect(Collectors.joining(", "));
    final var references =
        columns.stream()
            .filter(
                value ->
                    table.ids().contains(value)
                        || table.fields().stream()
                            .anyMatch(
                                field ->
                                    field.column().equals(value)
                                        && field.valueType().equals("REFERENCE")))
            .map(value -> "current.\"" + value + "\" AS ref_" + value)
            .collect(Collectors.joining(", "));
    final var candidateMatch =
        table.ids().stream()
            .map(id -> "history.\"" + id + "\" = candidate.\"" + id + "\"")
            .collect(Collectors.joining(" AND "));
    return "SELECT current.rev, current.revtype, previous.rev AS previous_rev, previous.revtype AS previous_type, "
        + references
        + ", "
        + values
        + " FROM (SELECT DISTINCT "
        + keys
        + " FROM "
        + table.sqlName()
        + " WHERE \""
        + column
        + "\" IN (:ids) AND rev <= :revision) candidate CROSS JOIN LATERAL (SELECT rev, revtype, "
        + selectedColumns
        + " FROM "
        + table.sqlName()
        + " history WHERE "
        + candidateMatch
        + " AND rev <= :revision ORDER BY rev DESC LIMIT 1) current LEFT JOIN LATERAL (SELECT rev, revtype, "
        + selectedColumns
        + " FROM "
        + table.sqlName()
        + " older WHERE "
        + matches
        + " AND older.rev < current.rev AND current.rev = :revision"
        + " ORDER BY rev DESC LIMIT 1) previous ON true WHERE (current.\""
        + column
        + "\" IN (:ids)"
        + " OR (current.rev = :revision AND previous.\""
        + column
        + "\" IN (:ids)))"
        + " AND (current.revtype <> 2 OR current.rev = :revision) ORDER BY "
        + table.ids().stream()
            .map(id -> "current.\"" + id + "\"")
            .collect(Collectors.joining(", "));
  }

  private static String value(final String alias, final EnversHistoryField field) {
    final var column = alias + ".\"" + field.column() + "\"";
    return field.valueType().equals("DATETIME")
        ? "to_char(" + column + " AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')"
        : field.valueType().equals("TIME") ? column : "CAST(" + column + " AS text)";
  }
}
