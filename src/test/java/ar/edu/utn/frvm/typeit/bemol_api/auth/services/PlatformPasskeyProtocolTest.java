package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsModule;
import ar.edu.utn.frvm.typeit.bemol_api.support.PasskeyAuthenticator;
import com.webauthn4j.verifier.exception.VerificationException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.*;
import org.springframework.security.web.webauthn.management.*;

class PlatformPasskeyProtocolTest {
  @Test
  void registrationAndAuthenticationRequirePlatformOriginAndVerification() throws Exception {
    final var repository = mock(PlatformPasskeyRepositoryAdapter.class);
    final var frontend = new FrontendPublicProperties("http://localhost:3000", "localhost");
    final var codec =
        new WebAuthnOptionsCodec(
            tools.jackson.databind.json.JsonMapper.builder()
                .addModule(
                    new org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule())
                .addModule(new WebAuthnOptionsModule())
                .build());
    final var handle =
        new Bytes(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    final var user =
        ImmutablePublicKeyCredentialUserEntity.builder()
            .id(handle)
            .name("Admin")
            .displayName("Admin")
            .build();
    final var records = new HashMap<Bytes, CredentialRecord>();
    when(repository.findByUsername(any())).thenReturn(user);
    when(repository.findById(any())).thenReturn(user);
    when(repository.findByUserId(any())).thenAnswer(invocation -> List.copyOf(records.values()));
    when(repository.findByCredentialId(any()))
        .thenAnswer(invocation -> records.get(invocation.getArgument(0)));
    final var operations =
        new PlatformWebAuthnOperations(
                repository,
                frontend,
                mock(
                    ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces
                        .PlatformPasskeyCredentialRepository.class),
                mock(PlatformPasskeyCredentialMapper.class),
                codec,
                mock(PlatformPasskeyStateStore.class),
                mock(ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder.class))
            .get();
    final var principal =
        UsernamePasswordAuthenticationToken.authenticated("account", null, List.of());
    final var creation =
        operations.createPublicKeyCredentialCreationOptions(
            new ImmutablePublicKeyCredentialCreationOptionsRequest(principal));
    assertThat(creation.getRp().getId()).isEqualTo("admin.localhost");
    assertThat(creation.getAuthenticatorSelection().getUserVerification())
        .isEqualTo(UserVerificationRequirement.REQUIRED);
    final var authenticator = new PasskeyAuthenticator();
    final var registration =
        authenticator.register(
            codec.parseSnapshot(codec.encodeCreationOptions(creation)),
            frontend.platformPublicUrl());
    final var record =
        operations.registerCredential(
            new ImmutableRelyingPartyRegistrationRequest(
                creation,
                new RelyingPartyPublicKey(
                    codec.decodeAttestationCredential(registration), "Equipo")));
    records.put(record.getCredentialId(), record);
    final var assertion =
        operations.createCredentialRequestOptions(
            new ImmutablePublicKeyCredentialRequestOptionsRequest(principal));
    final var options = codec.parseSnapshot(codec.encodeRequestOptions(assertion));
    assertThat(assertion.getUserVerification()).isEqualTo(UserVerificationRequirement.REQUIRED);
    assertThat(
            operations
                .authenticate(
                    new RelyingPartyAuthenticationRequest(
                        assertion,
                        codec.decodeAssertionCredential(
                            authenticator.authenticate(
                                options, frontend.platformPublicUrl(), handle.getBytes(), true))))
                .getId())
        .isEqualTo(handle);
    assertThatThrownBy(
            () ->
                operations.authenticate(
                    new RelyingPartyAuthenticationRequest(
                        assertion,
                        codec.decodeAssertionCredential(
                            authenticator.authenticate(
                                options, "http://localhost:3000", handle.getBytes(), true)))))
        .isInstanceOf(VerificationException.class);
    assertThatThrownBy(
            () ->
                operations.authenticate(
                    new RelyingPartyAuthenticationRequest(
                        assertion,
                        codec.decodeAssertionCredential(
                            authenticator.authenticate(
                                options, frontend.platformPublicUrl(), handle.getBytes(), false)))))
        .isInstanceOf(VerificationException.class);
  }
}
