package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.EmailVerificationProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalEmailVerificationToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.EmailVerificationCooldownException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidEmailVerificationTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalEmailVerificationTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ChangePendingEmailRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ConfirmEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.IdentifyLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ResendEmailVerificationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class InstitutionalEmailVerificationUseCaseTest {
  final UserRepository users = mock(UserRepository.class);
  final InstitutionalEmailVerificationTokenRepository tokens =
      mock(InstitutionalEmailVerificationTokenRepository.class);
  final InstitutionalPasswordResetTokenRepository resets =
      mock(InstitutionalPasswordResetTokenRepository.class);
  final PasswordEncoder encoder = mock(PasswordEncoder.class);
  final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  final Instant now = Instant.parse("2026-09-12T00:00:00Z");
  final String raw = "a".repeat(43);
  final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(raw);
  final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  final InstitutionalEmailVerificationUseCase useCase =
      new InstitutionalEmailVerificationUseCase(
          org.mockito.Mockito.mock(InstitutionalHostContext.class),
          users,
          tokens,
          resets,
          encoder,
          events,
          new EmailVerificationProperties(
              "http://localhost:3000", Duration.ofHours(24), Duration.ofMinutes(1)),
          clock,
          org.mockito.Mockito.mock(AuditEventRecorder.class));
  User user;
  InstitutionalEmailVerificationToken token;

  @BeforeEach
  void setUp() {
    final Institution institution =
        Institution.builder().id(UUID.randomUUID()).name("Boero").build();
    final Person person =
        Person.builder()
            .institution(institution)
            .id(UUID.randomUUID())
            .firstName("Ana")
            .lastName("Garcia")
            .documentNumber("12345678")
            .email("ana@example.com")
            .build();
    user =
        User.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .person(person)
            .password("encoded")
            .emailVerificationStatus(EmailVerificationStatus.PENDING)
            .build();
    token =
        InstitutionalEmailVerificationToken.issue(
            user, hash, now.minusSeconds(120), Duration.ofHours(24));
  }

  void lockIdentity() {
    when(users.findActiveUserId("12345678", user.getInstitutionId()))
        .thenReturn(Optional.of(user.getId()));
    when(users.findForEmailVerificationById(user.getId())).thenReturn(Optional.of(user));
    when(tokens.findByUser_Id(user.getId())).thenReturn(Optional.of(token));
  }

  void lockToken() {
    when(tokens.findUserIdByTokenHash(hash)).thenReturn(Optional.of(user.getId()));
    when(users.findForEmailVerificationById(user.getId())).thenReturn(Optional.of(user));
    when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(token));
  }

  ChangePendingEmailRequest correction(String password) {
    return new ChangePendingEmailRequest(
        user.getInstitutionId(), "12345678", password, "correct@example.com");
  }

  @Test
  void registrationStoresHashAndPublishesRecipientOnlyAfterPersistence() {
    useCase.sendInitial(user);
    final var captor = ArgumentCaptor.forClass(InstitutionalEmailVerificationRequested.class);
    final var order = inOrder(tokens, events);
    order.verify(tokens).findByUser_Id(user.getId());
    order.verify(tokens).saveAndFlush(any());
    order.verify(events).publishEvent(captor.capture());
    final var event = captor.getValue();
    assertThat(event.token()).matches("[A-Za-z0-9_-]{43}");
    assertThat(event.recipientEmail()).isEqualTo("ana@example.com");
    final var stored = ArgumentCaptor.forClass(InstitutionalEmailVerificationToken.class);
    verify(tokens).saveAndFlush(stored.capture());
    assertThat(stored.getValue().getTokenHash())
        .isEqualTo(RequestInstitutionalPasswordRecoveryUseCase.hash(event.token()))
        .isNotEqualTo(event.token());
    assertThat(stored.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
  }

  @Test
  void confirmationEnablesAccountAndCannotBeReplayed() {
    lockToken();
    assertThat(user.isEnabled()).isFalse();
    useCase.confirm(new ConfirmEmailVerificationRequest(raw));
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.getEmailVerifiedAt()).isEqualTo(now);
    assertThat(token.getUsedAt()).isEqualTo(now);
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
  }

  @Test
  void expiredAtExactBoundaryIsRejected() {
    token =
        InstitutionalEmailVerificationToken.issue(
            user, hash, now.minus(Duration.ofHours(24)), Duration.ofHours(24));
    lockToken();
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
    assertThat(user.requiresEmailVerification()).isTrue();
  }

  @Test
  void oldRecipientCannotVerifyChangedEmail() {
    lockToken();
    user.getPerson().updateContact("other@example.com", null);
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
  }

  @Test
  void disabledAccountCannotBeReactivated() {
    lockToken();
    user.updateAccess(false);
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
    assertThat(user.isAccessEnabled()).isFalse();
  }

  @Test
  void deletedPersonCannotVerify() {
    lockToken();
    user.getPerson().delete();
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
  }

  @Test
  void tokenReplacedWhileWaitingForLockIsRejected() {
    lockToken();
    when(tokens.findByTokenHash(hash)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.confirm(new ConfirmEmailVerificationRequest(raw)))
        .isInstanceOf(InvalidEmailVerificationTokenException.class);
  }

  @Test
  void resendReplacesTokenAndHonorsCooldown() {
    lockIdentity();
    useCase.resend(new ResendEmailVerificationRequest("12345678", user.getInstitutionId()));
    assertThat(token.getTokenHash()).isNotEqualTo(hash);
    useCase.resend(new ResendEmailVerificationRequest("12345678", user.getInstitutionId()));
    verify(events, times(1)).publishEvent(any(InstitutionalEmailVerificationRequested.class));
  }

  @Test
  void unknownAccountResendDoesNotDiscloseOrSend() {
    useCase.resend(new ResendEmailVerificationRequest("87654321", user.getInstitutionId()));
    verifyNoInteractions(tokens, events);
  }

  @Test
  void incorrectPasswordCannotChangeEmailOrInvalidateTokens() {
    lockIdentity();
    assertThatThrownBy(() -> useCase.changeEmail(correction("wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThat(user.getPerson().getEmail()).isEqualTo("ana@example.com");
    verifyNoInteractions(resets, events);
  }

  @Test
  void correctionReplacesEmailAndRevokesRecoveryToken() {
    lockIdentity();
    when(encoder.matches("correct", "encoded")).thenReturn(true);
    useCase.changeEmail(correction("correct"));
    assertThat(user.getPerson().getEmail()).isEqualTo("correct@example.com");
    assertThat(token.getRecipientEmail()).isEqualTo("correct@example.com");
    assertThat(user.requiresEmailVerification()).isTrue();
    verify(resets).deleteByUserId(user.getId());
    verify(events).publishEvent(any(InstitutionalEmailVerificationRequested.class));
  }

  @Test
  void cooldownCorrectionPreservesEmailAndTokens() {
    token.replace(hash, now, Duration.ofHours(24));
    lockIdentity();
    when(encoder.matches("correct", "encoded")).thenReturn(true);
    assertThatThrownBy(() -> useCase.changeEmail(correction("correct")))
        .isInstanceOf(EmailVerificationCooldownException.class);
    assertThat(user.getPerson().getEmail()).isEqualTo("ana@example.com");
    assertThat(token.getTokenHash()).isEqualTo(hash);
    verifyNoInteractions(resets, events);
  }

  @Test
  void existingAndAdministrativeAccountsRemainEnabled() {
    final var existing =
        User.builder().institution(user.getInstitution()).person(user.getPerson()).build();
    assertThat(existing.isEnabled()).isTrue();
    assertThat(existing.getEmailVerificationStatus())
        .isEqualTo(EmailVerificationStatus.NOT_REQUIRED);
    assertThat(existing.getEmailVerifiedAt()).isNull();
  }

  @Test
  void identifyPendingDoesNotCreateLoginAttempt() {
    final var passkeys = mock(PasskeyCredentialRepository.class);
    final var attempts = mock(LoginAttemptService.class);
    when(users.findAllByPersonDocumentNumberAndInstitution_Id("12345678", user.getInstitutionId()))
        .thenReturn(List.of(user));
    final var result =
        new IdentifyLoginUseCase(
                org.mockito.Mockito.mock(InstitutionalHostContext.class), users, passkeys, attempts)
            .execute(new IdentifyLoginRequest(user.getInstitutionId(), "12345678"));
    assertThat(result.nextStep().name()).isEqualTo("EMAIL_VERIFICATION");
    assertThat(result.loginAttemptId()).isNull();
    verifyNoInteractions(passkeys, attempts);
  }

  @Test
  void sessionIssuerRejectsPendingForBothMethods() {
    final var persistence = mock(LoginSessionPersistenceService.class);
    final var issuer =
        new AuthenticationSessionIssuer(
            persistence,
            mock(JwtService.class),
            mock(AuthorityResolver.class),
            mock(RecentAuthService.class),
            users,
            mock(LoginAttemptService.class));
    assertThatThrownBy(() -> issuer.issue(user, "127.0.0.1", "test", false, "PASSWORD"))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(() -> issuer.issue(user, "127.0.0.1", "test", false, "PASSKEY"))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(persistence);
  }
}
