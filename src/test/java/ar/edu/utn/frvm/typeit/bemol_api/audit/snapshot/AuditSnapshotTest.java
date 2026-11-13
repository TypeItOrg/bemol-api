package ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYearFixtures;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.InstrumentIcon;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Address;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditSnapshotTest {
  @Test
  void periodComparesAtPostgresPrecisionWithoutFalseChanges() {
    final var instant = Instant.parse("2026-11-11T12:00:00.123456789Z");
    final var period =
        EnrollmentPeriod.builder()
            .academicYear(AcademicYearFixtures.planned(UUID.randomUUID(), new Institution(), 2026))
            .name("Período")
            .status(EnrollmentPeriodStatus.PLANNED)
            .startDate(instant)
            .endDate(instant.plusSeconds(3600))
            .build();
    final var before = period.auditSnapshot();

    period.updateDetails("Período", instant, instant.plusSeconds(3600));

    assertThat(period.auditSnapshot().values()).isEqualTo(before.values());
    assertThat(before.values()).containsEntry("startDate", "2026-11-11T12:00:00.123457Z");
    assertThat(before.labels()).containsEntry("offeringIds", "Ofertas habilitadas (plan / nivel)");
  }

  @Test
  void institutionKeepsContactDataPrivateWhileReportingChangedFields() {
    final var institution =
        Institution.builder()
            .name("Escuela")
            .city(City.builder().id(UUID.randomUUID()).name("Ciudad").build())
            .email("anterior@example.com")
            .build();
    final var before = institution.auditSnapshot();
    institution.updateContact(null, "nuevo@example.com");
    final var after = institution.auditSnapshot();

    final var details = AuditChangeDetails.change(institution.getName(), before, after);

    assertThat(details.get("changedFields")).isEqualTo(java.util.List.of("email"));
    assertThat(details.toString()).doesNotContain("anterior@example.com", "nuevo@example.com");
    assertThat(after.labels()).containsEntry("email", "Correo electrónico");
  }

  @Test
  void catalogOwnsItsLabelsAndComparesDescriptionsWithoutPersistingThem() {
    final var instrument =
        Instrument.create(
            new Institution(), "Piano", "Texto privado inicial", InstrumentIcon.PIANO);
    final var before = instrument.auditSnapshot();
    instrument.update("Piano", "Texto privado nuevo", InstrumentIcon.PIANO);
    final var after = instrument.auditSnapshot();

    final var details = AuditChangeDetails.change("Piano", before, after);

    assertThat(details.get("changedFields")).isEqualTo(java.util.List.of("description"));
    assertThat(details.get("fieldLabels")).isEqualTo(after.labels());
    assertThat(after.labels())
        .containsEntry("description", "Descripción")
        .containsEntry("name", "Nombre");
    assertThat(details.toString()).doesNotContain("Texto privado inicial", "Texto privado nuevo");
    assertThat(before.visibleValues()).doesNotContainKey("description");
    assertThat(AuditChangeDetails.change("Piano", AuditSnapshot.empty(), after))
        .containsKey("fieldLabels");
  }

  @Test
  void personDefinesVisibleFieldsWithoutExposingPrivateValues() {
    final var address = Address.builder().street("Privada").build();
    final var person =
        Person.builder()
            .firstName("Ana")
            .lastName("Pérez")
            .phoneNumber("123")
            .documentNumber("12345678")
            .email("private@example.com")
            .address(address)
            .nationalityCountry(Country.builder().name("Argentina").build())
            .build();

    final var snapshot = AuditSnapshot.of(person);

    assertThat(snapshot.visibleValues())
        .containsOnlyKeys("firstName", "lastName", "phoneNumber", "nationalityCountry");
    assertThat(snapshot.values()).containsEntry("email", "private@example.com");
    assertThat(snapshot.toString()).doesNotContain("private@example.com", "12345678", "Privada");
    address.update(
        City.builder().id(UUID.randomUUID()).build(), "Otra", null, null, null, null, null);
    assertThat(snapshot.values().get("address")).isNotEqualTo(address.auditSnapshot().values());
    assertThat(snapshot.visibleValues()).doesNotContainKeys("address", "email", "documentNumber");
  }

  @Test
  void addressComparesCityIdentityAndNeverExposesValues() {
    final var first = City.builder().id(UUID.randomUUID()).build();
    final var second = City.builder().id(UUID.randomUUID()).build();
    final var address = Address.builder().city(first).street("Privada").build();
    final var before = address.auditSnapshot();

    address.update(second, "Privada", null, null, null, null, null);

    assertThat(before.values()).containsEntry("city", first.getId());
    assertThat(address.auditSnapshot().values()).containsEntry("city", second.getId());
    assertThat(before.visibleValues()).isEmpty();
  }

  @Test
  void referenceChangesUseIdentityButPublishOnlyTheDisplayValue() {
    final var first = Country.builder().id(UUID.randomUUID()).name("Mismo nombre").build();
    final var second = Country.builder().id(UUID.randomUUID()).name("Mismo nombre").build();
    final var person = Person.builder().nationalityCountry(first).build();
    final var before = person.auditSnapshot();
    final var other = Person.builder().nationalityCountry(second).build();
    final var after = other.auditSnapshot();

    final var details = AuditChangeDetails.change("Persona", before, after);

    assertThat(before.values()).isNotEqualTo(after.values());
    assertThat(details.get("changedFields")).isEqualTo(java.util.List.of("nationalityCountry"));
    assertThat(details.get("before")).isEqualTo(before.visibleValues());
    assertThat(details.get("after")).isEqualTo(after.visibleValues());
    assertThat(details.toString())
        .doesNotContain(first.getId().toString(), second.getId().toString());
  }

  @Test
  void snapshotCopiesMapsAndRetainsNulls() {
    final Map<String, Object> values = new LinkedHashMap<>();
    values.put("optional", null);
    final var snapshot = new AuditSnapshot.Snapshot(values, Map.of());
    values.put("later", "not captured");

    assertThat(snapshot.values()).containsOnlyKeys("optional").containsEntry("optional", null);
    assertThatThrownBy(() -> snapshot.values().put("injected", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
