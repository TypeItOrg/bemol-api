package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformEmailChangeToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformEmailChangeTokenRepository
    extends JpaRepository<PlatformEmailChangeToken, UUID> {
  Optional<PlatformEmailChangeToken> findByTokenHashAndPlatformAccount_Id(
      String tokenHash, UUID platformAccountId);

  void deleteByPlatformAccount_Id(UUID platformAccountId);
}
