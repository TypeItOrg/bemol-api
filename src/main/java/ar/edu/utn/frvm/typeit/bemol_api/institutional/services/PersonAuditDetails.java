package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.LinkedHashMap;
import java.util.Map;

final class PersonAuditDetails {
  private PersonAuditDetails() {}

  // These values are only compared in memory; AuditChangeDetails persists the changed field names.
  static Map<String, Object> snapshot(final Person person) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("documentNumber", person.getDocumentNumber());
    result.put("firstName", person.getFirstName());
    result.put("lastName", person.getLastName());
    result.put("email", person.getEmail());
    result.put("phoneNumber", person.getPhoneNumber());
    result.put("birthDate", person.getBirthDate());
    result.put("birthCityId", person.getBirthCity() == null ? null : person.getBirthCity().getId());
    result.put(
        "nationalityCountryId",
        person.getNationalityCountry() == null ? null : person.getNationalityCountry().getId());
    final var address = person.getAddress();
    if (address != null) {
      final Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("cityId", address.getCity().getId());
      fields.put("street", address.getStreet());
      fields.put("number", address.getNumber());
      fields.put("floor", address.getFloor());
      fields.put("apartment", address.getApartment());
      fields.put("neighborhood", address.getNeighborhood());
      fields.put("additionalInfo", address.getAdditionalInfo());
      result.put("address", fields);
    }
    return result;
  }
}
