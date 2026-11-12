package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditScope;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditMessages;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GlobalAuditEventFilter(
    @Nullable AuditScope scope,
    @Nullable UUID institutionId,
    @Nullable AuditActorKind actorKind,
    @Nullable UUID actorId,
    @Nullable UUID subjectPersonId,
    @Nullable String entityType,
    @Nullable UUID entityId,
    @Nullable String action,
    @Nullable String requestId,
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable String module,
    @Nullable String search,
    @Nullable UUID personId,
    @Nullable MailType mailType) {
  public GlobalAuditEventFilter {
    validateDateRange(from, to);
    validateActor(actorKind, actorId);
    validateScope(scope, institutionId);
  }

  public static void validateScope(
      @Nullable final AuditScope scope, @Nullable final UUID institutionId) {
    if (scope == AuditScope.PLATFORM && institutionId != null) {
      throw new AuditValidationException(AuditMessages.INVALID_SCOPE_FILTER);
    }
  }

  private static void validateDateRange(@Nullable final Instant from, @Nullable final Instant to) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new AuditValidationException(AuditMessages.INVALID_DATE_RANGE);
    }
  }

  private static void validateActor(
      @Nullable final AuditActorKind actorKind, @Nullable final UUID actorId) {
    final boolean identifiableKind =
        actorKind == AuditActorKind.INSTITUTION || actorKind == AuditActorKind.PLATFORM;

    if (actorId != null && !identifiableKind) {
      throw new AuditValidationException(AuditMessages.INVALID_ACTOR_FILTER);
    }
  }
}
