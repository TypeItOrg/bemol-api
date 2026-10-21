package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.LoginAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.LoginStateInconsistentException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PasskeyCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.IdentifyLoginRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.IdentifyLoginResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.IdentifyLoginResponse.LoginNextStep;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentifyLoginUseCaseTest {
  @Mock private InstitutionalHostContext hostContext;

  @Mock private UserRepository userRepository;
  @Mock private PasskeyCredentialRepository passkeyCredentialRepository;
  @Mock private LoginAttemptService loginAttemptService;

  private IdentifyLoginUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new IdentifyLoginUseCase(
            hostContext, userRepository, passkeyCredentialRepository, loginAttemptService);
  }

  @Test
  @DisplayName("Should route to PASSWORD when the account has no active passkeys")
  void execute_routesToPasswordWithoutPasskeys() {
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    when(userRepository.findAllByPersonDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(List.of(user));
    when(passkeyCredentialRepository.existsActiveByUserId(user.getId())).thenReturn(false);
    when(loginAttemptService.create(user.getId(), user.getInstitutionId(), false))
        .thenReturn(
            new LoginAttempt(
                "attempt", user.getId(), user.getInstitutionId(), false, Instant.now()));

    final IdentifyLoginResponse response =
        useCase.execute(new IdentifyLoginRequest(institutionId, "12345678"));

    assertThat(response.nextStep()).isEqualTo(LoginNextStep.PASSWORD);
    assertThat(response.loginAttemptId()).isEqualTo("attempt");
  }

  @Test
  @DisplayName("Should route to PASSKEY when the account has active passkeys")
  void execute_routesToPasskeyWithActivePasskeys() {
    final UUID institutionId = UUID.randomUUID();
    final User user = userWith(institutionId);
    when(userRepository.findAllByPersonDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(List.of(user));
    when(passkeyCredentialRepository.existsActiveByUserId(user.getId())).thenReturn(true);
    when(loginAttemptService.create(user.getId(), user.getInstitutionId(), true))
        .thenReturn(
            new LoginAttempt(
                "attempt", user.getId(), user.getInstitutionId(), true, Instant.now()));

    final IdentifyLoginResponse response =
        useCase.execute(new IdentifyLoginRequest(institutionId, "12345678"));

    assertThat(response.nextStep()).isEqualTo(LoginNextStep.PASSKEY);
  }

  @Test
  @DisplayName("Should return account-not-found without PII when unknown")
  void execute_throwsNotFoundForUnknownAccount() {
    final UUID institutionId = UUID.randomUUID();
    when(userRepository.findAllByPersonDocumentNumberAndInstitution_Id("99999999", institutionId))
        .thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute(new IdentifyLoginRequest(institutionId, "99999999")))
        .isInstanceOf(LoginAccountNotFoundException.class)
        .hasMessageNotContaining("99999999");
  }

  @Test
  @DisplayName("Should fail safely when multiple users match")
  void execute_failsOnMultipleUsers() {
    final UUID institutionId = UUID.randomUUID();
    when(userRepository.findAllByPersonDocumentNumberAndInstitution_Id("12345678", institutionId))
        .thenReturn(List.of(userWith(institutionId), userWith(institutionId)));

    assertThatThrownBy(() -> useCase.execute(new IdentifyLoginRequest(institutionId, "12345678")))
        .isInstanceOf(LoginStateInconsistentException.class);
  }

  private static User userWith(final UUID institutionId) {
    final Institution institution = Institution.builder().id(institutionId).build();
    return User.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .person(
            Person.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .documentNumber("12345678")
                .build())
        .password("hash")
        .build();
  }
}
