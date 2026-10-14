package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;

@ExtendWith(MockitoExtension.class)
class PasskeyUserCredentialRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-09-14T15:00:00Z");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final byte[] HANDLE = {9, 8, 7};
  private static final Bytes CREDENTIAL_ID = new Bytes(new byte[] {1, 2, 3});
  private final User user = User.builder().id(USER_ID).webauthnUserHandle(HANDLE).build();
  @Mock private PasskeyCredentialRepository credentials;
  @Mock private UserRepository users;
  private PasskeyUserCredentialRepository repository;

  @BeforeEach
  void setUp() {
    repository =
        new PasskeyUserCredentialRepository(
            Clock.fixed(NOW, ZoneOffset.UTC), credentials, users, new PasskeyCredentialMapper());
  }

  @Test
  void saveUpdatesUsageAndTransportsOnTheRealCredential() {
    final var existing = credential();
    existing.applyTransports(Set.of("nfc"));
    when(credentials.findWithLockByCredentialId("AQID")).thenReturn(Optional.of(existing));

    repository.save(assertion());

    assertThat(existing.getLastUsedAt()).isEqualTo(NOW);
    assertThat(existing.getSignatureCount()).isEqualTo(7L);
    assertThat(existing.transportSet()).containsExactlyInAnyOrder("usb", "internal");
    assertThat(existing.getUser()).isSameAs(user);
    assertThat(existing.isActive()).isTrue();
    verify(credentials).save(existing);
  }

  @Test
  void saveDoesNotCreateAnUnknownCredential() {
    when(credentials.findWithLockByCredentialId("AQID")).thenReturn(Optional.empty());

    repository.save(assertion());

    verify(credentials, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void revokedCredentialsCannotBeReadOrUpdatedByAnAssertion() {
    final var revoked = credential();
    revoked.revoke(NOW.minusSeconds(60));
    when(credentials.findWithLockByCredentialId("AQID")).thenReturn(Optional.of(revoked));

    repository.save(assertion());

    assertThat(repository.findByCredentialId(CREDENTIAL_ID)).isNull();
    assertThat(revoked.getLastUsedAt()).isNull();
    assertThat(revoked.getSignatureCount()).isZero();
    assertThat(revoked.getRevokedAt()).isEqualTo(NOW.minusSeconds(60));
    verify(credentials, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void userHandleLookupReturnsRealRecordsWithTheCanonicalHandleAndNoUnknownUserData() {
    final var existing = credential();
    when(users.findByWebauthnUserHandle(HANDLE)).thenReturn(Optional.of(user));
    when(credentials.findActiveByUserId(USER_ID)).thenReturn(List.of(existing));

    assertThat(repository.findByUserId(new Bytes(HANDLE)))
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getCredentialId().toBase64UrlString()).isEqualTo("AQID");
              assertThat(record.getUserEntityUserId().getBytes()).containsExactly(9, 8, 7);
              assertThat(record.getPublicKey().getBytes()).containsExactly(4, 5, 6);
              assertThat(record.getSignatureCount()).isZero();
              assertThat(record.getLabel()).isEqualTo("Llave");
            });
    assertThat(repository.findByUserId(new Bytes(new byte[] {0}))).isEmpty();
  }

  private PasskeyCredential credential() {
    return PasskeyCredential.builder()
        .user(user)
        .credentialId("AQID")
        .publicKeyCose(new byte[] {4, 5, 6})
        .userHandle(new byte[] {0})
        .label("Llave")
        .build();
  }

  private CredentialRecord assertion() {
    return ImmutableCredentialRecord.builder()
        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(CREDENTIAL_ID)
        .userEntityUserId(new Bytes(HANDLE))
        .publicKey(new ImmutablePublicKeyCose(new byte[] {4, 5, 6}))
        .signatureCount(7L)
        .transports(Set.of(AuthenticatorTransport.USB, AuthenticatorTransport.INTERNAL))
        .build();
  }
}
