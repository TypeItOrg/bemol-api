package ar.edu.utn.frvm.typeit.bemol_api.authorization.security;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.security.AuthorizationAspectSupport.denyUnless;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RoleAuthorizationAspect {

  private final AuthorizationService authorizationService;

  @Before("@annotation(requiresPlatformRole)")
  public void checkPlatformRole(RequiresPlatformRole requiresPlatformRole) {
    authorize(requiresPlatformRole);
  }

  @Before("@within(requiresPlatformRole)")
  public void checkTypePlatformRole(RequiresPlatformRole requiresPlatformRole) {
    authorize(requiresPlatformRole);
  }

  private void authorize(RequiresPlatformRole requiresPlatformRole) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    denyUnless(authorizationService.hasPlatformRole(authentication, requiresPlatformRole.value()));
  }
}
