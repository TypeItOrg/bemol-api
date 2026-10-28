package ar.edu.utn.frvm.typeit.bemol_api.auth.events;

import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PersonAccessInvited(
    UUID personId,
    String email,
    String institutionName,
    String fullName,
    String token,
    @Nullable String publicSubdomain,
    @Nullable String logoVersion,
    MailAuditContext auditContext,
    UUID invitationId) {
  public PersonAccessInvited(
      final UUID personId,
      final String email,
      final String institutionName,
      final String fullName,
      final String token,
      final @Nullable String publicSubdomain,
      final MailAuditContext auditContext) {
    this(
        personId,
        email,
        institutionName,
        fullName,
        token,
        publicSubdomain,
        null,
        auditContext,
        personId);
  }
}
