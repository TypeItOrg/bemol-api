package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformAccountRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.config.PlatformAdminProperties;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class PlatformAdminBootstrap implements ApplicationRunner {

  private final PlatformAccountRepository platformAccountRepository;
  private final AssignPlatformRoleUseCase assignPlatformRoleUseCase;
  private final PasswordEncoder passwordEncoder;
  private final PlatformAdminProperties platformAdminProperties;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!platformAdminProperties.hasPassword()) {
      log.warn("Platform admin bootstrap skipped: app.platform-admin.password is not configured");
      return;
    }

    String email = platformAdminProperties.resolvedEmail();
    if (email == null) {
      log.warn("Platform admin bootstrap skipped: app.platform-admin.email is not configured");
      return;
    }

    PlatformAccount account =
        platformAccountRepository
            .findByEmailIgnoreCase(email)
            .orElseGet(
                () ->
                    platformAccountRepository.save(
                        PlatformAccount.builder()
                            .email(email)
                            .name(platformAdminProperties.resolvedName())
                            .lastName(platformAdminProperties.resolvedLastName())
                            .password(
                                requireNonNull(
                                    passwordEncoder.encode(platformAdminProperties.password())))
                            .build()));

    assignPlatformRoleUseCase.execute(account, PlatformRoleCode.PLATFORM_ADMIN, false);
    log.info("Platform admin account ensured for {}", email);
  }
}
