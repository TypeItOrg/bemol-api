package ar.edu.utn.frvm.typeit.boero_api.audit.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.audit.entities.AuditEvent;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  @Query(
      """
      SELECT event FROM AuditEvent event
      WHERE event.institution.id = :institutionId
        AND (:subjectPersonId IS NULL OR event.subjectPersonId = :subjectPersonId)
        AND (:actorPersonId IS NULL OR event.actorPersonId = :actorPersonId)
        AND (:entityId IS NULL OR event.entityId = :entityId)
        AND (:action IS NULL OR event.action = :action)
        AND (:actedOnBehalf IS NULL OR event.actedOnBehalf = :actedOnBehalf)
        AND (CAST(:from AS Instant) IS NULL OR event.occurredAt >= :from)
        AND (CAST(:to AS Instant) IS NULL OR event.occurredAt < :to)
      """)
  Page<AuditEvent> search(
      @Param("institutionId") UUID institutionId,
      @Param("subjectPersonId") @Nullable UUID subjectPersonId,
      @Param("actorPersonId") @Nullable UUID actorPersonId,
      @Param("entityId") @Nullable UUID entityId,
      @Param("action") @Nullable AuditAction action,
      @Param("actedOnBehalf") @Nullable Boolean actedOnBehalf,
      @Param("from") @Nullable Instant from,
      @Param("to") @Nullable Instant to,
      Pageable pageable);
}
