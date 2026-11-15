package ar.edu.utn.frvm.typeit.bemol_api.auth.events;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PlatformAccountSecurityMessageRequested(
    UUID platformAccountId,
    String email,
    String fullName,
    Kind kind,
    @Nullable String token,
    MailAuditContext auditContext,
    @Nullable String detail) {

  public static PlatformAccountSecurityMessageRequested passkeyRegistered(
      final PlatformAccount account, final String label) {
    return new PlatformAccountSecurityMessageRequested(
        account.getId(),
        account.getEmail(),
        account.getName() + " " + account.getLastName(),
        Kind.PASSKEY_REGISTERED,
        null,
        MailAuditContext.forPlatformAccount(account.getId()),
        label);
  }

  public enum Kind {
    EMAIL_CONFIRMATION,
    EMAIL_CHANGE_REQUESTED,
    EMAIL_CHANGED,
    PASSWORD_CHANGED,
    PASSKEY_REGISTERED
  }
}
