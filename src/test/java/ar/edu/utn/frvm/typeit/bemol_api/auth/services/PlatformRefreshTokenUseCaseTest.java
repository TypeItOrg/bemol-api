package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.jwtProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformRefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidRefreshTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RefreshTokenReuseException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RefreshTokenRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformRefreshTokenUseCaseTest {

  @Mock private PlatformRefreshTokenRepository refreshTokenRepository;
  @Mock private PlatformSessionRepository sessionRepository;
  @Mock private PlatformAccountRepository accountRepository;
  @Mock private JwtService jwtService;
  @Mock private RefreshReplayCache replayCache;
  private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();
  @Mock private SessionRevocationService sessionRevocationService;

  private PlatformRefreshTokenUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new PlatformRefreshTokenUseCase(
            Clock.systemUTC(),
            refreshTokenRepository,
            sessionRepository,
            accountRepository,
            jwtService,
            jwtProperties(),
            replayCache,
            refreshTokenGenerator,
            sessionRevocationService);
  }

  @Test
  @DisplayName("Should revoke the platform token family and its sessions on reuse")
  void execute_revokesFamilyAndSessionsOnReuse() {
    final String rawToken = "reused-platform-token";
    final String hash = JwtService.hashToken(rawToken);
    final UUID sessionId = UUID.randomUUID();
    final PlatformRefreshToken token = token(hash, "family-1", sessionId, UUID.randomUUID());
    token.revoke();
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(refreshTokenRepository.findByFamilyId("family-1")).thenReturn(List.of(token));

    assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(RefreshTokenReuseException.class);

    verify(refreshTokenRepository).revokeByFamilyId("family-1");
    verify(sessionRevocationService)
        .revokePlatformSessionsByIds(argThat(ids -> ids.size() == 1 && ids.contains(sessionId)));
  }

  @Test
  @DisplayName("Should reject refresh for a disabled platform account")
  void execute_rejectsDisabledAccount() {
    final String rawToken = "disabled-platform-account";
    final String hash = JwtService.hashToken(rawToken);
    final UUID sessionId = UUID.randomUUID();
    final UUID accountId = UUID.randomUUID();
    final PlatformRefreshToken token = token(hash, "family-1", sessionId, accountId);
    final PlatformSession session =
        PlatformSession.builder().id(sessionId).platformAccountId(accountId).active(true).build();
    final PlatformAccount account =
        PlatformAccount.builder()
            .id(accountId)
            .email("disabled@example.com")
            .name("Disabled")
            .lastName("Account")
            .password("encoded")
            .enabled(false)
            .build();
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should replay the rotated tokens for concurrent refreshes")
  void execute_replaysConcurrentRefresh() {
    final String rawToken = "concurrent-platform-token";
    final String hash = JwtService.hashToken(rawToken);
    final UUID sessionId = UUID.randomUUID();
    final UUID accountId = UUID.randomUUID();
    final PlatformRefreshToken token = token(hash, "family-1", sessionId, accountId);
    token.revoke();
    final PlatformSession session =
        PlatformSession.builder().id(sessionId).platformAccountId(accountId).active(true).build();
    final PlatformAccount account =
        PlatformAccount.builder()
            .id(accountId)
            .email("admin@example.com")
            .name("Admin")
            .lastName("Platform")
            .password("encoded")
            .enabled(true)
            .build();
    final RefreshReplay replay =
        new RefreshReplay("replayed-access-token", "replayed-refresh-token");
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(replayCache.get(AuthRealm.PLATFORM, hash)).thenReturn(Optional.of(replay));
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    final var response = useCase.execute(new RefreshTokenRequest(rawToken));

    assertThat(response.tokens().accessToken()).isEqualTo(replay.accessToken());
    assertThat(response.tokens().refreshToken()).isEqualTo(replay.refreshToken());
    verify(refreshTokenRepository, never()).revokeByFamilyId("family-1");
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should cache the result of a successful platform refresh")
  void execute_cachesSuccessfulRefresh() {
    final String rawToken = "successful-platform-token";
    final String hash = JwtService.hashToken(rawToken);
    final UUID sessionId = UUID.randomUUID();
    final UUID accountId = UUID.randomUUID();
    final PlatformRefreshToken token = token(hash, "family-1", sessionId, accountId);
    final PlatformSession session =
        PlatformSession.builder().id(sessionId).platformAccountId(accountId).active(true).build();
    final PlatformAccount account =
        PlatformAccount.builder()
            .id(accountId)
            .email("admin@example.com")
            .name("Admin")
            .lastName("Platform")
            .password("encoded")
            .enabled(true)
            .build();
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(jwtService.generatePlatformAccessToken(any())).thenReturn("access-token");

    final var response = useCase.execute(new RefreshTokenRequest(rawToken));

    assertThat(response.tokens().accessToken()).isEqualTo("access-token");
    assertThat(response.tokens().refreshToken()).isNotBlank();
    verify(replayCache)
        .put(
            AuthRealm.PLATFORM,
            hash,
            new RefreshReplay("access-token", response.tokens().refreshToken()));
  }

  private static PlatformRefreshToken token(
      final String hash, final String familyId, final UUID sessionId, final UUID accountId) {
    return PlatformRefreshToken.builder()
        .platformSessionId(sessionId)
        .platformAccountId(accountId)
        .tokenHash(hash)
        .familyId(familyId)
        .expiresAt(Instant.now().plus(Duration.ofDays(7)))
        .build();
  }
}
