package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(
    name = "addresses",
    uniqueConstraints =
        @UniqueConstraint(
            name = "addresses_institution_id_id_unique",
            columnNames = {"institution_id", "address_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Address extends Auditable implements AuditSubject {

  @Id
  @GeneratedUUIDv7
  @Column(name = "address_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @Column(nullable = false)
  private String street;

  @Column(length = 50)
  private @Nullable String number;

  @Column(length = 50)
  private @Nullable String floor;

  @Column(length = 50)
  private @Nullable String apartment;

  private @Nullable String neighborhood;

  @Column(name = "additional_info")
  private @Nullable String additionalInfo;

  public static Address create(
      final Institution institution,
      final City city,
      final String street,
      final @Nullable String number,
      final @Nullable String floor,
      final @Nullable String apartment,
      final @Nullable String neighborhood,
      final @Nullable String additionalInfo) {
    return Address.builder()
        .institution(institution)
        .city(city)
        .street(street)
        .number(number)
        .floor(floor)
        .apartment(apartment)
        .neighborhood(neighborhood)
        .additionalInfo(additionalInfo)
        .build();
  }

  public void update(
      final City city,
      final String street,
      final @Nullable String number,
      final @Nullable String floor,
      final @Nullable String apartment,
      final @Nullable String neighborhood,
      final @Nullable String additionalInfo) {
    this.city = city;
    this.street = street;
    this.number = number;
    this.floor = floor;
    this.apartment = apartment;
    this.neighborhood = neighborhood;
    this.additionalInfo = additionalInfo;
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    final Map<String, Object> values = new LinkedHashMap<>();
    values.put("city", city == null ? null : city.getId());
    values.put("street", street);
    values.put("number", number);
    values.put("floor", floor);
    values.put("apartment", apartment);
    values.put("neighborhood", neighborhood);
    values.put("additionalInfo", additionalInfo);
    return new AuditSnapshot.Snapshot(
        values,
        Map.of(),
        Map.of(
            "city",
            "Ciudad",
            "street",
            "Calle",
            "number",
            "Número de domicilio",
            "floor",
            "Piso",
            "apartment",
            "Departamento",
            "neighborhood",
            "Barrio",
            "additionalInfo",
            "Información adicional"));
  }
}
