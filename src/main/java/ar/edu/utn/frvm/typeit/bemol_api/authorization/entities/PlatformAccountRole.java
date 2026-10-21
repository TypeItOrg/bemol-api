package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages;
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
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(
    name = "platform_account_roles",
    uniqueConstraints =
        @UniqueConstraint(
            name = "platform_account_roles_unique",
            columnNames = {"platform_account_id", "role_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlatformAccountRole extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "platform_account_role_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_account_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private PlatformAccount platformAccount;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Role role;

  public static PlatformAccountRole assign(final PlatformAccount platformAccount, final Role role) {
    if (role.getScope() != RoleScope.PLATFORM || role.getInstitution() != null) {
      throw new IllegalArgumentException(AuthorizationMessages.PLATFORM_ROLE_REQUIRED);
    }

    return PlatformAccountRole.builder().platformAccount(platformAccount).role(role).build();
  }
}
