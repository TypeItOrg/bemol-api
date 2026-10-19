package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.PasswordConfirmationMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.ResetPasswordRequest;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class ResetInstitutionalPasswordUseCaseTest {

  @Test
  @DisplayName(
      "Should not consume the token or change the password when confirmation does not match")
  void execute_doesNotChangePasswordWhenConfirmationDoesNotMatch() {
    final InstitutionalPasswordResetTokenRepository passwordResetTokenRepository =
        Mockito.mock(InstitutionalPasswordResetTokenRepository.class);
    final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    final SessionRevocationService sessionRevocationService =
        Mockito.mock(SessionRevocationService.class);
    final ResetInstitutionalPasswordUseCase useCase =
        new ResetInstitutionalPasswordUseCase(
            org.mockito.Mockito.mock(AuditEventRecorder.class),
            org.mockito.Mockito.mock(InstitutionalHostContext.class),
            Clock.systemUTC(),
            Mockito.mock(UserRepository.class),
            passwordResetTokenRepository,
            passwordEncoder,
            sessionRevocationService);
    final ResetPasswordRequest request =
        new ResetPasswordRequest("token", "password123", "different-password");

    assertThatThrownBy(() -> useCase.execute(request))
        .isInstanceOf(PasswordConfirmationMismatchException.class);

    verifyNoInteractions(passwordResetTokenRepository, passwordEncoder, sessionRevocationService);
  }
}
