package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyLimitExceededException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsCodec;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.web.webauthn.api.CredentialRecord;

@ExtendWith(MockitoExtension.class)
class PlatformWebAuthnOperationsTest {
  @Mock private PlatformPasskeyRepositoryAdapter repository;
  @Mock private PlatformPasskeyCredentialRepository credentials;
  @Mock private PlatformPasskeyCredentialMapper mapper;
  @Mock private WebAuthnOptionsCodec codec;
  @Mock private PlatformPasskeyStateStore state;
  @Mock private AuditEventRecorder audit;
  @Mock private WebAuthnProperties properties;
  @Mock private ApplicationEventPublisher events;

  private final UUID id = UUID.randomUUID();
  private final PlatformAccount account =
      PlatformAccount.builder()
          .id(id)
          .name("Ana")
          .lastName("López")
          .email("ana@example.test")
          .build();

  private PlatformWebAuthnOperations operations;

  @BeforeEach
  void setUp() {
    operations =
        new PlatformWebAuthnOperations(
            repository,
            new FrontendPublicProperties("http://localhost:3000", "localhost"),
            credentials,
            mapper,
            codec,
            state,
            audit,
            properties,
            events);
  }

  @Test
  void saveCredentialPublishesRegistrationNotification() {
    final var record = mock(CredentialRecord.class);
    final var credential =
        PlatformPasskeyCredential.builder()
            .id(UUID.randomUUID())
            .platformAccount(account)
            .label("Teléfono")
            .build();
    when(mapper.toEntity(account, record, "Teléfono")).thenReturn(credential);

    assertThat(operations.saveCredential(account, record, "Teléfono")).isSameAs(credential);

    final var eventCaptor = ArgumentCaptor.forClass(PlatformAccountSecurityMessageRequested.class);
    verify(credentials).saveAndFlush(credential);
    verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().kind())
        .isEqualTo(PlatformAccountSecurityMessageRequested.Kind.PASSKEY_REGISTERED);
    assertThat(eventCaptor.getValue().detail()).isEqualTo("Teléfono");
  }

  @Test
  void requireCapacityRejectsWhenMaxPasskeysReached() {
    when(credentials.countActiveByPlatformAccountId(id)).thenReturn(10L);
    when(properties.maxPasskeys()).thenReturn(10);

    assertThatThrownBy(() -> operations.requireCapacity(account))
        .isInstanceOf(PasskeyLimitExceededException.class);
  }
}
