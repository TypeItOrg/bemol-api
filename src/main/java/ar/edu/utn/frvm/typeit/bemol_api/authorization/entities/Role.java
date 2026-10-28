package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

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
public class Role extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "role_id")
  private UUID id;

  @Column(nullable = false, length = 50)
  @Audited
  private String code;

  @Column(nullable = false, length = 100)
  @Audited
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Audited
  private RoleScope scope;

  @Column(name = "is_system", nullable = false)
  @Builder.Default
  @Audited
  private boolean system = true;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "institution_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
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
}
