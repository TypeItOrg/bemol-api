package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountEmailAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformEmailChangeTokenRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.UpdatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePlatformAccountUseCase {
  private final AuditEventRecorder auditRecorder;

  private final PlatformAccountRepository platformAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;
  private final PlatformEmailChangeTokenRepository emailChangeTokens;

  @Transactional
  public PlatformAccountAdminResponse execute(
      final UUID id, final UpdatePlatformAccountRequest request) {
    final PlatformAccount account =
        platformAccountRepository
            .findWithLockById(id)
            .orElseThrow(PlatformAccountNotFoundException::new);
    final String email = request.email().trim().toLowerCase(Locale.ROOT);

    if (platformAccountRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
      throw new PlatformAccountEmailAlreadyExistsException();
    }

    final var before = account.auditSnapshot();
    final var passwordBefore = account.passwordAuditSnapshot();
    final boolean emailChanged = !account.getEmail().equals(email);
    final boolean passwordChanged = request.password() != null && !request.password().isEmpty();

    final boolean selfEdit =
        CurrentPrincipal.platformAccount()
            .map(principal -> id.equals(principal.platformAccountId()))
            .orElse(false);
    final boolean credentialsChanged = emailChanged || passwordChanged;
    if (selfEdit && credentialsChanged) {
      throw new PlatformAccountValidationException(
          AuthMessages.PLATFORM_ACCOUNT_SELF_CHANGE, emailChanged ? "email" : "password");
    }

    account.updateProfile(request.name().trim(), request.lastName().trim(), email);
    if (passwordChanged) {
      account.changePassword(requireNonNull(passwordEncoder.encode(request.password())));
    }

    if (emailChanged || passwordChanged) {
      emailChangeTokens.deleteByPlatformAccount_Id(id);
    }

    saveAccount(account);
    auditRecorder.recordSnapshotChange(
        null,
        null,
        AuthAuditAction.PLATFORM_ACCOUNT_UPDATED,
        PlatformAccount.AUDIT_TYPE,
        account.getId(),
        "Administrador de plataforma",
        before,
        account.auditSnapshot());
    if (passwordChanged) {
      auditRecorder.recordCurrentActor(
          null,
          null,
          AuthAuditAction.PASSWORD_CHANGED,
          PlatformAccount.AUDIT_TYPE,
          account.getId(),
          AuditOrigin.administration(),
          AuditChangeDetails.change(
              "Administrador de plataforma", passwordBefore, account.passwordAuditSnapshot()));
    }

    if (emailChanged || passwordChanged) {
      sessionRevocationService.revokePlatformAccountSessions(id);
    }

    return PlatformAccountAdminResponse.from(account);
  }

  private void saveAccount(final PlatformAccount account) {
    try {
      platformAccountRepository.saveAndFlush(account);
    } catch (final DataIntegrityViolationException exception) {
      throw new PlatformAccountEmailAlreadyExistsException();
    }
  }
}
