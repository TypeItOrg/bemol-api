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
    MailAuditContext auditContext) {}
