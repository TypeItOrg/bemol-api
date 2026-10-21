package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstitutionalPasswordResetTokenRepository
    extends JpaRepository<InstitutionalPasswordResetToken, UUID> {

  @Query(
      "select token.user.id from InstitutionalPasswordResetToken token where token.tokenHash = :hash")
  Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);

  @Modifying(flushAutomatically = true)
  @Query("delete from InstitutionalPasswordResetToken token where token.user.id = :userId")
  void deleteByUserId(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select token from InstitutionalPasswordResetToken token
      join fetch token.user user
      where token.tokenHash = :tokenHash
      """)
  Optional<InstitutionalPasswordResetToken> findByTokenHashForUpdate(
      @Param("tokenHash") String tokenHash);
}
