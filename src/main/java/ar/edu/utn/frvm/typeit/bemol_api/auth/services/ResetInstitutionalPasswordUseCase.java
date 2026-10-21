package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.InstitutionalPasswordResetToken;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidPasswordRecoveryTokenException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PasswordConfirmationMismatchException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionalPasswordResetTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ResetPasswordRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResetInstitutionalPasswordUseCase {
  private final AuditEventRecorder auditRecorder;
  private final InstitutionalHostContext hostContext;
  private final Clock clock;
  private final UserRepository userRepository;

  private final InstitutionalPasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;

  @Transactional
  public void execute(final ResetPasswordRequest request) {
    if (!request.password().equals(request.confirmPassword())) {
      throw new PasswordConfirmationMismatchException();
    }

    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(request.token());
    final var owner =
        passwordResetTokenRepository
            .findUserIdByTokenHash(hash)
            .flatMap(userRepository::findForEmailVerificationById)
            .filter(mappedUser -> mappedUser.isAccountActive())
            .orElseThrow(InvalidPasswordRecoveryTokenException::new);
    hostContext.requireInstitution(owner.getInstitutionId());
    final Instant now = clock.instant();
    final InstitutionalPasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHashForUpdate(hash)
            .filter(resetToken -> resetToken.isUsableAt(now))
            .orElseThrow(InvalidPasswordRecoveryTokenException::new);

    token.getUser().changePassword(requireNonNull(passwordEncoder.encode(request.password())));
    token.markUsed(now);
    final var user = token.getUser();
    auditRecorder.recordCurrentActor(
        user.getInstitution(),
        user.getPerson().getId(),
        AuditAction.PASSWORD_CHANGED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
    sessionRevocationService.revokeInstitutionalSessionsForUser(token.getUser().getId());
  }
}
