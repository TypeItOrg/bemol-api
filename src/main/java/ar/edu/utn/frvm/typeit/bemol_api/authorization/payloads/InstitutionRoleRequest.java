package ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record InstitutionRoleRequest(
    @NotBlank(message = ValidationMessages.NAME_REQUIRED)
        @Size(max = 100, message = ValidationMessages.ROLE_NAME_MAX_LENGTH)
        String name,
    Set<String> permissions) {

  public InstitutionRoleRequest {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  @AssertTrue(message = ValidationMessages.PERMISSION_CATALOG_UNKNOWN)
  public boolean hasKnownPermissions() {
    return permissions.stream().allMatch(InstitutionRoleRequest::isKnownPermission);
  }

  private static boolean isKnownPermission(String code) {
    try {
      ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode.fromCode(code);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
