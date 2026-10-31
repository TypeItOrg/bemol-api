package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAuthResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.Attempt;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.Ceremony;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformPasskeyUseCase {
  private final PlatformAccountRepository accounts;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformWebAuthnOperations operations;
  private final PlatformPasskeyStateStore state;
  private final PlatformAuthenticationSessionIssuer sessionIssuer;
  private final SessionRevocationService revocation;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PasskeyAuthenticationOptionsResponse authenticationOptions(final String attemptId) {
    final var account = accountForAttempt(state.attempt(attemptId), false);
    return operations.assertionOptions(account, null, attemptId, "login", "");
  }

  @Transactional
  public PlatformAuthResponse authenticate(
      final String ceremonyId, final JsonNode credential, final HttpServletRequest request) {
    final var ceremony = state.consumeCeremony(ceremonyId, "login");
    final var attempt = state.attempt(ceremony.attemptId());
    final var account = accountForAttempt(attempt, true);
    requireAccount(ceremony, account);
    operations.verifyAssertion(ceremony, account, credential);
    state.claimAttempt(ceremony.attemptId());
    return sessionIssuer.issue(account, attempt.rememberMe(), request);
  }

  @Transactional(readOnly = true)
  public PasskeyRegistrationOptionsResponse setupOptions(
      final String attemptId, final String code, final String label) {
    final var account = accountForAttempt(state.attempt(attemptId), false);
    state.limit(account.getId());
    final String grant = state.requireSetupGrant(account.getId(), code);
    return operations.creationOptions(account, null, attemptId, "setup", grant, label);
  }

  @Transactional
  public PlatformAuthResponse setup(
      final String ceremonyId, final JsonNode credential, final HttpServletRequest request) {
    final var ceremony = state.consumeCeremony(ceremonyId, "setup");
    final var attempt = state.attempt(ceremony.attemptId());
    final var account = accountForAttempt(attempt, true);
    requireAccount(ceremony, account);
    final CredentialRecord record = operations.verifyRegistration(ceremony, account, credential);
    state.claimAttempt(ceremony.attemptId());
    state.consumeSetupGrant(account.getId(), ceremony.grant());

    final var previous = credentials.findActiveByPlatformAccountId(account.getId());
    for (final var old : previous) {
      old.revoke(clock.instant());
      operations.recordChange(account, old, AuditAction.PASSKEY_DELETED);
    }
    operations.saveCredential(account, record, ceremony.label());
    revocation.revokePlatformAccountSessions(account.getId());
    log.info(
        "[Auth] Platform passkey setup completed, platformAccountId: {}, recovery: {}",
        account.getId(),
        !previous.isEmpty());
    return sessionIssuer.issue(account, attempt.rememberMe(), request);
  }

  private PlatformAccount accountForAttempt(final Attempt attempt, final boolean lock) {
    final var account = account(attempt.accountId(), lock);
    if (!JwtService.hashToken(account.getPassword()).equals(attempt.passwordHash())) {
      throw new InvalidLoginAttemptException();
    }
    return account;
  }

  private PlatformAccount account(final UUID id, final boolean lock) {
    return (lock ? accounts.findWithLockById(id) : accounts.findById(id))
        .filter(PlatformAccount::isEnabled)
        .orElseThrow(InvalidLoginAttemptException::new);
  }

  private void requireAccount(final Ceremony ceremony, final PlatformAccount account) {
    if (!ceremony.accountId().equals(account.getId())) {
      throw new InvalidLoginAttemptException();
    }
  }
}
