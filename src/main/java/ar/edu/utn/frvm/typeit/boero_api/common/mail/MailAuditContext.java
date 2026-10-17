package ar.edu.utn.frvm.typeit.boero_api.common.mail;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

/** Immutable identifiers captured before the mail executor loses the request context. */
public record MailAuditContext(
    UUID institutionId,
    UUID personId,
    AuditActorKind actorKind,
    @Nullable UUID actorPersonId,
    @Nullable UUID actorPlatformAccountId,
    @Nullable String requestId) {
  public static MailAuditContext capture(final UUID institutionId, final UUID personId) {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final Object principal = authentication == null ? null : authentication.getPrincipal();
    final String requestId = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
    if (principal instanceof JwtAuthenticatedUser user) {
      return new MailAuditContext(
          institutionId, personId, AuditActorKind.INSTITUTION, user.personId(), null, requestId);
    }
    if (principal instanceof JwtAuthenticatedPlatformAccount account) {
      return new MailAuditContext(
          institutionId,
          personId,
          AuditActorKind.PLATFORM,
          null,
          account.platformAccountId(),
          requestId);
    }
    return new MailAuditContext(
        institutionId, personId, AuditActorKind.ANONYMOUS, null, null, requestId);
  }
}
