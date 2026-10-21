package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.jwtProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.JwtProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.RefreshToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidRefreshTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.RefreshTokenReuseException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RefreshTokenRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {
  @Mock private InstitutionalHostContext hostContext;

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserSessionRepository userSessionRepository;
  @Mock private UserRepository userRepository;
  @Mock private JwtService jwtService;
  @Mock private AuthorityResolver authorityResolver;
  @Mock private RefreshReplayCache replayCache;
  private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();
  @Mock private SessionRevocationService sessionRevocationService;

  private RefreshTokenUseCase refreshTokenUseCase;
  private JwtProperties jwtProperties;

  @BeforeEach
  void setUp() {
    jwtProperties = jwtProperties();
    refreshTokenUseCase =
        new RefreshTokenUseCase(
            hostContext,
            Clock.systemUTC(),
            refreshTokenRepository,
            userSessionRepository,
            userRepository,
            jwtService,
            jwtProperties,
            authorityResolver,
            replayCache,
            refreshTokenGenerator,
            sessionRevocationService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pendingEmailCannotRotateOrReplayRefresh(final boolean replay) {
    final String raw = "pending-refresh";
    final String hash = JwtService.hashToken(raw);
    final UUID sessionId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final User existing = userWith(userId);
    final User pending =
        User.builder()
            .id(userId)
            .institution(existing.getInstitution())
            .person(existing.getPerson())
            .emailVerificationStatus(EmailVerificationStatus.PENDING)
            .build();
    final RefreshToken token = activeToken(hash, "pending-family", sessionId);
    if (replay) {
      token.revoke();
      when(replayCache.get(AuthRealm.INSTITUTIONAL, hash))
          .thenReturn(Optional.of(new RefreshReplay("access", "refresh")));
    }
    when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
    when(userSessionRepository.findById(sessionId))
        .thenReturn(Optional.of(activeSession(sessionId, userId, false)));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(pending));
    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(raw)))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(refreshTokenRepository, never()).save(any());
    verify(jwtService, never()).generateAccessToken(any(InstitutionalAccessTokenInput.class));
  }

  @Test
  @DisplayName("Should rotate refresh token and return new auth response")
  void execute_rotatesTokenSuccessfully() {
    String rawToken = "raw-refresh-token";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    RefreshToken current = activeToken(tokenHash, "family-1", sessionId);
    UserSession session = activeSession(sessionId, userId, false);
    User user = userWith(userId);

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(current));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    when(jwtService.generateAccessToken(any(InstitutionalAccessTokenInput.class)))
        .thenReturn("new-access-token");

    AuthResponse response = refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken));

    assertThat(response.tokens().accessToken()).isEqualTo("new-access-token");
    assertThat(response.tokens().refreshToken()).isNotBlank();
    assertThat(response.tokens().refreshToken()).isNotEqualTo(rawToken);
    assertThat(current.isRevoked()).isTrue();

    var newTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, times(2)).save(newTokenCaptor.capture());
    RefreshToken saved = savedNewToken(newTokenCaptor, current);
    assertThat(saved.getFamilyId()).isEqualTo("family-1");
    assertThat(saved.getSessionId()).isEqualTo(sessionId);
    assertThat(saved.isRevoked()).isFalse();
    assertThat(saved.getTokenHash())
        .isEqualTo(JwtService.hashToken(response.tokens().refreshToken()));
    verify(replayCache)
        .put(
            AuthRealm.INSTITUTIONAL,
            tokenHash,
            new RefreshReplay("new-access-token", response.tokens().refreshToken()));
  }

  @Test
  @DisplayName("Should replay rotated tokens for concurrent institutional refreshes")
  void execute_replaysConcurrentRefresh() {
    String rawToken = "concurrent-institutional-token";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken current = activeToken(tokenHash, "family-1", sessionId);
    current.revoke();
    UserSession session = activeSession(sessionId, userId, false);
    User user = userWith(userId);
    RefreshReplay replay = new RefreshReplay("replayed-access-token", "replayed-refresh-token");

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(current));
    when(replayCache.get(AuthRealm.INSTITUTIONAL, tokenHash)).thenReturn(Optional.of(replay));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));

    AuthResponse response = refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken));

    assertThat(response.tokens().accessToken()).isEqualTo(replay.accessToken());
    assertThat(response.tokens().refreshToken()).isEqualTo(replay.refreshToken());
    verify(refreshTokenRepository, never()).revokeByFamilyId("family-1");
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw when the refresh token hash is not found")
  void execute_throwsWhenTokenHashNotFound() {
    String rawToken = "unknown-token";
    when(refreshTokenRepository.findByTokenHash(JwtService.hashToken(rawToken)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  @DisplayName("Should revoke entire family and all sessions on token reuse detection")
  void execute_revokesEntireFamilyAndSessionsOnTokenReuse() {
    String rawToken = "already-revoked-token";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();

    RefreshToken revokedToken = activeToken(tokenHash, "family-1", sessionId);
    revokedToken.revoke();

    RefreshToken sibling = activeToken(JwtService.hashToken("sibling"), "family-1", sessionId);

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));
    when(refreshTokenRepository.findByFamilyId("family-1"))
        .thenReturn(List.of(revokedToken, sibling));

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(RefreshTokenReuseException.class)
        .hasMessageContaining("reutilización");

    verify(refreshTokenRepository).revokeByFamilyId("family-1");
    verify(sessionRevocationService)
        .revokeInstitutionalSessionsByIds(
            argThat(ids -> ids.size() == 1 && ids.contains(sessionId)));
  }

  @Test
  @DisplayName("Should throw when the refresh token is expired")
  void execute_throwsWhenTokenIsExpired() {
    String rawToken = "expired-token";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();

    RefreshToken expired =
        RefreshToken.builder()
            .sessionId(sessionId)
            .tokenHash(tokenHash)
            .familyId("family-1")
            .expiresAt(Instant.now().minus(Duration.ofDays(1)))
            .build();

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(userSessionRepository, never()).findById(any());
  }

  @Test
  @DisplayName("Should throw when the associated session is not active")
  void execute_throwsWhenSessionIsInactive() {
    String rawToken = "valid-token-inactive-session";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    RefreshToken token = activeToken(tokenHash, "family-1", sessionId);
    UserSession inactiveSession = activeSession(sessionId, userId, false);
    inactiveSession.end(Instant.now());

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(inactiveSession));

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(userRepository, never()).findWithPersonAndInstitutionById(any());
  }

  @Test
  @DisplayName("Should throw when the user associated to the session is not found")
  void execute_throwsWhenUserNotFound() {
    String rawToken = "valid-token-missing-user";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    RefreshToken token = activeToken(tokenHash, "family-1", sessionId);
    UserSession session = activeSession(sessionId, userId, false);

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  @DisplayName("Should reject refresh when the institution is inactive")
  void execute_throwsWhenInstitutionIsInactive() {
    final String rawToken = "inactive-institution-token";
    final String tokenHash = JwtService.hashToken(rawToken);
    final UUID sessionId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final RefreshToken token = activeToken(tokenHash, "family-1", sessionId);
    final UserSession session = activeSession(sessionId, userId, false);
    final User user = userWith(userId);
    user.getInstitution().updateStatus(false);
    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should use extended expiration for new token when session has rememberMe")
  void execute_usesRememberMeExpirationWhenSessionHasRememberMe() {
    String rawToken = "remember-me-token";
    String tokenHash = JwtService.hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    RefreshToken current = activeToken(tokenHash, "family-1", sessionId);
    UserSession session = activeSession(sessionId, userId, true);
    User user = userWith(userId);

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(current));
    when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    when(jwtService.generateAccessToken(any(InstitutionalAccessTokenInput.class)))
        .thenReturn("access");

    refreshTokenUseCase.execute(new RefreshTokenRequest(rawToken));

    var captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, times(2)).save(captor.capture());
    RefreshToken newToken = savedNewToken(captor, current);

    Instant expectedExpiry = Instant.now().plus(jwtProperties.rememberMeTokenExpiration());
    assertThat(newToken.getExpiresAt()).isCloseTo(expectedExpiry, within(5, ChronoUnit.SECONDS));
  }

  private static RefreshToken savedNewToken(
      ArgumentCaptor<RefreshToken> captor, RefreshToken current) {
    return captor.getAllValues().stream().filter(t -> t != current).findFirst().orElseThrow();
  }

  private static RefreshToken activeToken(String hash, String familyId, UUID sessionId) {
    return RefreshToken.builder()
        .sessionId(sessionId)
        .tokenHash(hash)
        .familyId(familyId)
        .expiresAt(Instant.now().plus(Duration.ofDays(7)))
        .build();
  }

  private static UserSession activeSession(UUID id, UUID userId, boolean rememberMe) {
    return UserSession.builder()
        .id(id)
        .userId(userId)
        .ipAddress("192.0.2.1")
        .userAgent("JUnit")
        .rememberMe(rememberMe)
        .build();
  }

  private static User userWith(UUID userId) {
    return User.builder()
        .id(userId)
        .institution(Institution.builder().id(UUID.randomUUID()).build())
        .person(Person.builder().documentNumber("12345678").build())
        .password("encoded")
        .build();
  }
}
