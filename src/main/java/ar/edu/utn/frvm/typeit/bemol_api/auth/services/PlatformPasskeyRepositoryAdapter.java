package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformPasskeyCredentialRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlatformPasskeyRepositoryAdapter
    implements UserCredentialRepository,
        org.springframework.security.web.webauthn.management
            .PublicKeyCredentialUserEntityRepository {

  private final Clock clock;

  private final PlatformPasskeyCredentialRepository passkeyCredentialRepository;
  private final PlatformAccountRepository accountRepository;
  private final PlatformPasskeyCredentialMapper mapper;

  @Override
  @Transactional
  public @Nullable CredentialRecord findByCredentialId(final Bytes credentialId) {
    if (credentialId == null) {
      return null;
    }
    return passkeyCredentialRepository
        .findWithLockByCredentialId(credentialId.toBase64UrlString())
        .filter(mappedPlatformPasskeyCredential -> mappedPlatformPasskeyCredential.isActive())
        .map(mapper::toRecord)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CredentialRecord> findByUserId(final Bytes userId) {
    if (userId == null) {
      return List.of();
    }
    return accountRepository
        .findByWebauthnUserHandle(userId.getBytes())
        .map(
            user ->
                passkeyCredentialRepository.findActiveByPlatformAccountId(user.getId()).stream()
                    .map(mapper::toRecord)
                    .toList())
        .orElse(List.of());
  }

  @Override
  @Transactional
  public void save(final CredentialRecord credentialRecord) {
    if (credentialRecord == null || credentialRecord.getCredentialId() == null) {
      return;
    }
    passkeyCredentialRepository
        .findWithLockByCredentialId(credentialRecord.getCredentialId().toBase64UrlString())
        .filter(mappedPlatformPasskeyCredential -> mappedPlatformPasskeyCredential.isActive())
        .ifPresent(
            existing -> {
              existing.markUsed(clock.instant(), credentialRecord.getSignatureCount());
              existing.applyTransports(
                  credentialRecord.getTransports() == null
                      ? Set.of()
                      : credentialRecord.getTransports().stream()
                          .map(transport -> transport == null ? "" : transport.getValue())
                          .collect(Collectors.toUnmodifiableSet()));
              passkeyCredentialRepository.save(existing);
            });
  }

  @Override
  public void delete(final Bytes credentialId) {}

  @Override
  @Transactional(readOnly = true)
  public PublicKeyCredentialUserEntity findById(final Bytes id) {
    return id == null
        ? null
        : accountRepository
            .findByWebauthnUserHandle(id.getBytes())
            .map(this::toUserEntity)
            .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public PublicKeyCredentialUserEntity findByUsername(final String username) {
    if (username == null) {
      return null;
    }
    try {
      return accountRepository
          .findById(UUID.fromString(username))
          .map(this::toUserEntity)
          .orElse(null);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  @Override
  public void save(final PublicKeyCredentialUserEntity user) {}

  private PublicKeyCredentialUserEntity toUserEntity(final PlatformAccount account) {
    final String name = account.getName() + " " + account.getLastName();
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(account.getWebauthnUserHandle()))
        .name(name)
        .displayName(name)
        .build();
  }
}
