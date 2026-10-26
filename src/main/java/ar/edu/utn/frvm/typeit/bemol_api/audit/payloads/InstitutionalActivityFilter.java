package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

public record InstitutionalActivityFilter(
    @Nullable AuditAction action,
    @Nullable AuditModule module,
    @Size(max = 100) @Nullable String search,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant from,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant to,
    @Nullable MailType mailType) {
  public GlobalAuditEventFilter scoped(final UUID institutionId, final boolean mailView) {
    return new GlobalAuditEventFilter(
        AuditScope.INSTITUTION,
        institutionId,
        null,
        null,
        null,
        null,
        null,
        action,
        null,
        from,
        to,
        mailView ? AuditModule.EMAILS : module == AuditModule.EMAILS ? null : module,
        search,
        null,
        mailView ? mailType : null);
  }
}
