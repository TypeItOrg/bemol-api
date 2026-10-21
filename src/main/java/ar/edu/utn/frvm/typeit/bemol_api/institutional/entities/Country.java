package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "countries",
    uniqueConstraints = {
      @UniqueConstraint(name = "countries_name_unique", columnNames = "name"),
      @UniqueConstraint(name = "countries_iso_code_unique", columnNames = "iso_code")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Country extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "country_id")
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "iso_code", length = 3)
  private String isoCode;
}
