package ar.edu.utn.frvm.typeit.bemol_api.authorization.security;

import static ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.AuthorizationMessages.INSTITUTION_ACCESS_ANNOTATION_MISCONFIGURED;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class InstitutionAccessAspect {

  private final InstitutionalCallerGuard institutionalCallerGuard;

  @Before(
      "@annotation(ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess) ||"
          + " @within(ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess)")
  public void checkAccess(JoinPoint joinPoint) {
    Authentication authentication = null;
    UUID institutionId = null;
    Object[] args = joinPoint.getArgs();
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();

    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length; i++) {
        if ("institutionId".equals(parameterNames[i]) && args[i] instanceof UUID) {
          institutionId = (UUID) args[i];
        } else if (args[i] instanceof Authentication) {
          authentication = (Authentication) args[i];
        }
      }
    }

    if (authentication == null) {
      authentication = SecurityContextHolder.getContext().getAuthentication();
    }

    if (institutionId == null) {
      throw new IllegalStateException(INSTITUTION_ACCESS_ANNOTATION_MISCONFIGURED);
    }

    institutionalCallerGuard.ensureCallerBelongsToInstitution(authentication, institutionId);
  }
}
