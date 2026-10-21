package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class RefreshTokenRepositoryTest {

  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("Should find refresh token by hash, family and session")
  void shouldFindRefreshTokenByHashFamilyAndSession() {
    Institution institution = InstitutionalTestData.createInstitution(entityManager, "tokens-one");
    UUID sessionId = createSessionId(institution, "10000001");
    RefreshToken token = refreshToken("hash-1", "family-1", sessionId);
    refreshTokenRepository.saveAndFlush(token);

    assertThat(refreshTokenRepository.findByTokenHash("hash-1")).contains(token);
    assertThat(refreshTokenRepository.findByFamilyId("family-1")).containsExactly(token);
    assertThat(refreshTokenRepository.findBySessionId(sessionId)).containsExactly(token);
  }

  @Test
  @DisplayName("Should revoke only tokens from selected family")
  void shouldRevokeOnlyTokensFromSelectedFamily() {
    Institution institution = InstitutionalTestData.createInstitution(entityManager, "tokens-two");
    RefreshToken first =
        refreshToken("hash-1", "family-1", createSessionId(institution, "20000001"));
    RefreshToken second =
        refreshToken("hash-2", "family-1", createSessionId(institution, "20000002"));
    RefreshToken other =
        refreshToken("hash-3", "family-2", createSessionId(institution, "20000003"));
    refreshTokenRepository.save(first);
    refreshTokenRepository.save(second);
    refreshTokenRepository.saveAndFlush(other);

    refreshTokenRepository.revokeByFamilyId("family-1");

    assertThat(refreshTokenRepository.findByFamilyId("family-1"))
        .extracting(mappedRefreshToken -> mappedRefreshToken.isRevoked())
        .containsOnly(true);
    assertThat(refreshTokenRepository.findByTokenHash("hash-3"))
        .get()
        .returns(false, mappedRefreshToken -> mappedRefreshToken.isRevoked());
  }

  @Test
  @DisplayName("Should revoke only tokens from selected session")
  void shouldRevokeOnlyTokensFromSelectedSession() {
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "tokens-three");
    UUID sessionId = createSessionId(institution, "30000001");
    RefreshToken first = refreshToken("hash-1", "family-1", sessionId);
    RefreshToken second = refreshToken("hash-2", "family-2", sessionId);
    RefreshToken other =
        refreshToken("hash-3", "family-3", createSessionId(institution, "30000002"));
    refreshTokenRepository.save(first);
    refreshTokenRepository.save(second);
    refreshTokenRepository.saveAndFlush(other);

    refreshTokenRepository.revokeBySessionId(sessionId);

    assertThat(refreshTokenRepository.findBySessionId(sessionId))
        .extracting(mappedRefreshToken -> mappedRefreshToken.isRevoked())
        .containsOnly(true);
    assertThat(refreshTokenRepository.findByTokenHash("hash-3"))
        .get()
        .returns(false, mappedRefreshToken -> mappedRefreshToken.isRevoked());
  }

  private static RefreshToken refreshToken(String hash, String familyId, UUID sessionId) {
    return RefreshToken.builder()
        .sessionId(sessionId)
        .tokenHash(hash)
        .familyId(familyId)
        .expiresAt(Instant.now().plus(java.time.Duration.ofDays(7)))
        .build();
  }

  private UUID createSessionId(final Institution institution, final String documentNumber) {
    final var user = InstitutionalTestData.createUser(entityManager, institution, documentNumber);
    final var session =
        UserSession.builder()
            .userId(user.getId())
            .ipAddress("192.0.2.10")
            .userAgent("Mozilla/5.0")
            .active(true)
            .build();
    entityManager.persist(session);
    return session.getId();
  }
}
