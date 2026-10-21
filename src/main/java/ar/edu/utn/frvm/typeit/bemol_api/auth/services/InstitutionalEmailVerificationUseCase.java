package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.EmailVerificationProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalEmailVerificationToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.EmailVerificationConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.EmailVerificationCooldownException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalEmailVerificationTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ChangePendingEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ResendEmailVerificationRequest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstitutionalEmailVerificationUseCase {
  private final InstitutionalHostContext hostContext;
  private final UserRepository users;
  private final InstitutionalEmailVerificationTokenRepository tokens;
  private final InstitutionalPasswordResetTokenRepository passwordResetTokens;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final EmailVerificationProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  private final AuditEventRecorder auditEventRecorder;

  @Transactional(propagation = Propagation.MANDATORY)
  public void sendInitial(final User user) {
    issue(user, clock.instant(), tokens.findByUser_Id(user.getId()));
  }

  @Transactional
  public void resend(final ResendEmailVerificationRequest request) {
    hostContext.requireInstitution(request.institutionId());

    users
        .findActiveUserId(request.documentNumber(), request.institutionId())
        .flatMap(users::findForEmailVerificationById)
        .filter(user -> user.isAccountActive() && user.requiresEmailVerification())
        .ifPresent(
            user -> {
              final Instant now = clock.instant();
              final var existingToken = tokens.findByUser_Id(user.getId());
              if (canResend(existingToken, now)) {
                issue(user, now, existingToken);
              }
            });
  }

  @Transactional
  public void confirm(final ConfirmEmailVerificationRequest request) {
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(request.token());
    // Resolve only the ID before locking: a managed token here could remain stale after a resend.
    final User user =
        tokens
            .findUserIdByTokenHash(hash)
            .flatMap(users::findForEmailVerificationById)
            .orElseThrow(InvalidEmailVerificationTokenException::new);
    hostContext.requireInstitution(user.getInstitutionId());
    final var token =
        tokens.findByTokenHash(hash).orElseThrow(InvalidEmailVerificationTokenException::new);
    final Instant now = clock.instant();
    if (!user.isAccountActive() || !user.requiresEmailVerification()) {
      throw new InvalidEmailVerificationTokenException();
    }
    token.consume(now);
    user.verifyEmail(now);
    auditEventRecorder.recordCurrentActor(
        user.getInstitution(),
        user.getPerson().getId(),
        AuditAction.INSTITUTIONAL_EMAIL_VERIFIED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.EMAIL_VERIFICATION,
        Map.of());
  }

  @Transactional
  public void changeEmail(final ChangePendingEmailRequest request) {
    hostContext.requireInstitution(request.institutionId());

    final User user =
        users
            .findActiveUserId(request.documentNumber(), request.institutionId())
            .flatMap(users::findForEmailVerificationById)
            .filter(
                candidate -> candidate.isAccountActive() && candidate.requiresEmailVerification())
            .filter(
                candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
            .orElseThrow(InvalidCredentialsException::new);
    final Instant now = clock.instant();
    final var existingToken = tokens.findByUser_Id(user.getId());
    if (!canResend(existingToken, now)) {
      throw new EmailVerificationCooldownException();
    }
    final var previousEmail = user.getPerson().getEmail();
    user.getPerson().updateContact(request.email(), user.getPerson().getPhoneNumber());
    if (!previousEmail.equals(user.getPerson().getEmail())) {
      auditEventRecorder.recordCurrentActor(
          user.getInstitution(),
          user.getPerson().getId(),
          AuditAction.INSTITUTIONAL_EMAIL_CHANGED,
          AuditEntityType.USER,
          user.getId(),
          AuditOrigin.SELF_SERVICE,
          Map.of("changedFields", java.util.List.of("email")));
    }
    passwordResetTokens.deleteByUserId(user.getId());
    issue(user, now, existingToken);
  }

  private boolean canResend(
      final Optional<InstitutionalEmailVerificationToken> existingToken, final Instant now) {
    return existingToken
        .map(token -> token.canResendAt(now, properties.resendInterval()))
        .orElse(true);
  }

  private void issue(
      final User user,
      final Instant now,
      final Optional<InstitutionalEmailVerificationToken> existingToken) {
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(raw);
    final var token =
        existingToken
            .map(
                existing -> {
                  existing.replace(hash, now, properties.tokenExpiration());
                  return existing;
                })
            .orElseGet(
                () ->
                    InstitutionalEmailVerificationToken.issue(
                        user, hash, now, properties.tokenExpiration()));
    try {
      tokens.saveAndFlush(token);
    } catch (final DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && ("email_verification_user_unique".equals(violation.getConstraintName())
                || "email_verification_hash_unique".equals(violation.getConstraintName()))) {
          throw new EmailVerificationConflictException();
        }
      }
      throw exception;
    }
    events.publishEvent(InstitutionalEmailVerificationRequested.from(user, raw));
  }
}
