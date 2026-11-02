package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind.EMAIL_CHANGED;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind.EMAIL_CHANGE_REQUESTED;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind.EMAIL_CONFIRMATION;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind.PASSWORD_CHANGED;
import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformEmailChangeToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCurrentPasswordException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidLoginAttemptException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasswordConfirmationMismatchException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountEmailAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformEmailChangeTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ChangePlatformPasswordRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdateOwnPlatformProfileRequest;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAccountManagementUseCase {
  private final PlatformAccountRepository accounts;
  private final PlatformEmailChangeTokenRepository tokens;
  private final PlatformSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final PlatformPasskeyStateStore state;
  private final AccountActionRateLimiter limiter;
  private final SessionRevocationService revocation;
  private final AuditEventRecorder audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Transactional
  public void updateProfile(
      final JwtAuthenticatedPlatformAccount principal,
      final UpdateOwnPlatformProfileRequest request) {
    final var account = account(principal);
    account.updateName(request.name().trim(), request.lastName().trim());
    accounts.saveAndFlush(account);
    record(account, AuditAction.PLATFORM_ACCOUNT_UPDATED, List.of("firstName", "lastName"));
  }

  @Transactional
  public void changePassword(
      final JwtAuthenticatedPlatformAccount principal,
      final ChangePlatformPasswordRequest request) {
    if (!request.password().equals(request.confirmPassword())) {
      throw new PasswordConfirmationMismatchException();
    }
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new PlatformAccountValidationException(AuthMessages.PASSWORD_BYTE_LIMIT, "password");
    }

    final var account = account(principal);
    state.limit(account.getId());
    if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(request.currentPassword(), account.getPassword())) {
      throw new InvalidCurrentPasswordException();
    }
    state.requireRecent(account.getId(), principal.sessionId(), "password", true);

    account.changePassword(requireNonNull(passwords.encode(request.password())));
    tokens.deleteByPlatformAccount_Id(account.getId());
    accounts.saveAndFlush(account);
    record(account, AuditAction.PASSWORD_CHANGED, List.of("password"));
    notify(account, account.getEmail(), PASSWORD_CHANGED, null);
    revocation.revokePlatformAccountSessions(account.getId());
  }

  @Transactional
  public void requestEmailChange(
      final JwtAuthenticatedPlatformAccount principal, final String requestedEmail) {
    final var account = account(principal);
    final String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
    if (account.getEmail().equalsIgnoreCase(email)) {
      throw new PlatformAccountValidationException(
          AuthMessages.PLATFORM_ACCOUNT_EMAIL_UNCHANGED, "email");
    }
    requireAvailableEmail(account, email);
    state.requireRecent(account.getId(), principal.sessionId(), "email", true);
    limiter.mail(account.getId());

    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    final String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.deleteByPlatformAccount_Id(account.getId());
    tokens.flush();
    tokens.saveAndFlush(
        PlatformEmailChangeToken.issue(
            account,
            email,
            JwtService.hashToken(account.getPassword()),
            JwtService.hashToken(raw),
            clock.instant(),
            clock.instant().plus(Duration.ofHours(24))));
    record(account, AuditAction.ACCOUNT_EMAIL_CHANGE_REQUESTED, List.of("email"));
    notify(account, email, EMAIL_CONFIRMATION, raw);
    notify(account, account.getEmail(), EMAIL_CHANGE_REQUESTED, null);
  }

  @Transactional
  public void confirmEmailChange(
      final JwtAuthenticatedPlatformAccount principal, final String raw) {
    final var account = account(principal);
    final var token =
        tokens
            .findByTokenHashAndPlatformAccount_Id(JwtService.hashToken(raw), account.getId())
            .orElseThrow(InvalidEmailVerificationTokenException::new);
    token.consume(clock.instant(), JwtService.hashToken(account.getPassword()));
    requireAvailableEmail(account, token.getNewEmail());

    final String previousEmail = account.getEmail();
    account.changeEmail(token.getNewEmail());
    saveEmail(account);
    record(account, AuditAction.PLATFORM_ACCOUNT_UPDATED, List.of("email"));
    notify(account, previousEmail, EMAIL_CHANGED, null);
    notify(account, account.getEmail(), EMAIL_CHANGED, null);
    revocation.revokePlatformAccountSessions(account.getId());
  }

  private PlatformAccount account(final JwtAuthenticatedPlatformAccount principal) {
    final var account =
        accounts
            .findWithLockById(principal.platformAccountId())
            .filter(value -> value.isEnabled())
            .orElseThrow(InvalidLoginAttemptException::new);
    if (!sessions.existsByIdAndActiveTrue(principal.sessionId())) {
      throw new InvalidLoginAttemptException();
    }
    return account;
  }

  private void requireAvailableEmail(final PlatformAccount account, final String email) {
    if (accounts.existsByEmailIgnoreCaseAndIdNot(email, account.getId())) {
      throw new PlatformAccountEmailAlreadyExistsException();
    }
  }

  private void saveEmail(final PlatformAccount account) {
    try {
      accounts.saveAndFlush(account);
    } catch (final DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof ConstraintViolationException violation
            && "platform_accounts_email_unique".equals(violation.getConstraintName())) {
          throw new PlatformAccountEmailAlreadyExistsException();
        }
      }
      throw exception;
    }
  }

  private void record(
      final PlatformAccount account, final AuditAction action, final List<String> fields) {
    audit.recordCurrentActor(
        null,
        null,
        action,
        AuditEntityType.PLATFORM_ACCOUNT,
        account.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of("changedFields", fields));
  }

  private void notify(
      final PlatformAccount account,
      final String email,
      final Kind kind,
      final @Nullable String token) {
    events.publishEvent(
        new PlatformAccountSecurityMessageRequested(
            account.getId(),
            email,
            account.getName() + " " + account.getLastName(),
            kind,
            token,
            MailAuditContext.forPlatformAccount(account.getId())));
  }
}
