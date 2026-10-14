package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.PasswordLoginRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.TokenResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserPayload;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

@ExtendWith(MockitoExtension.class)
class PasswordLoginWithAttemptUseCaseTest {

  @Mock private LoginAttemptService loginAttemptService;
  @Mock private UserRepository userRepository;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private AuthenticationSessionIssuer sessionIssuer;
  @Mock private HttpServletRequest httpRequest;

  private PasswordLoginWithAttemptUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new PasswordLoginWithAttemptUseCase(
            loginAttemptService,
            userRepository,
            new CredentialsAuthenticator(authenticationManager),
            sessionIssuer);
  }

  @Test
  @DisplayName("Verifies the institutional credentials before issuing a session for that account")
  void execute_authenticatesTheExactAccountBeforeSessionIssuance() {
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    final LoginAttempt attempt =
        new LoginAttempt(
            "attempt", user.getId(), institutionId, false, Instant.parse("2026-10-08T12:00:00Z"));
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(userRepository.findWithPersonAndInstitutionById(user.getId()))
        .thenReturn(Optional.of(user));
    when(authenticationManager.authenticate(any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                user, "secret", user.getAuthorities()));
    when(sessionIssuer.issuePassword(eq(attempt), eq(user), eq("127.0.0.1"), eq("JUnit"), eq(true)))
        .thenReturn(
            AuthResponse.builder()
                .user(UserPayload.from(user, user.getPerson().getId(), Set.of()))
                .tokens(
                    TokenResponse.builder().accessToken("access").refreshToken("refresh").build())
                .build());

    final AuthResponse response =
        useCase.execute(new PasswordLoginRequest("attempt", "secret", true), httpRequest);

    assertThat(response.tokens().accessToken()).isEqualTo("access");
    assertThat(response.tokens().refreshToken()).isEqualTo("refresh");
    assertThat(response.user().userId()).isEqualTo(user.getId());
    final InOrder order = inOrder(loginAttemptService, authenticationManager, sessionIssuer);
    order.verify(loginAttemptService).resolve("attempt");
    ArgumentCaptor<Authentication> credentials = ArgumentCaptor.forClass(Authentication.class);
    order.verify(authenticationManager).authenticate(credentials.capture());
    assertThat(credentials.getValue().getPrincipal()).isEqualTo(institutionId + ":12345678");
    assertThat(credentials.getValue().getCredentials()).isEqualTo("secret");
    assertThat(credentials.getValue().isAuthenticated()).isFalse();
    order.verify(sessionIssuer).issuePassword(attempt, user, "127.0.0.1", "JUnit", true);
  }

  @Test
  @DisplayName("Should reject login when the attempt institution no longer matches")
  void execute_rejectsInstitutionMismatch() {
    final User user = userWith(UUID.randomUUID());
    final LoginAttempt attempt =
        new LoginAttempt(
            "attempt",
            user.getId(),
            UUID.randomUUID(),
            false,
            Instant.parse("2026-10-08T12:00:00Z"));
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(userRepository.findWithPersonAndInstitutionById(user.getId()))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                useCase.execute(new PasswordLoginRequest("attempt", "secret", false), httpRequest))
        .isInstanceOf(InvalidLoginAttemptException.class);
    verify(loginAttemptService).invalidate("attempt");
    verifyNoInteractions(authenticationManager, sessionIssuer);
  }

  static Stream<Arguments> credentialFailures() {
    return Stream.of(
        Arguments.of("bad password", new BadCredentialsException("bad password")),
        Arguments.of("disabled account", new DisabledException("disabled account")));
  }

  @ParameterizedTest(name = "Rejects {0} without issuing a session")
  @MethodSource("credentialFailures")
  @DisplayName("Credential failures have the same public error and cannot issue a session")
  void execute_propagatesBadPassword(
      String failureKind, AuthenticationException authenticationFailure) {
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    final LoginAttempt attempt =
        new LoginAttempt(
            "attempt", user.getId(), institutionId, false, Instant.parse("2026-10-08T12:00:00Z"));
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(userRepository.findWithPersonAndInstitutionById(user.getId()))
        .thenReturn(Optional.of(user));
    when(authenticationManager.authenticate(any())).thenThrow(authenticationFailure);

    assertThatThrownBy(
            () -> useCase.execute(new PasswordLoginRequest("attempt", "wrong", false), httpRequest))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginAttemptService, never()).claim(any());
    verify(sessionIssuer, never()).issuePassword(any(), any(), any(), any(), any(boolean.class));
  }

  @Test
  @DisplayName("Should reject login without claiming when the account is disabled")
  void execute_rejectsDisabledAccountWithoutClaiming() {
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    user.updateAccess(false);
    final LoginAttempt attempt =
        new LoginAttempt(
            "attempt", user.getId(), institutionId, false, Instant.parse("2026-10-08T12:00:00Z"));
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(userRepository.findWithPersonAndInstitutionById(user.getId()))
        .thenReturn(Optional.of(user));
    when(authenticationManager.authenticate(any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                user, "secret", user.getAuthorities()));

    assertThatThrownBy(
            () ->
                useCase.execute(new PasswordLoginRequest("attempt", "secret", false), httpRequest))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginAttemptService, never()).claim(any());
    verify(sessionIssuer, never()).issuePassword(any(), any(), any(), any(), any(boolean.class));
  }

  @Test
  @DisplayName("Should reject login without claiming when the institution is inactive")
  void execute_rejectsInactiveInstitutionWithoutClaiming() {
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    user.getInstitution().updateStatus(false);
    final LoginAttempt attempt =
        new LoginAttempt(
            "attempt", user.getId(), institutionId, false, Instant.parse("2026-10-08T12:00:00Z"));
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(userRepository.findWithPersonAndInstitutionById(user.getId()))
        .thenReturn(Optional.of(user));
    when(authenticationManager.authenticate(any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                user, "secret", user.getAuthorities()));

    assertThatThrownBy(
            () ->
                useCase.execute(new PasswordLoginRequest("attempt", "secret", false), httpRequest))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(loginAttemptService, never()).claim(any());
    verify(sessionIssuer, never()).issuePassword(any(), any(), any(), any(), any(boolean.class));
  }

  private static User userWith(final UUID institutionId) {
    final Institution institution = Institution.builder().id(institutionId).build();
    return User.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .person(
            Person.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .documentNumber("12345678")
                .build())
        .password("hash")
        .build();
  }
}
