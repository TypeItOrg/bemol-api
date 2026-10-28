package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages.SHA_256_UNAVAILABLE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PasswordRecoveryProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalPasswordRecoveryRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasswordRecoveryRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestInstitutionalPasswordRecoveryUseCase {
  private final InstitutionalHostContext hostContext;
  private final Clock clock;

  private static final int TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final InstitutionalPasswordResetTokenRepository passwordResetTokenRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final PasswordRecoveryProperties passwordRecoveryProperties;
  private final AccountActionRateLimiter limiter;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public void execute(final PasswordRecoveryRequest request) {
    hostContext.requireInstitution(request.institutionId());

    userRepository
        .findWithPersonAndInstitutionForPasswordRecovery(
            request.documentNumber(), request.institutionId())
        .ifPresent(
            user -> {
              try {
                requestForUser(user);
              } catch (
                  final ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions
                          .EmailVerificationCooldownException
                      ignored) {
                // Public recovery keeps the same response for absent and rate-limited accounts.
              }
            });
  }

  public void requestForUser(final User user) {
    if (canRecoverPassword(user)) {
      limiter.mail(user.getPerson().getId());
      createTokenAndPublishEvent(user);
    }
  }

  private boolean canRecoverPassword(final User user) {
    return user.isAccountActive()
        && user.getPerson().getEmail() != null
        && !user.getPerson().getEmail().isBlank();
  }

  private void createTokenAndPublishEvent(final User user) {
    final String token = generateToken();
    passwordResetTokenRepository.deleteByUserId(user.getId());
    passwordResetTokenRepository.save(
        InstitutionalPasswordResetToken.builder()
            .user(user)
            .tokenHash(hash(token))
            .expiresAt(clock.instant().plus(passwordRecoveryProperties.tokenExpiration()))
            .build());

    eventPublisher.publishEvent(InstitutionalPasswordRecoveryRequested.from(user, token));
  }

  private String generateToken() {
    final byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(final String token) {
    try {
      return Base64.getEncoder()
          .encodeToString(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (final NoSuchAlgorithmException exception) {
      throw new IllegalStateException(SHA_256_UNAVAILABLE, exception);
    }
  }
}
