package ar.edu.utn.frvm.typeit.boero_api.audit.payloads;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuditEventFilter(
    @Nullable UUID subjectPersonId,
    @Nullable UUID actorPersonId,
    @Nullable UUID entityId,
    @Nullable AuditAction action,
    @Nullable Boolean actedOnBehalf,
    @Nullable Instant from,
    @Nullable Instant to) {}
