package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformPasskeyCredentialRepository
    extends JpaRepository<PlatformPasskeyCredential, UUID> {

  @Query(
      """
      SELECT credential FROM PlatformPasskeyCredential credential
      WHERE credential.platformAccount.id = :accountId
        AND credential.revokedAt IS NULL
      ORDER BY credential.createdAt ASC
      """)
  List<PlatformPasskeyCredential> findActiveByPlatformAccountId(@Param("accountId") UUID accountId);

  @Query(
      """
      SELECT credential FROM PlatformPasskeyCredential credential
      WHERE credential.platformAccount.id = :accountId
        AND credential.revokedAt IS NULL
        AND (:used IS NULL OR (:used = true AND credential.lastUsedAt IS NOT NULL) OR (:used = false AND credential.lastUsedAt IS NULL))
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(credential.label) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  List<PlatformPasskeyCredential> findActiveByFilters(
      @Param("accountId") UUID accountId,
      @Param("search") @Nullable String search,
      @Param("used") @Nullable Boolean used,
      Sort sort);

  @Query(
      """
      SELECT COUNT(credential) FROM PlatformPasskeyCredential credential
      WHERE credential.platformAccount.id = :accountId
        AND credential.revokedAt IS NULL
      """)
  long countActiveByPlatformAccountId(@Param("accountId") UUID accountId);

  @Query(
      """
      SELECT CASE WHEN COUNT(credential) > 0 THEN true ELSE false END
      FROM PlatformPasskeyCredential credential
      WHERE credential.platformAccount.id = :accountId
        AND credential.revokedAt IS NULL
      """)
  boolean existsActiveByPlatformAccountId(@Param("accountId") UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT credential FROM PlatformPasskeyCredential credential WHERE credential.id = :id AND credential.platformAccount.id = :accountId")
  Optional<PlatformPasskeyCredential> findWithLockByIdAndPlatformAccountId(
      @Param("id") UUID id, @Param("accountId") UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT credential FROM PlatformPasskeyCredential credential WHERE credential.credentialId = :credentialId")
  Optional<PlatformPasskeyCredential> findWithLockByCredentialId(
      @Param("credentialId") String credentialId);

  Optional<PlatformPasskeyCredential> findByCredentialId(String credentialId);
}
