package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnCeremonyInvalidException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.AuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import com.webauthn4j.verifier.exception.BadSignatureException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class VerifyPasskeyAuthenticationOptionsTest {

  @Mock private LoginAttemptService loginAttemptService;
  @Mock private UserRepository userRepository;
  @Mock private PasskeyCredentialRepository passkeyCredentialRepository;

  @Mock private WebAuthnRelyingPartyOperations relyingPartyOperations;

  @Mock private WebAuthnCeremonyService ceremonyService;
  @Mock private AuthenticationSessionIssuer sessionIssuer;
  @Mock private HttpServletRequest httpRequest;
  @Mock private WebAuthnOptionsCodec codec;

  @Test
  @DisplayName("Should keep a 401 ceremony error when stored options are corrupt")
  void execute_mapsCorruptOptionsToCeremonyInvalid() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final LoginAttempt attempt =
        new LoginAttempt("attempt", userId, institutionId, true, Instant.now());
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    final User expected = Mockito.mock(User.class);
    when(expected.getInstitutionId()).thenReturn(attempt.institutionId());
    when(userRepository.findWithPersonAndInstitutionById(attempt.userId()))
        .thenReturn(Optional.of(expected));
    when(codec.decodeRequestOptions("not-json")).thenThrow(new IllegalStateException("unreadable"));
    when(ceremonyService.consumeAuthentication("ceremony"))
        .thenReturn(
            Optional.of(
                new AuthenticationCeremony(
                    "ceremony", "attempt", userId, "not-json", Instant.now())));
    final VerifyPasskeyAuthenticationUseCase useCase =
        new VerifyPasskeyAuthenticationUseCase(
            loginAttemptService,
            userRepository,
            passkeyCredentialRepository,
            relyingPartyOperations,
            ceremonyService,
            sessionIssuer,
            codec);

    final JsonNode credential =
        JsonMapper.builder().build().createObjectNode().put("id", "credential-id");

    assertThatThrownBy(() -> useCase.execute("attempt", "ceremony", credential, false, httpRequest))
        .isInstanceOf(WebAuthnCeremonyInvalidException.class);
    verify(loginAttemptService, never()).claim(anyString());
  }

  @Test
  @DisplayName("Should keep a 401 error for known verification failures")
  void execute_mapsVerificationFailureToUnauthorized() {
    stubAuthentication();
    when(relyingPartyOperations.authenticate(any()))
        .thenThrow(new BadSignatureException("bad signature"));

    assertThatThrownBy(
            () -> useCase().execute("attempt", "ceremony", credentialNode(), false, httpRequest))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
  }

  @Test
  @DisplayName("Should propagate infrastructure failures as server errors")
  void execute_propagatesInfrastructureFailure() {
    stubAuthentication();
    when(relyingPartyOperations.authenticate(any()))
        .thenThrow(new DataAccessResourceFailureException("database down"));

    assertThatThrownBy(
            () -> useCase().execute("attempt", "ceremony", credentialNode(), false, httpRequest))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  private VerifyPasskeyAuthenticationUseCase useCase() {
    return new VerifyPasskeyAuthenticationUseCase(
        loginAttemptService,
        userRepository,
        passkeyCredentialRepository,
        relyingPartyOperations,
        ceremonyService,
        sessionIssuer,
        codec);
  }

  private void stubAuthentication() {
    final UUID userId = UUID.randomUUID();
    final LoginAttempt attempt =
        new LoginAttempt("attempt", userId, UUID.randomUUID(), true, Instant.now());
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    final User expected = Mockito.mock(User.class);
    when(expected.getInstitutionId()).thenReturn(attempt.institutionId());
    when(userRepository.findWithPersonAndInstitutionById(attempt.userId()))
        .thenReturn(Optional.of(expected));
    when(ceremonyService.consumeAuthentication("ceremony"))
        .thenReturn(
            Optional.of(
                new AuthenticationCeremony("ceremony", "attempt", userId, "{}", Instant.now())));
    when(codec.decodeRequestOptions("{}")).thenReturn(requestOptions());
    when(codec.decodeAssertionCredential(any())).thenReturn(assertionCredential());
  }

  @Test
  @DisplayName("Should issue the session without a second credential write")
  void execute_issuesSessionWithoutSecondCredentialWrite() {
    final UUID userId = UUID.randomUUID();
    final UUID institutionId = UUID.randomUUID();
    final Institution institution = Institution.builder().id(institutionId).build();
    final User user =
        User.builder()
            .id(userId)
            .institution(institution)
            .person(
                Person.builder()
                    .id(UUID.randomUUID())
                    .institution(institution)
                    .documentNumber("12345678")
                    .build())
            .password("hash")
            .build();
    final byte[] handle = user.ensureWebAuthnUserHandle(new SecureRandom());
    final LoginAttempt attempt =
        new LoginAttempt("attempt", userId, institutionId, true, Instant.now());
    when(loginAttemptService.resolve("attempt")).thenReturn(attempt);
    when(ceremonyService.consumeAuthentication("ceremony"))
        .thenReturn(
            Optional.of(
                new AuthenticationCeremony("ceremony", "attempt", userId, "{}", Instant.now())));
    when(codec.decodeRequestOptions("{}")).thenReturn(requestOptions());
    when(codec.decodeAssertionCredential(any())).thenReturn(assertionCredential());
    when(relyingPartyOperations.authenticate(any()))
        .thenReturn(
            ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(handle))
                .name("user")
                .displayName("User")
                .build());
    when(userRepository.findWithPersonAndInstitutionById(userId)).thenReturn(Optional.of(user));
    final PasskeyCredential stored = Mockito.mock(PasskeyCredential.class);
    when(stored.isActive()).thenReturn(true);
    when(stored.getUser()).thenReturn(user);
    when(passkeyCredentialRepository.findByCredentialId(any())).thenReturn(Optional.of(stored));
    when(loginAttemptService.claim("attempt")).thenReturn(attempt);
    final AuthResponse response = Mockito.mock(AuthResponse.class);
    when(sessionIssuer.issue(any(), any(), any(), any(boolean.class), any())).thenReturn(response);

    assertThat(useCase().execute("attempt", "ceremony", credentialNode(), false, httpRequest))
        .isSameAs(response);
    verify(sessionIssuer).issue(any(), any(), any(), any(boolean.class), any());
    verify(passkeyCredentialRepository, never()).save(any());
  }

  private static PublicKeyCredentialRequestOptions requestOptions() {
    return PublicKeyCredentialRequestOptions.builder()
        .challenge(Bytes.random())
        .timeout(Duration.ofMinutes(5))
        .rpId("localhost")
        .allowCredentials(List.of())
        .userVerification(UserVerificationRequirement.REQUIRED)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static PublicKeyCredential<AuthenticatorAssertionResponse> assertionCredential() {
    return Mockito.mock(PublicKeyCredential.class);
  }

  private static JsonNode credentialNode() {
    return Mockito.mock(JsonNode.class);
  }
}
