package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {

  @Query(
      """
      SELECT credential FROM PasskeyCredential credential
      WHERE credential.user.id = :userId
        AND credential.revokedAt IS NULL
      ORDER BY credential.createdAt ASC
      """)
  List<PasskeyCredential> findActiveByUserId(@Param("userId") UUID userId);

  @Query(
      """
      SELECT COUNT(credential) FROM PasskeyCredential credential
      WHERE credential.user.id = :userId
        AND credential.revokedAt IS NULL
      """)
  long countActiveByUserId(@Param("userId") UUID userId);

  @Query(
      """
      SELECT CASE WHEN COUNT(credential) > 0 THEN true ELSE false END
      FROM PasskeyCredential credential
      WHERE credential.user.id = :userId
        AND credential.revokedAt IS NULL
      """)
  boolean existsActiveByUserId(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT credential FROM PasskeyCredential credential WHERE credential.id = :id AND credential.user.id = :userId")
  Optional<PasskeyCredential> findWithLockByIdAndUserId(
      @Param("id") UUID id, @Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT credential FROM PasskeyCredential credential WHERE credential.credentialId = :credentialId")
  Optional<PasskeyCredential> findWithLockByCredentialId(
      @Param("credentialId") String credentialId);

  Optional<PasskeyCredential> findByCredentialId(String credentialId);
}
