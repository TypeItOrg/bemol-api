package ar.edu.utn.frvm.typeit.boero_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.UserSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
  List<UserSession> findByUserIdAndActive(UUID userId, boolean active);

  Page<UserSession> findByUserIdAndActive(UUID userId, boolean active, Pageable pageable);

  long countByUserIdAndActive(UUID userId, boolean active);

  Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByIdAndActiveTrue(UUID id);

  @Query(
      """
      SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
      FROM UserSession s, User u
      WHERE s.id = :sessionId
        AND s.userId = u.id
        AND s.active = true
        AND u.enabled = true
        AND u.selfAccessEnabled = true
        AND u.person.deleted = false
        AND u.institution.active = true
      """)
  boolean existsUsableById(@Param("sessionId") UUID sessionId);

  @Query(
      """
      SELECT s
      FROM UserSession s, User u
      WHERE s.userId = u.id
        AND u.institution.id = :institutionId
        AND s.active = true
      """)
  List<UserSession> findActiveByInstitutionId(@Param("institutionId") UUID institutionId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE UserSession s
      SET s.active = false, s.endedAt = :endedAt
      WHERE s.id IN :sessionIds
      """)
  void deactivateByIds(
      @Param("sessionIds") Collection<UUID> sessionIds, @Param("endedAt") Instant endedAt);
}
