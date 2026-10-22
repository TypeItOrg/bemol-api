package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentApplicationCourseAuthorization {
  private final ScopedAuthorizationService authorization;
  private final OperatingContextResolver operatingContext;

  public boolean canRead(EnrollmentApplication application) {
    if (authorization.isPlatformAdministrator()) {
      return true;
    }
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)
        || !user.institutionId().equals(application.getInstitution().getId())) {
      return false;
    }
    final var selected = operatingContext.currentPersonId();
    if (selected.isPresent()) {
      return selected.get().equals(application.getApplicantPerson().getId());
    }
    return user.personId().equals(application.getApplicantPerson().getId())
        || authorization
            .access(PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ)
            .includes(application.getTrainingPathId());
  }
}
