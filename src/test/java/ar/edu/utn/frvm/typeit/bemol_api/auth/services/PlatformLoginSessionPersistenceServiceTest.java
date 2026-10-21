package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.jwtProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformRefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformLoginSessionPersistenceServiceTest {

  @Mock private PlatformSessionRepository platformSessionRepository;
  @Mock private PlatformRefreshTokenRepository platformRefreshTokenRepository;

  private PlatformLoginSessionPersistenceService service;

  @BeforeEach
  void setUp() {
    service =
        new PlatformLoginSessionPersistenceService(
            Clock.systemUTC(),
            platformSessionRepository,
            platformRefreshTokenRepository,
            jwtProperties(),
            new RefreshTokenGenerator());
  }

  @Test
  void createPersistsPlatformSessionAndRefreshToken() {
    final UUID platformAccountId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    when(platformSessionRepository.save(any(PlatformSession.class)))
        .thenAnswer(
            invocation -> {
              final PlatformSession session = invocation.getArgument(0);
              return PlatformSession.builder()
                  .id(sessionId)
                  .platformAccountId(session.getPlatformAccountId())
                  .ipAddress(session.getIpAddress())
                  .userAgent(session.getUserAgent())
                  .rememberMe(session.isRememberMe())
                  .build();
            });

    final PlatformLoginSessionPersistenceService.Result result =
        service.create(platformAccountId, "192.0.2.2", "JUnit", false);

    final ArgumentCaptor<PlatformSession> sessionCaptor =
        ArgumentCaptor.forClass(PlatformSession.class);
    final ArgumentCaptor<PlatformRefreshToken> tokenCaptor =
        ArgumentCaptor.forClass(PlatformRefreshToken.class);
    org.mockito.Mockito.verify(platformSessionRepository).save(sessionCaptor.capture());
    org.mockito.Mockito.verify(platformRefreshTokenRepository).save(tokenCaptor.capture());

    assertThat(sessionCaptor.getValue().getPlatformAccountId()).isEqualTo(platformAccountId);
    assertThat(tokenCaptor.getValue().getPlatformSessionId()).isEqualTo(sessionId);
    assertThat(tokenCaptor.getValue().getPlatformAccountId()).isEqualTo(platformAccountId);
    assertThat(tokenCaptor.getValue().getTokenHash())
        .isEqualTo(JwtService.hashToken(result.refreshToken()));
  }
}
