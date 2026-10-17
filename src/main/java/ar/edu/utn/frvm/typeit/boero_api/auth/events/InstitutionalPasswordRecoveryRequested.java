package ar.edu.utn.frvm.typeit.boero_api.auth.events;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.common.mail.MailAuditContext;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record InstitutionalPasswordRecoveryRequested(
    UUID userId,
    String recipientEmail,
    String institutionName,
    String fullName,
    String token,
    @Nullable String publicSubdomain,
    MailAuditContext auditContext) {
  public InstitutionalPasswordRecoveryRequested(
      final UUID userId,
      final String recipientEmail,
      final String institutionName,
      final String fullName,
      final String token,
      final MailAuditContext auditContext) {
    this(userId, recipientEmail, institutionName, fullName, token, null, auditContext);
  }

  public static InstitutionalPasswordRecoveryRequested from(final User user, final String token) {
    return new InstitutionalPasswordRecoveryRequested(
        user.getId(),
        user.getPerson().getEmail(),
        user.getInstitution().getName(),
        user.getName() + " " + user.getLastName(),
        token,
        user.getInstitution().getPublicSubdomain(),
        MailAuditContext.capture(user.getInstitution().getId(), user.getPerson().getId()));
  }
}
