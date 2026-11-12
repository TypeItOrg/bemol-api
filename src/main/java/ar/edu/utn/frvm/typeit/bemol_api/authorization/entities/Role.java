package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSubject;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "roles",
    uniqueConstraints =
        @UniqueConstraint(
            name = "roles_scope_code_institution_unique",
            columnNames = {"scope", "code", "institution_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Role extends Auditable implements AuditSubject {
  public static final AuditResource AUDIT_TYPE = new AuditResource("ROLE", "Rol");

  @Id
  @GeneratedUUIDv7
  @Column(name = "role_id")
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RoleScope scope;

  @Column(name = "is_system", nullable = false)
  @Builder.Default
  private boolean system = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "institution_id")
  private Institution institution;

  public static Role customInstitutional(
      final String code, final String name, final Institution institution) {
    return Role.builder()
        .code(code)
        .name(name)
        .scope(RoleScope.INSTITUTION)
        .system(false)
        .institution(institution)
        .build();
  }

  public void rename(final String name) {
    this.name = name;
  }

  public boolean isInstitutionalAuthority() {
    return system && SystemRoleCode.INSTITUTIONAL_AUTHORITY.name().equals(code);
  }

  public boolean isRepresentative() {
    return system && SystemRoleCode.REPRESENTATIVE.name().equals(code);
  }

  public boolean replacesOnAssignment(final Role currentRole) {
    if (isRepresentative() || currentRole.isRepresentative()) {
      return false;
    }

    return isApplicant() ? !currentRole.isApplicant() : currentRole.isApplicant();
  }

  public boolean isApplicant() {
    return system && SystemRoleCode.APPLICANT.name().equals(code);
  }

  @Override
  public AuditSnapshot.Snapshot auditSnapshot() {
    return AuditSnapshot.fields().expose("name", "Nombre", name).build();
  }

  public AuditSnapshot.Snapshot auditSnapshot(final Set<String> permissionCodes) {
    final var codes = permissionCodes.stream().sorted().toList();
    final var labels =
        codes.stream().map(code -> Map.of("code", code, "label", permissionLabel(code))).toList();
    return AuditSnapshot.fields()
        .expose("name", "Nombre", name)
        .expose("permissions", "Permisos efectivos", codes, labels)
        .build();
  }

  private static String permissionLabel(final String code) {
    for (final var permission : PermissionCode.values()) {
      if (permission.getCode().equals(code)) {
        return permission.getDescription();
      }
    }
    return code;
  }
}
