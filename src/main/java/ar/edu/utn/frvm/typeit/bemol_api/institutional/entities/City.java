package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "cities",
    uniqueConstraints =
        @UniqueConstraint(
            name = "cities_province_georef_unique",
            columnNames = {"province_id", "georef_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class City extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "city_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "province_id", nullable = false)
  private Province province;

  @Column(nullable = false)
  private String name;

  @Column(name = "georef_id", length = 20)
  private String georefId;

  @Column(name = "department_georef_id", length = 20)
  private String departmentGeorefId;

  @Column(name = "department_name")
  private String departmentName;

  @Column(name = "municipality_georef_id", length = 20)
  private String municipalityGeorefId;

  @Column(name = "municipality_name")
  private String municipalityName;
}
