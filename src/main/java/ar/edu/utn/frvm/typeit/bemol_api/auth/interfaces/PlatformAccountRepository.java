package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, UUID> {

  Optional<PlatformAccount> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

  long countByEnabledTrue();

  @Query(
      """
      SELECT account FROM PlatformAccount account
      WHERE (:enabled IS NULL OR account.enabled = :enabled)
        AND (
          :search IS NULL
          OR UNACCENT_LOWER(account.name) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(account.lastName) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR UNACCENT_LOWER(CONCAT(account.name, ' ', account.lastName)) LIKE UNACCENT_LOWER(CONCAT('%', CAST(:search AS string), '%'))
          OR LOWER(account.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        )
      """)
  Page<PlatformAccount> findByFilters(
      @Param("search") @Nullable String search,
      @Param("enabled") @Nullable Boolean enabled,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT account FROM PlatformAccount account ORDER BY account.id")
  List<PlatformAccount> findAllForUpdate();
}
