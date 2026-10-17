package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentApplicationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/enrollment-applications")
@RequiredArgsConstructor
public class EnrollmentApplicationController {
  private final EnrollmentApplicationService applicationService;

  @GetMapping(value = "/{applicationId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ENROLLMENT_APPLICATION_READ)
  public EnrollmentApplicationResponse getApplication(
      Authentication authentication, @PathVariable UUID applicationId) {
    final var actor = (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    return applicationService.getApplicationById(actor.institutionId(), null, applicationId);
  }
}
