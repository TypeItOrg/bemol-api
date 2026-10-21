package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;

@Builder
@io.swagger.v3.oas.annotations.media.Schema(
    requiredProperties = {
      "userId",
      "personId",
      "name",
      "lastName",
      "documentNumber",
      "institutionId",
      "roles",
      "permissions",
      "permissionScopes"
    })
public record UserPayload(
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) @org.jspecify.annotations.Nullable
        UUID userId,
    UUID personId,
    String name,
    String lastName,
    String documentNumber,
    UUID institutionId,
    List<String> roles,
    Set<String> permissions,
    Map<String, PermissionAccess> permissionScopes) {

  public UserPayload {
    permissionScopes = permissionScopes == null ? Map.of() : Map.copyOf(permissionScopes);
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public UserPayload withScopes(Map<PermissionCode, PermissionAccess> scopes) {
    return new UserPayload(
        userId,
        personId,
        name,
        lastName,
        documentNumber,
        institutionId,
        roles,
        permissions,
        scopes.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().getCode(), entry -> entry.getValue())));
  }

  public static UserPayload from(User user, UUID personId, Set<PermissionCode> permissions) {
    return from(user, personId, permissions, List.of());
  }

  public static UserPayload from(
      User user, UUID personId, Set<PermissionCode> permissions, List<String> roles) {
    Set<PermissionCode> grantedPermissions = permissions == null ? Set.of() : permissions;

    return UserPayload.builder()
        .userId(user.getId())
        .personId(personId)
        .name(user.getName())
        .lastName(user.getLastName())
        .documentNumber(user.getDocumentNumber())
        .institutionId(user.getInstitutionId())
        .roles(roles)
        .permissions(
            grantedPermissions.stream()
                .map(mappedPermissionCode -> mappedPermissionCode.getCode())
                .collect(Collectors.toUnmodifiableSet()))
        .build();
  }
}
