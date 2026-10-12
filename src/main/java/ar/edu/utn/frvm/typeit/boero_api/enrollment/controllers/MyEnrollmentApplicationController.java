package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.GetMyEnrollmentApplicationUseCase;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListMyEnrollmentApplicationsUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only self-service views over the applicant's own enrollment applications. Starting, editing,
 * submitting, and cancelling a draft all happen through {@link EnrollmentApplicationController}
 * (base path {@code /enrollment-applications}), which is the only surface the wizard calls —
 * keeping a single write path avoids two controllers drifting apart on the same resource.
 */
@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class MyEnrollmentApplicationController {

  private final ListMyEnrollmentApplicationsUseCase listMyEnrollmentApplicationsUseCase;
  private final GetMyEnrollmentApplicationUseCase getMyEnrollmentApplicationUseCase;

  @GetMapping(value = "/my-enrollment-applications", version = Version.V1)
  public PaginatedResponse<EnrollmentApplicationResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) final EnrollmentApplicationStatus status,
      @RequestParam(required = false) final Boolean pendingCourses,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable,
      final Authentication authentication) {
    return PaginatedResponse.from(
        listMyEnrollmentApplicationsUseCase.execute(
            institutionId, currentPersonId(authentication), status, pendingCourses, pageable));
  }

  @GetMapping(value = "/my-enrollment-applications/{applicationId}", version = Version.V1)
  public EnrollmentApplicationResponse get(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID applicationId,
      final Authentication authentication) {
    return getMyEnrollmentApplicationUseCase.execute(
        institutionId, currentPersonId(authentication), applicationId);
  }

  private UUID currentPersonId(final Authentication authentication) {
    return ((JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal())).personId();
  }
}
