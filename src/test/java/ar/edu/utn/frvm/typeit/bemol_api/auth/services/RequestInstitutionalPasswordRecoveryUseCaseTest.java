package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PasswordRecoveryProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalPasswordRecoveryRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.PasswordRecoveryRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RequestInstitutionalPasswordRecoveryUseCaseTest {
  @Mock private InstitutionalHostContext hostContext;

  private static final String DOCUMENT_NUMBER = "12345678";
  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Duration TOKEN_EXPIRATION = Duration.ofMinutes(30);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);

  @Mock private UserRepository userRepository;

  @Mock private InstitutionalPasswordResetTokenRepository passwordResetTokenRepository;

  @Mock private ApplicationEventPublisher eventPublisher;

  private RequestInstitutionalPasswordRecoveryUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RequestInstitutionalPasswordRecoveryUseCase(
            hostContext,
            CLOCK,
            userRepository,
            passwordResetTokenRepository,
            eventPublisher,
            new PasswordRecoveryProperties("http://localhost:3000", TOKEN_EXPIRATION),
            org.mockito.Mockito.mock(AccountActionRateLimiter.class));
  }

  @Test
  @DisplayName("publica el evento con los datos resueltos cuando el usuario puede recuperar")
  void publishesResolvedEventWhenUserCanRecover() {
    // given
    final User user = recoverableUser();
    when(userRepository.findWithPersonAndInstitutionForPasswordRecovery(
            DOCUMENT_NUMBER, INSTITUTION_ID))
        .thenReturn(Optional.of(user));

    // when
    useCase.execute(new PasswordRecoveryRequest(DOCUMENT_NUMBER, INSTITUTION_ID));

    // then
    verify(passwordResetTokenRepository).deleteByUserId(USER_ID);

    final ArgumentCaptor<InstitutionalPasswordResetToken> tokenCaptor =
        ArgumentCaptor.forClass(InstitutionalPasswordResetToken.class);
    verify(passwordResetTokenRepository).save(tokenCaptor.capture());

    final ArgumentCaptor<InstitutionalPasswordRecoveryRequested> eventCaptor =
        ArgumentCaptor.forClass(InstitutionalPasswordRecoveryRequested.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    final InstitutionalPasswordRecoveryRequested event = eventCaptor.getValue();
    assertThat(event.userId()).isEqualTo(USER_ID);
    assertThat(event.recipientEmail()).isEqualTo("ana@example.com");
    assertThat(event.institutionName()).isEqualTo("Conservatorio Superior de Música Felipe Boero");
    assertThat(event.fullName()).isEqualTo("Ana García");
    assertThat(event.token()).isNotBlank();

    assertThat(tokenCaptor.getValue().getTokenHash())
        .isEqualTo(RequestInstitutionalPasswordRecoveryUseCase.hash(event.token()));
    assertThat(tokenCaptor.getValue().getExpiresAt())
        .isEqualTo(CLOCK.instant().plus(TOKEN_EXPIRATION));
  }

  @Test
  @DisplayName("no persiste ni publica nada cuando el usuario no existe")
  void publishesNothingWhenUserNotFound() {
    when(userRepository.findWithPersonAndInstitutionForPasswordRecovery(
            DOCUMENT_NUMBER, INSTITUTION_ID))
        .thenReturn(Optional.empty());

    useCase.execute(new PasswordRecoveryRequest(DOCUMENT_NUMBER, INSTITUTION_ID));

    verifyNoInteractions(passwordResetTokenRepository, eventPublisher);
  }

  @Test
  @DisplayName("no publica el evento cuando el acceso del usuario está deshabilitado")
  void publishesNothingWhenUserAccessDisabled() {
    final User user =
        User.builder()
            .id(USER_ID)
            .institution(activeInstitution())
            .person(personWithEmail())
            .enabled(false)
            .build();
    when(userRepository.findWithPersonAndInstitutionForPasswordRecovery(
            DOCUMENT_NUMBER, INSTITUTION_ID))
        .thenReturn(Optional.of(user));

    useCase.execute(new PasswordRecoveryRequest(DOCUMENT_NUMBER, INSTITUTION_ID));

    verifyNoInteractions(passwordResetTokenRepository, eventPublisher);
  }

  @Test
  @DisplayName("no publica el evento cuando la persona no tiene email")
  void publishesNothingWhenPersonHasNoEmail() {
    final Person personWithoutEmail = Person.builder().firstName("Ana").lastName("García").build();
    final User user =
        User.builder()
            .id(USER_ID)
            .institution(activeInstitution())
            .person(personWithoutEmail)
            .build();
    when(userRepository.findWithPersonAndInstitutionForPasswordRecovery(
            DOCUMENT_NUMBER, INSTITUTION_ID))
        .thenReturn(Optional.of(user));

    useCase.execute(new PasswordRecoveryRequest(DOCUMENT_NUMBER, INSTITUTION_ID));

    verifyNoInteractions(passwordResetTokenRepository, eventPublisher);
  }

  @Test
  void pendingUserCanRequestRecoveryWithoutBeingVerified() {
    final User user =
        User.builder()
            .id(USER_ID)
            .institution(activeInstitution())
            .person(personWithEmail())
            .emailVerificationStatus(
                ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus.PENDING)
            .build();
    when(userRepository.findWithPersonAndInstitutionForPasswordRecovery(
            DOCUMENT_NUMBER, INSTITUTION_ID))
        .thenReturn(Optional.of(user));
    useCase.execute(new PasswordRecoveryRequest(DOCUMENT_NUMBER, INSTITUTION_ID));
    verify(passwordResetTokenRepository).save(org.mockito.ArgumentMatchers.any());
    assertThat(user.requiresEmailVerification()).isTrue();
    assertThat(user.isEnabled()).isFalse();
  }

  private User recoverableUser() {
    return User.builder()
        .id(USER_ID)
        .institution(activeInstitution())
        .person(personWithEmail())
        .build();
  }

  private Institution activeInstitution() {
    return Institution.builder().name("Conservatorio Superior de Música Felipe Boero").build();
  }

  private Person personWithEmail() {
    return Person.builder().firstName("Ana").lastName("García").email("ana@example.com").build();
  }
}
