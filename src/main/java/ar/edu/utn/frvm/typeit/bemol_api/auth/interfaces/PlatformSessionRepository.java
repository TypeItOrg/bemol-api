package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformSessionRepository extends JpaRepository<PlatformSession, UUID> {

  List<PlatformSession> findByPlatformAccountIdAndActive(UUID platformAccountId, boolean active);

  boolean existsByIdAndActiveTrue(UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE PlatformSession s
      SET s.active = false, s.endedAt = :endedAt
      WHERE s.id IN :sessionIds
      """)
  void deactivateByIds(
      @Param("sessionIds") Collection<UUID> sessionIds, @Param("endedAt") Instant endedAt);
}
