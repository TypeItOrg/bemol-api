package ar.edu.utn.frvm.typeit.bemol_api.authorization.security;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.security.AuthorizationAspectSupport.denyUnless;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
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
public class PermissionAuthorizationAspect {

  private final AuthorizationService authorizationService;

  @Before("@annotation(requiresPermission)")
  public void checkPermission(RequiresPermission requiresPermission) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    denyUnless(authorizationService.hasPermission(authentication, requiresPermission.value()));
  }

  @Before("@annotation(requiresAnyPermission)")
  public void checkAnyPermission(RequiresAnyPermission requiresAnyPermission) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    denyUnless(
        authorizationService.hasAnyPermission(authentication, requiresAnyPermission.value()));
  }
}
