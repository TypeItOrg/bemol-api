package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;

record EnversHistoryTable(
    String name, String label, List<String> ids, List<EnversHistoryField> fields) {
  static EnversHistoryTable of(
      final String name, final String label, final String ids, final String fields) {
    return new EnversHistoryTable(
        name,
        label,
        List.of(ids.split(" ")),
        Arrays.stream(fields.split(" "))
            .map(
                value -> {
                  final var parts = value.split(":");
                  return new EnversHistoryField(
                      parts[0],
                      requireNonNull(EnversHistoryFieldLabels.LABELS.get(parts[0])),
                      parts[1]);
                })
            .toList());
  }

  String sqlName() {
    return "envers_poc_" + name + "_aud";
  }
}
