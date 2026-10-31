package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PlatformAuthenticationPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PlatformLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformLoginChallengeResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class PlatformLoginUseCaseTest {
  private final CredentialsAuthenticator authenticator = mock(CredentialsAuthenticator.class);
  private final PlatformPasskeyStateStore state = mock(PlatformPasskeyStateStore.class);
  private final PlatformPasskeyCredentialRepository credentials =
      mock(PlatformPasskeyCredentialRepository.class);
  private final PlatformAuthenticationPolicy policy = mock(PlatformAuthenticationPolicy.class);
  private final PlatformAuthenticationSessionIssuer sessionIssuer =
      mock(PlatformAuthenticationSessionIssuer.class);
  private final PlatformAccount account =
      PlatformAccount.builder()
          .id(UUID.randomUUID())
          .email("admin@example.test")
          .enabled(true)
          .build();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiredPasskeysOnlyReturnAnAttemptWithoutTokens(final boolean hasPasskey) {
    when(authenticator.authenticate(any(), any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(account, null, java.util.List.of()));
    when(policy.passkeyRequired()).thenReturn(true);
    when(credentials.existsActiveByPlatformAccountId(account.getId())).thenReturn(hasPasskey);
    when(state.createAttempt(account, false)).thenReturn("attempt");
    final var response =
        (PlatformLoginChallengeResponse)
            new PlatformLoginUseCase(authenticator, state, credentials, policy, sessionIssuer)
                .execute(
                    new PlatformLoginRequest("admin@example.test", "password", false),
                    new MockHttpServletRequest());
    assertThat(response.loginAttemptId()).isEqualTo("attempt");
    assertThat(response.setupRequired()).isEqualTo(!hasPasskey);
    verify(state).createAttempt(account, false);
    verifyNoInteractions(sessionIssuer);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void passwordOnlyUsesTheSameSessionIssuerWithRequestMetadata(final boolean rememberMe) {
    when(authenticator.authenticate(any(), any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(account, null, java.util.List.of()));
    final var sessions = mock(PlatformLoginSessionPersistenceService.class);
    final var jwt = mock(JwtService.class);
    final UUID sessionId = UUID.randomUUID();
    when(sessions.create(account.getId(), "127.0.0.1", "test-browser", rememberMe))
        .thenReturn(new PlatformLoginSessionPersistenceService.Result(sessionId, "refresh"));
    when(jwt.generatePlatformAccessToken(any())).thenReturn("access");
    final var request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("User-Agent", "test-browser");

    final var response =
        (PlatformAuthResponse)
            new PlatformLoginUseCase(
                    authenticator,
                    state,
                    credentials,
                    policy,
                    new PlatformAuthenticationSessionIssuer(sessions, jwt))
                .execute(
                    new PlatformLoginRequest("admin@example.test", "password", rememberMe),
                    request);

    assertThat(response.account().platformAccountId()).isEqualTo(account.getId());
    assertThat(response.tokens().accessToken()).isEqualTo("access");
    assertThat(response.tokens().refreshToken()).isEqualTo("refresh");
    verify(authenticator).authenticate("platform:admin@example.test", "password");
    verify(sessions).create(account.getId(), "127.0.0.1", "test-browser", rememberMe);
    verify(jwt)
        .generatePlatformAccessToken(
            argThat(
                input ->
                    input.sessionId().equals(sessionId)
                        && input.platformAccountId().equals(account.getId())));
    verifyNoInteractions(state, credentials);
  }

  @Test
  void passwordOnlyStillRejectsInvalidCredentials() {
    when(authenticator.authenticate(any(), any()))
        .thenThrow(new BadCredentialsException("invalid"));

    assertThatThrownBy(
            () ->
                new PlatformLoginUseCase(authenticator, state, credentials, policy, sessionIssuer)
                    .execute(
                        new PlatformLoginRequest("admin@example.test", "wrong", false),
                        new MockHttpServletRequest()))
        .isInstanceOf(BadCredentialsException.class);
    verifyNoInteractions(policy, state, credentials, sessionIssuer);
  }

  @Test
  void passwordOnlyDoesNotAcceptAnInstitutionalPrincipal() {
    when(authenticator.authenticate(any(), any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                "institutional", null, java.util.List.of()));

    assertThatThrownBy(
            () ->
                new PlatformLoginUseCase(authenticator, state, credentials, policy, sessionIssuer)
                    .execute(
                        new PlatformLoginRequest("admin@example.test", "password", false),
                        new MockHttpServletRequest()))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(policy, state, credentials, sessionIssuer);
  }
}
