package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PasskeyUserCredentialRepository implements UserCredentialRepository {

  private final Clock clock;

  private final PasskeyCredentialRepository passkeyCredentialRepository;
  private final UserRepository userRepository;
  private final PasskeyCredentialMapper mapper;

  @Override
  @Transactional
  public @Nullable CredentialRecord findByCredentialId(final Bytes credentialId) {
    if (credentialId == null) {
      return null;
    }
    return passkeyCredentialRepository
        .findWithLockByCredentialId(credentialId.toBase64UrlString())
        .filter(mappedPasskeyCredential -> mappedPasskeyCredential.isActive())
        .map(mapper::toRecord)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CredentialRecord> findByUserId(final Bytes userId) {
    if (userId == null) {
      return List.of();
    }
    return userRepository
        .findByWebauthnUserHandle(userId.getBytes())
        .map(
            user ->
                passkeyCredentialRepository.findActiveByUserId(user.getId()).stream()
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
        .filter(mappedPasskeyCredential -> mappedPasskeyCredential.isActive())
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
}
