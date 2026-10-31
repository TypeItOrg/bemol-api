package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.WebAuthnProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformPasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyLimitExceededException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasskeyNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformPasskeyRequiredException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyAuthenticationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyListResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyRegistrationOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PasskeyResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PlatformPasskeyStateStore.Ceremony;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class PlatformPasskeyManagementUseCase {
  private static final Set<String> SORT_FIELDS = Set.of("label", "createdAt", "lastUsedAt");

  private final PlatformAccountRepository accounts;
  private final PlatformPasskeyCredentialRepository credentials;
  private final PlatformWebAuthnOperations operations;
  private final PlatformPasskeyStateStore state;
  private final WebAuthnProperties properties;
  private final SessionRevocationService revocation;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PasskeyListResponse list(final UUID accountId) {
    return list(accountId, null, null, null);
  }

  @Transactional(readOnly = true)
  public PasskeyListResponse list(
      final UUID accountId,
      final @Nullable String search,
      final @Nullable Boolean used,
      final @Nullable Sort sort) {
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);

    final List<PasskeyResponse> items =
        credentials
            .findActiveByFilters(accountId, normalizedSearch, used, resolveSort(sort))
            .stream()
            .map(
                value ->
                    new PasskeyResponse(
                        value.getId(),
                        value.getLabel(),
                        value.getCreatedAt(),
                        value.getLastUsedAt()))
            .toList();

    final int activeCount =
        normalizedSearch == null && used == null
            ? items.size()
            : Math.toIntExact(credentials.countActiveByPlatformAccountId(accountId));

    return new PasskeyListResponse(items, properties.maxPasskeys(), activeCount);
  }

  public static Sort resolveSort(final @Nullable Sort sort) {
    final List<Sort.Order> orders =
        sort == null
            ? List.of()
            : sort.stream()
                .filter(order -> SORT_FIELDS.contains(order.getProperty()))
                .map(order -> "lastUsedAt".equals(order.getProperty()) ? order.nullsLast() : order)
                .toList();

    final Sort base = orders.isEmpty() ? Sort.by(Sort.Direction.ASC, "createdAt") : Sort.by(orders);

    return base.and(Sort.by(Sort.Direction.ASC, "id"));
  }

  @Transactional(readOnly = true)
  public PasskeyAuthenticationOptionsResponse reauthOptions(
      final JwtAuthenticatedPlatformAccount principal, final String target) {
    final var account = account(principal.platformAccountId(), false);
    return operations.assertionOptions(
        account, principal.sessionId().toString(), null, "reauth", target);
  }

  @Transactional
  public void reauthenticate(
      final JwtAuthenticatedPlatformAccount principal,
      final String ceremonyId,
      final JsonNode credential) {
    final var ceremony = state.consumeCeremony(ceremonyId, "reauth");
    final var account = account(principal.platformAccountId(), true);
    requireSession(ceremony, principal);
    operations.verifyAssertion(ceremony, account, credential);
    state.grantRecent(account.getId(), principal.sessionId(), ceremony.target());
  }

  @Transactional(readOnly = true)
  public PasskeyRegistrationOptionsResponse registrationOptions(
      final JwtAuthenticatedPlatformAccount principal, final String label) {
    final var account = account(principal.platformAccountId(), false);
    state.requireRecent(account.getId(), principal.sessionId(), "register", false);
    return operations.creationOptions(
        account, principal.sessionId().toString(), null, "registration", "", label);
  }

  @Transactional
  public PasskeyResponse register(
      final JwtAuthenticatedPlatformAccount principal,
      final String ceremonyId,
      final JsonNode credential) {
    final var ceremony = state.consumeCeremony(ceremonyId, "registration");
    requireSession(ceremony, principal);
    final var account = account(principal.platformAccountId(), true);
    if (credentials.countActiveByPlatformAccountId(account.getId()) >= properties.maxPasskeys()) {
      throw new PasskeyLimitExceededException();
    }
    final var record = operations.verifyRegistration(ceremony, account, credential);
    state.requireRecent(account.getId(), principal.sessionId(), "register", true);
    final var saved = operations.saveCredential(account, record, ceremony.label());
    return new PasskeyResponse(
        saved.getId(), saved.getLabel(), saved.getCreatedAt(), saved.getLastUsedAt());
  }

  @Transactional
  public void rename(final UUID accountId, final UUID credentialId, final String label) {
    final var account = account(accountId, true);
    final var credential = credential(accountId, credentialId);
    credential.rename(label);
    operations.recordChange(account, credential, AuditAction.PASSKEY_UPDATED);
  }

  @Transactional
  public void revoke(final JwtAuthenticatedPlatformAccount principal, final UUID credentialId) {
    final var account = account(principal.platformAccountId(), true);
    final var credential = credential(account.getId(), credentialId);
    if (credentials.countActiveByPlatformAccountId(account.getId()) <= 1) {
      throw new PlatformPasskeyRequiredException();
    }
    state.requireRecent(account.getId(), principal.sessionId(), "revoke:" + credentialId, true);
    credential.revoke(clock.instant());
    operations.recordChange(account, credential, AuditAction.PASSKEY_DELETED);
    revocation.revokePlatformAccountSessions(account.getId());
  }

  private PlatformAccount account(final UUID id, final boolean lock) {
    return (lock ? accounts.findWithLockById(id) : accounts.findById(id))
        .filter(value -> value.isEnabled())
        .orElseThrow(InvalidLoginAttemptException::new);
  }

  private PlatformPasskeyCredential credential(final UUID accountId, final UUID id) {
    return credentials
        .findWithLockByIdAndPlatformAccountId(id, accountId)
        .filter(value -> value.isActive())
        .orElseThrow(PasskeyNotFoundException::new);
  }

  private void requireSession(
      final Ceremony ceremony, final JwtAuthenticatedPlatformAccount principal) {
    if (!ceremony.accountId().equals(principal.platformAccountId())
        || !principal.sessionId().toString().equals(ceremony.sessionId())) {
      throw new InvalidLoginAttemptException();
    }
  }
}
