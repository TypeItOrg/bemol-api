package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

@DynamicUpdate
@Entity
@Table(name = "institutions")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Institution extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE = new AuditResource("INSTITUTION", "Institución");

  @Id
  @GeneratedUUIDv7
  @Column(name = "institution_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @Column(nullable = false)
  private String name;

  @Column(name = "short_name", length = 50)
  private @Nullable String shortName;

  @Column(name = "public_subdomain", length = 63)
  private @Nullable String publicSubdomain;

  @Column(name = "logo_key")
  private @Nullable String logoKey;

  @Column(name = "logo_content_type", length = 32)
  private @Nullable String logoContentType;

  @Column(name = "logo_version", length = 36)
  private @Nullable String logoVersion;

  @Column(name = "logo_size")
  private @Nullable Long logoSize;

  private @Nullable String street;

  @Column(length = 50)
  private @Nullable String number;

  private @Nullable String neighborhood;

  @Column(name = "additional_info")
  private @Nullable String additionalInfo;

  @Column(name = "phone_number", length = 30)
  private @Nullable String phoneNumber;

  @Column(length = 150)
  private @Nullable String email;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  public void changePublicSubdomain(final @Nullable String publicSubdomain) {
    PublicSubdomainPolicy.validate(publicSubdomain);
    this.publicSubdomain = publicSubdomain;
  }

  public void replaceLogo(
      final String key, final String contentType, final String version, final long size) {
    this.logoKey = key;
    this.logoContentType = contentType;
    this.logoVersion = version;
    this.logoSize = size;
  }

  public void removeLogo() {
    this.logoKey = null;
    this.logoContentType = null;
    this.logoVersion = null;
    this.logoSize = null;
  }

  public void changeShortName(final @Nullable String shortName) {
    this.shortName = shortName == null || shortName.isBlank() ? null : shortName.strip();
  }

  public void rename(final String name) {
    this.name = name;
  }

  public void updateLocation(
      final City city,
      final @Nullable String street,
      final @Nullable String number,
      final @Nullable String neighborhood,
      final @Nullable String additionalInfo) {
    this.city = city;
    this.street = street;
    this.number = number;
    this.neighborhood = neighborhood;
    this.additionalInfo = additionalInfo;
  }

  public void updateContact(final @Nullable String phoneNumber, final @Nullable String email) {
    this.phoneNumber = phoneNumber;
    this.email = email;
  }

  public boolean updateStatus(final boolean active) {
    if (this.active == active) {
      return false;
    }

    this.active = active;
    return true;
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields()
        .expose("name", "Nombre", name)
        .expose("shortName", "Nombre corto", shortName)
        .compare("cityId", "Ciudad", city.getId())
        .expose("cityName", "Ciudad", city.getName())
        .compare("street", "Calle", street)
        .compare("number", "Número de domicilio", number)
        .compare("neighborhood", "Barrio", neighborhood)
        .compare("additionalInfo", "Información adicional", additionalInfo)
        .compare("phoneNumber", "Teléfono", phoneNumber)
        .compare("email", "Correo electrónico", email)
        .expose("active", "Estado", active)
        .build();
  }

  public AuditSnapshot.Snapshot publicAccessAuditSnapshot() {
    return AuditSnapshot.fields()
        .expose("publicSubdomain", "Subdominio público", publicSubdomain)
        .build();
  }

  public AuditSnapshot.Snapshot logoAuditSnapshot() {
    return AuditSnapshot.fields()
        .expose("logoPresent", "Logo configurado", logoKey != null)
        .expose("contentType", "Formato del archivo", logoContentType)
        .expose("size", "Tamaño del archivo", logoSize)
        .build();
  }

  public AuditSnapshot.Snapshot statusAuditSnapshot() {
    return AuditSnapshot.fields().expose("active", "Estado", active).build();
  }
}
