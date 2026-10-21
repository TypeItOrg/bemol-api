package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PlatformAccountAdminResponse(
    UUID platformAccountId,
    String name,
    String lastName,
    String email,
    boolean enabled,
    Instant createdAt,
    PlatformRoleCode roleCode,
    String roleName) {

  public static PlatformAccountAdminResponse from(final PlatformAccount account) {
    final PlatformRoleCode roleCode = PlatformRoleCode.PLATFORM_ADMIN;

    return PlatformAccountAdminResponse.builder()
        .platformAccountId(account.getId())
        .name(account.getName())
        .lastName(account.getLastName())
        .email(account.getEmail())
        .enabled(account.isEnabled())
        .createdAt(account.getCreatedAt())
        .roleCode(roleCode)
        .roleName(roleCode.getDisplayName())
        .build();
  }
}
