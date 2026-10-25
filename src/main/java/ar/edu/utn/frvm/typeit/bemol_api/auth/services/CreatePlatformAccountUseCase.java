package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.PlatformAccountEmailAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.CreatePlatformAccountRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.PlatformAccountAdminResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPlatformRoleUseCase;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePlatformAccountUseCase {

  private final PlatformAccountRepository platformAccountRepository;
  private final AssignPlatformRoleUseCase assignPlatformRoleUseCase;
  private final PasswordEncoder passwordEncoder;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public PlatformAccountAdminResponse execute(final CreatePlatformAccountRequest request) {
    final String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (platformAccountRepository.existsByEmailIgnoreCase(email)) {
      throw new PlatformAccountEmailAlreadyExistsException();
    }

    final PlatformAccount account = saveAccount(request, email);
    assignPlatformRoleUseCase.execute(account, PlatformRoleCode.PLATFORM_ADMIN, false);

    auditEventRecorder.recordCurrentActor(
        null,
        null,
        AuditAction.PLATFORM_ACCOUNT_CREATED,
        AuditEntityType.PLATFORM_ACCOUNT,
        account.getId(),
        AuditOrigin.PLATFORM_ADMINISTRATION,
        Map.of("initialRole", PlatformRoleCode.PLATFORM_ADMIN.name()));

    return PlatformAccountAdminResponse.from(account);
  }

  private PlatformAccount saveAccount(
      final CreatePlatformAccountRequest request, final String email) {
    try {
      return platformAccountRepository.saveAndFlush(
          PlatformAccount.builder()
              .name(request.name().trim())
              .lastName(request.lastName().trim())
              .email(email)
              .password(requireNonNull(passwordEncoder.encode(request.password())))
              .enabled(!Boolean.FALSE.equals(request.enabled()))
              .build());
    } catch (final DataIntegrityViolationException exception) {
      throw new PlatformAccountEmailAlreadyExistsException();
    }
  }
}
