package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

public record InstitutionalActivityFilter(
    @Nullable String action,
    @Nullable String module,
    @Size(max = 100) @Nullable String search,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant from,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant to,
    @Nullable MailType mailType) {
  public static InstitutionalActivityFilter empty() {
    return new InstitutionalActivityFilter(null, null, null, null, null, null);
  }

  public GlobalAuditEventFilter scoped(final UUID institutionId) {
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
        module,
        search,
        null,
        mailType);
  }
}
