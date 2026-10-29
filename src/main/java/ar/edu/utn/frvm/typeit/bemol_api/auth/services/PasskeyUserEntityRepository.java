package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PasskeyUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public @Nullable PublicKeyCredentialUserEntity findById(final Bytes id) {
    if (id == null) {
      return null;
    }
    return userRepository
        .findByWebauthnUserHandle(id.getBytes())
        .map(this::toUserEntity)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public @Nullable PublicKeyCredentialUserEntity findByUsername(final String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    final UUID userId;
    try {
      userId = UUID.fromString(username);
    } catch (IllegalArgumentException exception) {
      return null;
    }
    return userRepository
        .findWithPersonAndInstitutionById(userId)
        .map(this::toUserEntity)
        .orElse(null);
  }

  @Override
  public void save(final PublicKeyCredentialUserEntity userEntity) {}

  @Override
  public void delete(final Bytes id) {}

  private @Nullable PublicKeyCredentialUserEntity toUserEntity(final User user) {
    final byte[] handle = user.getWebauthnUserHandle();
    if (handle == null) {
      return null;
    }
    final String fullName = user.getName() + " " + user.getLastName();
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(handle.clone()))
        .name(fullName)
        .displayName(fullName)
        .build();
  }
}
