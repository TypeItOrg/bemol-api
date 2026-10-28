package ar.edu.utn.frvm.typeit.bemol_api.auth.events;

import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AccountEmailMessageRequested(
    UUID personId,
    String email,
    String fullName,
    String institutionName,
    @Nullable String token,
    @Nullable String publicSubdomain,
    @Nullable String logoVersion,
    MailAuditContext auditContext) {
  public AccountEmailMessageRequested(
      final UUID personId,
      final String email,
      final String fullName,
      final String institutionName,
      final @Nullable String token,
      final @Nullable String publicSubdomain,
      final MailAuditContext auditContext) {
    this(personId, email, fullName, institutionName, token, publicSubdomain, null, auditContext);
  }
}
