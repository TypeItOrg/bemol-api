package ar.edu.utn.frvm.typeit.bemol_api.audit.enums;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import org.springframework.security.core.context.SecurityContextHolder;

public enum AuditOrigin {
  SELF_REGISTRATION,
  EMAIL_VERIFICATION,
  INSTITUTIONAL_ADMINISTRATION,
  PLATFORM_ADMINISTRATION,
  SELF_SERVICE,
  MAIL_DELIVERY;

  public static AuditOrigin administration() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
            && authentication.getPrincipal() instanceof JwtAuthenticatedPlatformAccount
        ? PLATFORM_ADMINISTRATION
        : INSTITUTIONAL_ADMINISTRATION;
  }
}
