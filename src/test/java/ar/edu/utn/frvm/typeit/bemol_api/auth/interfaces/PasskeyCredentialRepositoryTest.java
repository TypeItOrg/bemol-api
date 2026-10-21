package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PasskeyCredentialMapper;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class PasskeyCredentialRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private PasskeyCredentialRepository passkeyCredentialRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("Should list only active credentials and count them")
  void shouldListOnlyActiveCredentials() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User user = createUser(entityManager, institution, "12345678");
    persistCredential(user, "credential-active");
    final PasskeyCredential revoked = persistCredential(user, "credential-revoked");
    revoked.revoke(Instant.now());
    passkeyCredentialRepository.save(revoked);
    entityManager.flush();

    assertThat(passkeyCredentialRepository.findActiveByUserId(user.getId()))
        .extracting(credential -> credential.getCredentialId())
        .containsExactly("credential-active");
    assertThat(passkeyCredentialRepository.countActiveByUserId(user.getId())).isEqualTo(1L);
    assertThat(passkeyCredentialRepository.existsActiveByUserId(user.getId())).isTrue();
  }

  @Test
  @DisplayName("Should enforce ownership by user")
  void shouldEnforceOwnership() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User owner = createUser(entityManager, institution, "12345678");
    final User other = createUser(entityManager, institution, "87654321");
    final PasskeyCredential credential = persistCredential(owner, "credential-owned");
    entityManager.flush();

    assertThat(
            passkeyCredentialRepository.findWithLockByIdAndUserId(
                credential.getId(), owner.getId()))
        .isPresent();
    assertThat(
            passkeyCredentialRepository.findWithLockByIdAndUserId(
                credential.getId(), other.getId()))
        .isEmpty();
    assertThat(passkeyCredentialRepository.findByCredentialId("credential-owned")).isPresent();
  }

  @Test
  @DisplayName("Should reject duplicate credential ids even after revoke")
  void shouldRejectDuplicateCredentialIds() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User user = createUser(entityManager, institution, "12345678");
    final PasskeyCredential revoked = persistCredential(user, "credential-duplicated");
    revoked.revoke(Instant.now());
    passkeyCredentialRepository.saveAndFlush(revoked);

    assertThatThrownBy(
            () ->
                passkeyCredentialRepository.saveAndFlush(
                    detachedCredential(user, "credential-duplicated")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Should roundtrip the opaque user handle without PII")
  void shouldRoundtripUserHandle() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User user = createUser(entityManager, institution, "12345678");
    user.ensureWebAuthnUserHandle(new SecureRandom());
    userRepository.save(user);
    entityManager.flush();

    final byte[] handle = user.getWebauthnUserHandle();
    assertThat(handle).hasSize(32);
    assertThat(userRepository.findByWebauthnUserHandle(handle.clone()))
        .map(foundUser -> foundUser.getId())
        .contains(user.getId());
  }

  @Test
  @DisplayName("Should persist credential ids larger than 255 chars without truncation")
  void shouldPersistLargeCredentialIds() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User user = createUser(entityManager, institution, "12345678");
    final String largeCredentialId = "A".repeat(300);
    passkeyCredentialRepository.saveAndFlush(detachedCredential(user, largeCredentialId));
    entityManager.clear();

    assertThat(passkeyCredentialRepository.findByCredentialId(largeCredentialId))
        .map(credential -> credential.getCredentialId())
        .contains(largeCredentialId);
  }

  @Test
  @DisplayName("Should resolve the WebAuthn user handle from the user, not the snapshot")
  void shouldResolveUserHandleFromUser() {
    final Institution institution = createInstitution(entityManager, "boero");
    final User user = createUser(entityManager, institution, "12345678");
    final byte[] canonicalHandle = user.ensureWebAuthnUserHandle(new SecureRandom());
    userRepository.save(user);
    final PasskeyCredential credential = detachedCredential(user, "credential-canonical-handle");
    passkeyCredentialRepository.saveAndFlush(credential);
    entityManager.clear();

    final PasskeyCredential reloaded =
        passkeyCredentialRepository.findByCredentialId("credential-canonical-handle").orElseThrow();

    assertThat(new PasskeyCredentialMapper().toRecord(reloaded).getUserEntityUserId().getBytes())
        .isEqualTo(canonicalHandle);
  }

  private PasskeyCredential persistCredential(final User user, final String credentialId) {
    final PasskeyCredential credential = detachedCredential(user, credentialId);
    entityManager.persist(credential);
    return credential;
  }

  private static PasskeyCredential detachedCredential(final User user, final String credentialId) {
    return PasskeyCredential.builder()
        .user(user)
        .credentialId(credentialId)
        .publicKeyCose(new byte[] {1, 2, 3})
        .userHandle(new byte[] {4, 5, 6, 7})
        .label("Key " + UUID.randomUUID())
        .build();
  }
}
