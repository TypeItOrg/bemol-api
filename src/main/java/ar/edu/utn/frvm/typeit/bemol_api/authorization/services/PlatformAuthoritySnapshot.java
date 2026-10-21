package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import java.io.Serializable;
import java.util.Set;

public record PlatformAuthoritySnapshot(
    Set<PermissionCode> permissions, Set<PlatformRoleCode> roles) implements Serializable {

  public PlatformAuthoritySnapshot {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }
}
