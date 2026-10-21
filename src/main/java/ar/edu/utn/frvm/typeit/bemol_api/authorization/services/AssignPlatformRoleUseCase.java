package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.PLATFORM_ROLE_NOT_SEEDED;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PlatformAccountRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PlatformAccountRoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignPlatformRoleUseCase {

  private final RoleRepository roleRepository;
  private final PlatformAccountRoleRepository platformAccountRoleRepository;
  private final SessionRevocationService sessionRevocationService;
  private final AuthorizationCacheInvalidator authorizationCacheInvalidator;

  @Transactional
  public void execute(PlatformAccount account, PlatformRoleCode roleCode) {
    execute(account, roleCode, false);
  }

  @Transactional
  public void execute(PlatformAccount account, PlatformRoleCode roleCode, boolean revokeSessions) {
    Role role =
        roleRepository
            .findByScopeAndCodeAndInstitutionIsNull(RoleScope.PLATFORM, roleCode.name())
            .orElseThrow(
                () -> new IllegalStateException(String.format(PLATFORM_ROLE_NOT_SEEDED, roleCode)));

    if (platformAccountRoleRepository.existsByPlatformAccount_IdAndRole_Id(
        account.getId(), role.getId())) {
      return;
    }

    platformAccountRoleRepository.save(PlatformAccountRole.assign(account, role));

    if (revokeSessions) {
      sessionRevocationService.revokePlatformAccountSessions(account.getId());
    }
    authorizationCacheInvalidator.evictPlatformAccount(account.getId());
  }
}
