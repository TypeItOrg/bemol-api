package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.LoginStateInconsistentException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import java.security.SecureRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebAuthnUserHandleService {

  private final UserRepository userRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public byte[] ensureHandle(final UUID userId) {
    final User user =
        userRepository.findWithLockById(userId).orElseThrow(LoginStateInconsistentException::new);
    final byte[] handle = user.ensureWebAuthnUserHandle(secureRandom);
    userRepository.save(user);
    return handle.clone();
  }
}
