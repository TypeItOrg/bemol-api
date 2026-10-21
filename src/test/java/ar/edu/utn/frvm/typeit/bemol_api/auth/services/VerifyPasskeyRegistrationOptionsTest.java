package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.DuplicatePasskeyCredentialException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnCeremonyInvalidException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.WebAuthnVerificationFailedException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import com.webauthn4j.verifier.exception.BadSignatureException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import tools.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
class VerifyPasskeyRegistrationOptionsTest {

  private final Bytes credentialId = Bytes.random();

  @Mock private UserRepository userRepository;
  @Mock private PasskeyCredentialRepository passkeyCredentialRepository;
  @Mock private PasskeyCredentialMapper mapper;

  @Mock private WebAuthnRelyingPartyOperations relyingPartyOperations;

  @Mock private WebAuthnCeremonyService ceremonyService;
  @Mock private WebAuthnProperties properties;
  @Mock private WebAuthnOptionsCodec codec;

  @Test
  @DisplayName("Should keep a 401 ceremony error when stored options are corrupt")
  void execute_mapsCorruptOptionsToCeremonyInvalid() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    when(ceremonyService.consumeRegistration("ceremony"))
        .thenReturn(
            Optional.of(
                new RegistrationCeremony(
                    "ceremony", userId, sessionId, "Mi PC", "not-json", Instant.now())));
    when(codec.decodeCreationOptions("not-json"))
        .thenThrow(new IllegalStateException("unreadable"));
    when(userRepository.findWithLockById(userId)).thenReturn(Optional.of(user(userId)));
    when(passkeyCredentialRepository.countActiveByUserId(any())).thenReturn(0L);
    when(properties.maxPasskeys()).thenReturn(10);

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .isInstanceOf(WebAuthnCeremonyInvalidException.class);
  }

  @Test
  @DisplayName("Should map an already registered credential to a 409 conflict")
  void execute_mapsDuplicateCredentialToConflict() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    stubVerification(principal, userId, sessionId);
    when(relyingPartyOperations.registerCredential(any())).thenReturn(credentialRecord());
    when(passkeyCredentialRepository.findByCredentialId(credentialId.toBase64UrlString()))
        .thenReturn(Optional.of(Mockito.mock(PasskeyCredential.class)));

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .satisfies(
            exception -> {
              assertThat(exception).isInstanceOf(DuplicatePasskeyCredentialException.class);
              assertThat(((DuplicatePasskeyCredentialException) exception).category())
                  .isEqualTo(ErrorCategory.CONFLICT);
            });
    verify(passkeyCredentialRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Should map a raced duplicate insert to a 409 conflict")
  void execute_mapsRacedDuplicateInsertToConflict() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    stubVerification(principal, userId, sessionId);
    when(relyingPartyOperations.registerCredential(any())).thenReturn(credentialRecord());
    when(passkeyCredentialRepository.findByCredentialId(credentialId.toBase64UrlString()))
        .thenReturn(Optional.empty());
    when(passkeyCredentialRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException(
                    "duplicate",
                    new SQLException("duplicate"),
                    "passkey_credentials_credential_id_key")));

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .isInstanceOf(DuplicatePasskeyCredentialException.class);
  }

  @Test
  @DisplayName("Should propagate unexpected integrity violations as server errors")
  void execute_propagatesUnexpectedIntegrityViolation() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    stubVerification(principal, userId, sessionId);
    when(relyingPartyOperations.registerCredential(any())).thenReturn(credentialRecord());
    when(passkeyCredentialRepository.findByCredentialId(credentialId.toBase64UrlString()))
        .thenReturn(Optional.empty());
    when(passkeyCredentialRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "unexpected",
                new ConstraintViolationException(
                    "unexpected", new SQLException("unexpected"), "other_constraint")));

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Should keep a 401 error for known verification failures")
  void execute_mapsVerificationFailureToUnauthorized() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    stubVerificationCeremony(principal, userId, sessionId);
    when(codec.decodeAttestationCredential(any())).thenReturn(attestationCredential());
    when(relyingPartyOperations.registerCredential(any()))
        .thenThrow(new BadSignatureException("bad signature"));

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .isInstanceOf(WebAuthnVerificationFailedException.class);
  }

  @Test
  @DisplayName("Should propagate infrastructure failures as server errors")
  void execute_propagatesInfrastructureFailure() {
    final UUID userId = UUID.randomUUID();
    final UUID sessionId = UUID.randomUUID();
    final JwtAuthenticatedUser principal =
        institutionalPrincipal(userId, UUID.randomUUID(), sessionId);
    stubVerificationCeremony(principal, userId, sessionId);
    when(codec.decodeAttestationCredential(any())).thenReturn(attestationCredential());
    when(relyingPartyOperations.registerCredential(any()))
        .thenThrow(new DataAccessResourceFailureException("database down"));

    assertThatThrownBy(() -> useCase().execute(principal, "ceremony", credentialNode()))
        .isInstanceOf(DataAccessResourceFailureException.class);
  }

  private VerifyPasskeyRegistrationUseCase useCase() {
    return new VerifyPasskeyRegistrationUseCase(
        org.mockito.Mockito.mock(AuditEventRecorder.class),
        userRepository,
        passkeyCredentialRepository,
        mapper,
        relyingPartyOperations,
        ceremonyService,
        properties,
        codec);
  }

  private void stubVerificationCeremony(
      final JwtAuthenticatedUser principal, final UUID userId, final UUID sessionId) {
    when(ceremonyService.consumeRegistration("ceremony"))
        .thenReturn(
            Optional.of(
                new RegistrationCeremony(
                    "ceremony", userId, sessionId, "Mi PC", "{}", Instant.now())));
    when(codec.decodeCreationOptions("{}")).thenReturn(creationOptions());
    when(userRepository.findWithLockById(userId)).thenReturn(Optional.of(user(userId)));
    when(passkeyCredentialRepository.countActiveByUserId(any())).thenReturn(0L);
    when(properties.maxPasskeys()).thenReturn(10);
  }

  private void stubVerification(
      final JwtAuthenticatedUser principal, final UUID userId, final UUID sessionId) {
    stubVerificationCeremony(principal, userId, sessionId);
    when(codec.decodeAttestationCredential(any())).thenReturn(attestationCredential());
  }

  private JsonNode credentialNode() {
    return Mockito.mock(JsonNode.class);
  }

  @SuppressWarnings("unchecked")
  private PublicKeyCredential<AuthenticatorAttestationResponse> attestationCredential() {
    return Mockito.mock(PublicKeyCredential.class);
  }

  private CredentialRecord credentialRecord() {
    return ImmutableCredentialRecord.builder()
        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(credentialId)
        .userEntityUserId(Bytes.random())
        .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
        .signatureCount(0L)
        .uvInitialized(false)
        .transports(Set.of(AuthenticatorTransport.INTERNAL))
        .backupEligible(false)
        .backupState(false)
        .label("Mi PC")
        .created(Instant.now())
        .build();
  }

  private static PublicKeyCredentialCreationOptions creationOptions() {
    return PublicKeyCredentialCreationOptions.builder()
        .rp(PublicKeyCredentialRpEntity.builder().id("localhost").name("Bemol").build())
        .user(
            ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name("user-id")
                .displayName("Display Name")
                .build())
        .challenge(Bytes.random())
        .pubKeyCredParams(List.of(PublicKeyCredentialParameters.ES256))
        .timeout(Duration.ofMinutes(5))
        .excludeCredentials(List.of())
        .authenticatorSelection(
            AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build())
        .attestation(AttestationConveyancePreference.NONE)
        .build();
  }

  private static User user(final UUID userId) {
    final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
    return User.builder()
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
  }
}
