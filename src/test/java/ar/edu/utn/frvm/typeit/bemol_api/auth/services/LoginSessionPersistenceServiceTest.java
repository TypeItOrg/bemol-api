package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.jwtProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginSessionPersistenceServiceTest {

  @Mock private UserSessionRepository userSessionRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;

  private LoginSessionPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new LoginSessionPersistenceService(
            Clock.systemUTC(),
            userSessionRepository,
            refreshTokenRepository,
            jwtProperties(),
            new RefreshTokenGenerator());
  }

  @Test
  void createPersistsSessionAndHashedRefreshTokenInsidePersistenceBoundary() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    when(userSessionRepository.save(any(UserSession.class)))
        .thenAnswer(
            invocation -> {
              final UserSession session = invocation.getArgument(0);
              return UserSession.builder()
                  .id(sessionId)
                  .userId(session.getUserId())
                  .ipAddress(session.getIpAddress())
                  .userAgent(session.getUserAgent())
                  .rememberMe(session.isRememberMe())
                  .build();
            });

    final LoginSessionPersistenceService.Result result =
        service.create(userId, "192.0.2.1", "JUnit", true);

    final ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
    final ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
    org.mockito.Mockito.verify(userSessionRepository).save(sessionCaptor.capture());
    org.mockito.Mockito.verify(refreshTokenRepository).save(tokenCaptor.capture());

    assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(sessionCaptor.getValue().getIpAddress()).isEqualTo("192.0.2.1");
    assertThat(sessionCaptor.getValue().getUserAgent()).isEqualTo("JUnit");
    assertThat(sessionCaptor.getValue().isRememberMe()).isTrue();
    assertThat(tokenCaptor.getValue().getSessionId()).isEqualTo(sessionId);
    assertThat(tokenCaptor.getValue().getTokenHash())
        .isEqualTo(JwtService.hashToken(result.refreshToken()));
    assertThat(tokenCaptor.getValue().getExpiresAt())
        .isCloseTo(
            Instant.now().plus(jwtProperties().rememberMeTokenExpiration()),
            within(5, ChronoUnit.SECONDS));
  }
}
