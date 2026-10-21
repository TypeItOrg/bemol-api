package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class RolePermissionId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "permission_id")
  private UUID permissionId;

  public static RolePermissionId of(UUID roleId, UUID permissionId) {
    return new RolePermissionId(roleId, permissionId);
  }
}
