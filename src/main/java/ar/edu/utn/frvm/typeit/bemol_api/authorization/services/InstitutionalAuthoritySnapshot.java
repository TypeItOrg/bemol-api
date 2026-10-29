package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record InstitutionalAuthoritySnapshot(
    Set<PermissionCode> permissions,
    List<String> roles,
    Map<PermissionCode, PermissionAccess> permissionScopes,
    Set<SystemRoleCode> systemRoleCodes)
    implements Serializable {
  public InstitutionalAuthoritySnapshot {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    roles = roles == null ? List.of() : List.copyOf(roles);
    systemRoleCodes = systemRoleCodes == null ? Set.of() : Set.copyOf(systemRoleCodes);
    permissionScopes = permissionScopes == null ? Map.of() : Map.copyOf(permissionScopes);
  }

  public InstitutionalAuthoritySnapshot(
      Set<PermissionCode> permissions,
      List<String> roles,
      Map<PermissionCode, PermissionAccess> permissionScopes) {
    this(permissions, roles, permissionScopes, Set.of());
  }

  public InstitutionalAuthoritySnapshot(Set<PermissionCode> permissions, List<String> roles) {
    this(
        permissions,
        roles,
        permissions.stream()
            .collect(Collectors.toMap(p -> p, p -> PermissionAccess.institution())));
  }
}
