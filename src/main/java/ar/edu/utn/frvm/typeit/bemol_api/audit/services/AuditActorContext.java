package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

/** Audit actor identity resolved from the current security context. */
public record AuditActorContext(
    AuditActorKind kind,
    @Nullable UUID personId,
    @Nullable UUID platformAccountId,
    boolean delegated,
    @Nullable UUID activePersonId) {

  private static final AuditActorContext ANONYMOUS =
      new AuditActorContext(AuditActorKind.ANONYMOUS, null, null, false, null);

  public static AuditActorContext current() {
    final Object principal = currentPrincipal();
    if (principal instanceof JwtAuthenticatedUser user) {
      return institutional(user);
    }
    if (principal instanceof JwtAuthenticatedPlatformAccount account) {
      return platform(account);
    }
    return ANONYMOUS;
  }

  public static @Nullable Object currentPrincipal() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : authentication.getPrincipal();
  }

  public static @Nullable String currentRequestId() {
    return MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
  }

  public boolean isPlatformAccount() {
    return kind == AuditActorKind.PLATFORM;
  }

  private static AuditActorContext institutional(final JwtAuthenticatedUser user) {
    return new AuditActorContext(
        AuditActorKind.INSTITUTION, user.actorPersonId(), null, user.delegated(), user.personId());
  }

  private static AuditActorContext platform(final JwtAuthenticatedPlatformAccount account) {
    return new AuditActorContext(
        AuditActorKind.PLATFORM, null, account.platformAccountId(), false, null);
  }
}
