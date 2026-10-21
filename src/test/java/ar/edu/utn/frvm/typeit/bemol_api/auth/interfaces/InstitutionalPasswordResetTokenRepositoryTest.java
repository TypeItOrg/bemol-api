package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createUser;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class InstitutionalPasswordResetTokenRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private InstitutionalPasswordResetTokenRepository tokenRepository;

  @Test
  @DisplayName("Should replace an existing password reset token for the same user")
  void shouldReplaceExistingPasswordResetTokenForSameUser() {
    final Institution institution = createInstitution(entityManager, "boero-recovery-token");
    final User user = createUser(entityManager, institution, "12345678");
    entityManager.flush();

    tokenRepository.save(createToken(user, "old-token"));
    entityManager.flush();

    tokenRepository.deleteByUserId(user.getId());
    tokenRepository.save(createToken(user, "new-token"));
    entityManager.flush();

    assertThat(tokenRepository.findAll())
        .singleElement()
        .extracting(token -> token.getTokenHash())
        .isEqualTo("new-token");
  }

  private InstitutionalPasswordResetToken createToken(final User user, final String tokenHash) {
    return InstitutionalPasswordResetToken.builder()
        .user(user)
        .tokenHash(tokenHash)
        .expiresAt(Instant.now().plus(java.time.Duration.ofMinutes(30)))
        .build();
  }
}
