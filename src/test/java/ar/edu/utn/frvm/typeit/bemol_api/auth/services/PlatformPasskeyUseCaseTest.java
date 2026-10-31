package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.*;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
class PlatformPasskeyUseCaseTest {
  @Mock private PlatformAccountRepository accounts;
  @Mock private PlatformPasskeyCredentialRepository credentials;
  @Mock private PlatformPasskeyCredentialMapper mapper;
  @Mock private PlatformWebAuthnOperations operations;
  @Mock private WebAuthnOptionsCodec codec;
  @Mock private PlatformPasskeyStateStore state;
  @Mock private WebAuthnProperties properties;
  @Mock private PlatformAuthenticationSessionIssuer sessionIssuer;
  @Mock private SessionRevocationService revocation;
  @Mock private AuditEventRecorder audit;
  @Mock private Clock clock;
  @InjectMocks private PlatformPasskeyUseCase service;
  @InjectMocks private PlatformPasskeyManagementUseCase management;

  private final UUID id = UUID.randomUUID();
  private final PlatformAccount account =
      PlatformAccount.builder().id(id).password("new-password-hash").enabled(true).build();

  @Test
  void verifiedPasskeyUsesTheSharedSessionIssuer() {
    final var ceremony =
        new PlatformPasskeyStateStore.Ceremony(id, null, "attempt", "login", "", "", "", "{}");
    final var request = new MockHttpServletRequest();
    final var credential = mock(JsonNode.class);
    final var response = PlatformAuthResponse.of(account, "access", "refresh");
    when(state.consumeCeremony("ceremony", "login")).thenReturn(ceremony);
    when(state.attempt("attempt"))
        .thenReturn(
            new PlatformPasskeyStateStore.Attempt(
                id, JwtService.hashToken(account.getPassword()), true));
    when(accounts.findWithLockById(id)).thenReturn(Optional.of(account));
    when(sessionIssuer.issue(account, true, request)).thenReturn(response);

    assertThat(service.authenticate("ceremony", credential, request)).isEqualTo(response);

    final var order = inOrder(operations, state, sessionIssuer);
    order.verify(operations).verifyAssertion(ceremony, account, credential);
    order.verify(state).claimAttempt("attempt");
    order.verify(sessionIssuer).issue(account, true, request);
  }

  @Test
  void passwordChangeInvalidatesAPendingLoginBeforeVerification() {
    final var ceremony =
        new PlatformPasskeyStateStore.Ceremony(id, null, "attempt", "login", "", "", "", "{}");
    when(state.consumeCeremony("ceremony", "login")).thenReturn(ceremony);
    when(state.attempt("attempt"))
        .thenReturn(
            new PlatformPasskeyStateStore.Attempt(id, JwtService.hashToken("old-hash"), false));
    when(accounts.findWithLockById(id)).thenReturn(Optional.of(account));
    assertThatThrownBy(
            () ->
                service.authenticate(
                    "ceremony", mock(JsonNode.class), new MockHttpServletRequest()))
        .isInstanceOf(InvalidLoginAttemptException.class);
    verifyNoInteractions(sessionIssuer, operations);
  }

  @Test
  void cannotRemoveTheLastKeyEvenWithAnAuthenticatedSession() {
    final var key =
        PlatformPasskeyCredential.builder().id(UUID.randomUUID()).platformAccount(account).build();
    when(accounts.findWithLockById(id)).thenReturn(Optional.of(account));
    when(credentials.findWithLockByIdAndPlatformAccountId(key.getId(), id))
        .thenReturn(Optional.of(key));
    when(credentials.countActiveByPlatformAccountId(id)).thenReturn(1L);
    assertThatThrownBy(() -> management.revoke(AuthTestData.platformPrincipal(id), key.getId()))
        .isInstanceOf(PlatformPasskeyRequiredException.class);
    verifyNoInteractions(state, revocation);
  }

  @Test
  void deletionConsumesActionBoundProofAndClosesAllSessions() {
    final var principal = AuthTestData.platformPrincipal(id);
    final var key =
        PlatformPasskeyCredential.builder().id(UUID.randomUUID()).platformAccount(account).build();
    when(accounts.findWithLockById(id)).thenReturn(Optional.of(account));
    when(credentials.findWithLockByIdAndPlatformAccountId(key.getId(), id))
        .thenReturn(Optional.of(key));
    when(credentials.countActiveByPlatformAccountId(id)).thenReturn(2L);
    when(clock.instant()).thenReturn(Instant.parse("2026-10-30T00:00:00Z"));
    management.revoke(principal, key.getId());
    verify(state).requireRecent(id, principal.sessionId(), "revoke:" + key.getId(), true);
    verify(revocation).revokePlatformAccountSessions(id);
    org.assertj.core.api.Assertions.assertThat(key.isActive()).isFalse();
  }

  @Test
  void registrationCannotBeMovedToAnotherSession() {
    final var principal = AuthTestData.platformPrincipal(id);
    when(state.consumeCeremony("ceremony", "registration"))
        .thenReturn(
            new PlatformPasskeyStateStore.Ceremony(
                id,
                UUID.randomUUID().toString(),
                null,
                "registration",
                "register",
                "",
                "Equipo",
                "{}"));
    assertThatThrownBy(() -> management.register(principal, "ceremony", mock(JsonNode.class)))
        .isInstanceOf(InvalidLoginAttemptException.class);
    verifyNoInteractions(accounts, sessionIssuer, operations);
  }
}
