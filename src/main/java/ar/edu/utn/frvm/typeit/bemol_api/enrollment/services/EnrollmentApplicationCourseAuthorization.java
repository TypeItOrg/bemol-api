package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.Optional;
import java.util.UUID;
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
    return memberOf(application).map(user -> canReadAsMember(application, user)).orElse(false);
  }

  private Optional<JwtAuthenticatedUser> memberOf(final EnrollmentApplication application) {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    final UUID institutionId = application.getInstitution().getId();
    return CurrentPrincipal.institutionalUser(authentication)
        .filter(user -> user.institutionId().equals(institutionId));
  }

  private boolean canReadAsMember(
      final EnrollmentApplication application, final JwtAuthenticatedUser user) {
    final UUID applicantId = application.getApplicantPerson().getId();
    final var selected = operatingContext.currentPersonId();
    if (selected.isPresent()) {
      return selected.get().equals(applicantId);
    }

    return user.personId().equals(applicantId)
        || authorization
            .access(PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ)
            .includes(application.getTrainingPathId());
  }
}
