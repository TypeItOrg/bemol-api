package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalAuthoritySnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilities;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;

@Builder(toBuilder = true)
@io.swagger.v3.oas.annotations.media.Schema(
    requiredProperties = {
      "userId",
      "personId",
      "name",
      "lastName",
      "documentNumber",
      "institutionId",
      "roles",
      "systemRoleCodes",
      "capabilities",
      "permissions",
      "permissionScopes",
      "actorPersonId",
      "delegated",
      "canRequestGuardianLinks",
      "actorPermissions"
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
    Set<SystemRoleCode> systemRoleCodes,
    InstitutionalCapabilities capabilities,
    Set<String> permissions,
    Map<String, PermissionAccess> permissionScopes,
    @org.jspecify.annotations.Nullable @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
        UUID actorPersonId,
    boolean delegated,
    boolean canRequestGuardianLinks,
    Set<String> actorPermissions) {

  public UserPayload {
    capabilities = capabilities == null ? InstitutionalCapabilities.NONE : capabilities;
    permissionScopes = permissionScopes == null ? Map.of() : Map.copyOf(permissionScopes);
    actorPermissions = actorPermissions == null ? Set.of() : Set.copyOf(actorPermissions);
    roles = roles == null ? List.of() : List.copyOf(roles);
    systemRoleCodes = systemRoleCodes == null ? Set.of() : Set.copyOf(systemRoleCodes);
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public UserPayload withScopes(Map<PermissionCode, PermissionAccess> scopes) {
    return toBuilder()
        .permissionScopes(
            scopes.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().getCode(), entry -> entry.getValue())))
        .build();
  }

  public UserPayload withActor(
      final UUID actorId,
      final boolean representing,
      final boolean canRequest,
      final Set<String> actorGrants) {
    return toBuilder()
        .actorPersonId(actorId)
        .delegated(representing)
        .canRequestGuardianLinks(canRequest)
        .actorPermissions(actorGrants)
        .build();
  }

  public static UserPayload from(
      User user, UUID personId, InstitutionalAuthoritySnapshot authorities) {
    return from(user, personId, authorities.permissions(), authorities.roles())
        .withScopes(authorities.permissionScopes())
        .toBuilder()
        .systemRoleCodes(authorities.systemRoleCodes())
        .capabilities(InstitutionalCapabilities.from(authorities))
        .build();
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
