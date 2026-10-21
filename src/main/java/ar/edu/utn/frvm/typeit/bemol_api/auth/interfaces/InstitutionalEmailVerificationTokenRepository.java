package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalEmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstitutionalEmailVerificationTokenRepository
    extends JpaRepository<InstitutionalEmailVerificationToken, UUID> {
  Optional<InstitutionalEmailVerificationToken> findByUser_Id(UUID userId);

  Optional<InstitutionalEmailVerificationToken> findByTokenHash(String hash);

  @Query("select t.user.id from InstitutionalEmailVerificationToken t where t.tokenHash = :hash")
  Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);
}
