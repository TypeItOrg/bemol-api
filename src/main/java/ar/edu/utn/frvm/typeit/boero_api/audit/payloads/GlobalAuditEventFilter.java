package ar.edu.utn.frvm.typeit.boero_api.audit.payloads;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditModule;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditMessages;
import ar.edu.utn.frvm.typeit.boero_api.audit.exceptions.AuditValidationException;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GlobalAuditEventFilter(
    @Nullable AuditScope scope,
    @Nullable UUID institutionId,
    @Nullable AuditActorKind actorKind,
    @Nullable UUID actorId,
    @Nullable UUID subjectPersonId,
    @Nullable AuditEntityType entityType,
    @Nullable UUID entityId,
    @Nullable AuditAction action,
    @Nullable String requestId,
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable AuditModule module,
    @Nullable String search,
    @Nullable UUID personId) {
  public GlobalAuditEventFilter {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new AuditValidationException(AuditMessages.INVALID_DATE_RANGE);
    }
    if (actorId != null && (actorKind == null || actorKind == AuditActorKind.ANONYMOUS)) {
      throw new AuditValidationException(AuditMessages.INVALID_ACTOR_FILTER);
    }
    if (scope == AuditScope.PLATFORM && institutionId != null) {
      throw new AuditValidationException(AuditMessages.INVALID_SCOPE_FILTER);
    }
  }
}
