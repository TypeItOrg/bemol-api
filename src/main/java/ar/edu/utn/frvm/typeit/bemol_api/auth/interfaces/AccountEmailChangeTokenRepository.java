package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.AccountEmailChangeToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountEmailChangeTokenRepository
    extends JpaRepository<AccountEmailChangeToken, UUID> {
  Optional<AccountEmailChangeToken> findByTokenHash(String hash);

  void deleteByUser_Id(UUID userId);

  @Query("select t.user.person.id from AccountEmailChangeToken t where t.tokenHash = :hash")
  Optional<UUID> findPersonIdByHash(@Param("hash") String hash);
}
