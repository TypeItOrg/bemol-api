package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

  @Mock private AccessTokenRevocationService accessTokenRevocationService;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserSessionRepository userSessionRepository;

  private LogoutUseCase logoutUseCase;

  @BeforeEach
  void setUp() {
    logoutUseCase =
        new LogoutUseCase(
            Clock.systemUTC(),
            accessTokenRevocationService,
            refreshTokenRepository,
            userSessionRepository);
  }

  @Test
  @DisplayName("Should blacklist the jti, revoke refresh tokens, and close the session")
  void execute_blacklistsTokenAndClosesSession() {
    UUID sessionId = UUID.randomUUID();
    var principal = institutionalPrincipal(UUID.randomUUID(), UUID.randomUUID(), sessionId);
    UserSession session = activeSession(sessionId);

    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    logoutUseCase.execute(principal, "access-token");

    verify(accessTokenRevocationService).revoke("access-token");

    verify(refreshTokenRepository).revokeBySessionId(sessionId);
    assertThat(session.isActive()).isFalse();
    assertThat(session.getEndedAt()).isNotNull();
    verify(userSessionRepository).save(session);
  }

  @Test
  @DisplayName("Should close the session even when the access token is already expired")
  void execute_closesSessionWhenTokenIsExpired() {
    UUID sessionId = UUID.randomUUID();
    var principal = institutionalPrincipal(UUID.randomUUID(), UUID.randomUUID(), sessionId);
    UserSession session = activeSession(sessionId);

    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    logoutUseCase.execute(principal, "expired-token");

    verify(accessTokenRevocationService).revoke("expired-token");
    verify(refreshTokenRepository).revokeBySessionId(sessionId);
    assertThat(session.isActive()).isFalse();
  }

  @Test
  @DisplayName("Should close the session even when the access token is invalid")
  void execute_closesSessionWhenTokenIsInvalid() {
    UUID sessionId = UUID.randomUUID();
    var principal = institutionalPrincipal(UUID.randomUUID(), UUID.randomUUID(), sessionId);
    UserSession session = activeSession(sessionId);

    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    logoutUseCase.execute(principal, "garbage");

    verify(accessTokenRevocationService).revoke("garbage");
    verify(refreshTokenRepository).revokeBySessionId(sessionId);
    assertThat(session.isActive()).isFalse();
  }

  @Test
  @DisplayName("Should revoke refresh tokens even when the session record is not found")
  void execute_handlesAlreadyClosedSessionGracefully() {
    UUID sessionId = UUID.randomUUID();
    var principal = institutionalPrincipal(UUID.randomUUID(), UUID.randomUUID(), sessionId);

    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

    logoutUseCase.execute(principal, "access-token");

    verify(refreshTokenRepository).revokeBySessionId(sessionId);
    verify(userSessionRepository, never()).save(any());
  }

  private static UserSession activeSession(UUID sessionId) {
    return UserSession.builder()
        .id(sessionId)
        .userId(UUID.randomUUID())
        .ipAddress("192.0.2.1")
        .userAgent("JUnit")
        .build();
  }
}
