package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class InstitutionAuditDetails {
  private InstitutionAuditDetails() {}

  static Map<String, Object> snapshot(final Institution institution) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("name", institution.getName());
    values.put("cityId", institution.getCity().getId());
    values.put("cityName", institution.getCity().getName());
    values.put("street", institution.getStreet());
    values.put("number", institution.getNumber());
    values.put("neighborhood", institution.getNeighborhood());
    values.put("additionalInfo", institution.getAdditionalInfo());
    values.put("phoneNumber", institution.getPhoneNumber());
    values.put("email", institution.getEmail());
    values.put("active", institution.isActive());
    return values;
  }

  static Map<String, Object> created(final Institution institution) {
    return Map.of(
        "entityName", institution.getName(), "after", publicValues(snapshot(institution)));
  }

  static Map<String, Object> changed(
      final Map<String, Object> before, final Map<String, Object> after) {
    final var fields =
        before.keySet().stream()
            .filter(key -> !Objects.equals(before.get(key), after.get(key)))
            .toList();
    return Map.of(
        "entityName", after.get("name"),
        "changedFields", fields,
        "before", publicValues(before),
        "after", publicValues(after));
  }

  // Contact and address values only participate in no-op detection, never in persisted details.
  private static Map<String, Object> publicValues(final Map<String, Object> snapshot) {
    final var values = new LinkedHashMap<String, Object>();
    for (final var field : List.of("name", "cityName", "active")) {
      values.put(field, snapshot.get(field));
    }
    return values;
  }

  static Map<String, Object> publicAccess(final Institution institution) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("publicSubdomain", institution.getPublicSubdomain());
    return values;
  }

  static Map<String, Object> logo(final Institution institution) {
    final var values = new LinkedHashMap<String, Object>();
    values.put("logoPresent", institution.getLogoKey() != null);
    values.put("contentType", institution.getLogoContentType());
    values.put("size", institution.getLogoSize());
    return values;
  }
}
