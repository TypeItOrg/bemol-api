package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {
  @Spy private Clock clock = Clock.systemUTC();

  @Mock private UserRepository userRepository;
  @Mock private UserSessionRepository userSessionRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PlatformSessionRepository platformSessionRepository;
  @Mock private PlatformRefreshTokenRepository platformRefreshTokenRepository;
  @Mock private org.springframework.cache.CacheManager cacheManager;

  @Captor private ArgumentCaptor<List<UUID>> sessionIdsCaptor;
  @Captor private ArgumentCaptor<Instant> endedAtCaptor;

  @InjectMocks private SessionRevocationService sessionRevocationService;

  @Test
  @DisplayName("Should deactivate institutional sessions when revoking by person")
  void revokeInstitutionalSessionsForPerson_closesActiveSessions() {
    UUID personId = UUID.randomUUID();
    UUID institutionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UserSession session =
        UserSession.builder()
            .id(sessionId)
            .userId(userId)
            .ipAddress("127.0.0.1")
            .userAgent("JUnit")
            .build();

    whenUserFound(personId, institutionId, userId);
    when(userSessionRepository.findByUserIdAndActive(userId, true)).thenReturn(List.of(session));

    sessionRevocationService.revokeInstitutionalSessionsForPerson(personId, institutionId);

    verify(refreshTokenRepository).revokeBySessionIds(sessionIdsCaptor.capture());
    assertThat(sessionIdsCaptor.getValue()).containsExactly(sessionId);
    verify(userSessionRepository)
        .deactivateByIds(sessionIdsCaptor.capture(), endedAtCaptor.capture());
    assertThat(sessionIdsCaptor.getValue()).containsExactly(sessionId);
    assertThat(endedAtCaptor.getValue()).isNotNull();
  }

  @Test
  @DisplayName("Should deactivate every active session from an institution")
  void revokeInstitutionalSessionsForInstitution_closesActiveSessions() {
    final UUID institutionId = UUID.randomUUID();
    final UUID firstSessionId = UUID.randomUUID();
    final UUID secondSessionId = UUID.randomUUID();
    final UserSession first = session(firstSessionId);
    final UserSession second = session(secondSessionId);
    when(userSessionRepository.findActiveByInstitutionId(institutionId))
        .thenReturn(List.of(first, second));

    sessionRevocationService.revokeInstitutionalSessionsForInstitution(institutionId);

    verify(refreshTokenRepository).revokeBySessionIds(sessionIdsCaptor.capture());
    assertThat(sessionIdsCaptor.getValue()).containsExactly(firstSessionId, secondSessionId);
    verify(userSessionRepository)
        .deactivateByIds(sessionIdsCaptor.capture(), endedAtCaptor.capture());
  }

  private static UserSession session(final UUID sessionId) {
    return UserSession.builder()
        .id(sessionId)
        .userId(UUID.randomUUID())
        .ipAddress("127.0.0.1")
        .userAgent("JUnit")
        .build();
  }

  private void whenUserFound(UUID personId, UUID institutionId, UUID userId) {
    when(userRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(
            Optional.of(
                ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User.builder().id(userId).build()));
  }
}
