package ar.edu.utn.frvm.typeit.boero_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.OwnWeeklySchedulesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListOwnWeeklySchedulesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.OperatingProfileResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.OperatingContextResolver;
import ar.edu.utn.frvm.typeit.boero_api.search.SearchService;
import ar.edu.utn.frvm.typeit.boero_api.search.SearchSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class OperatingProfileController {
  private final OperatingContextResolver contexts;
  private final InstitutionalCallerGuard callers;
  private final CourseEnrollmentService courses;
  private final ListOwnWeeklySchedulesUseCase schedules;
  private final SearchService searchService;

  @GetMapping(value = "/self-service/{selectedPersonId}/search", version = Version.V1)
  public SearchSummaryResponse search(
      @PathVariable UUID institutionId,
      @PathVariable UUID selectedPersonId,
      Authentication authentication,
      @RequestParam @Size(min = 2, max = 100) @Pattern(regexp = ".*[\\p{L}\\p{N}].*") String search,
      @RequestParam(defaultValue = "5") @Min(1) @Max(5) int limit) {
    contexts.require(institutionId, selectedPersonId, actor(authentication));
    return searchService.selfServiceSummary(institutionId, selectedPersonId, search, limit);
  }

  @GetMapping(value = "/operating-profiles", version = Version.V1)
  public List<OperatingProfileResponse> profiles(
      @PathVariable UUID institutionId, Authentication authentication) {
    return contexts.profiles(institutionId, actor(authentication));
  }

  @GetMapping(value = "/self-service/{selectedPersonId}", version = Version.V1)
  public OperatingProfileResponse context(
      @PathVariable UUID institutionId,
      @PathVariable UUID selectedPersonId,
      Authentication authentication) {
    return contexts.profile(institutionId, selectedPersonId, actor(authentication));
  }

  @GetMapping(value = "/self-service/{selectedPersonId}/courses", version = Version.V1)
  public PaginatedResponse<CourseEnrollmentResponse> courses(
      @PathVariable UUID institutionId,
      @PathVariable UUID selectedPersonId,
      Authentication authentication,
      @RequestParam(required = false) CourseEnrollmentStatus status,
      @RequestParam(required = false) AcademicEnrollmentStatus academicStatus,
      Pageable pageable) {
    contexts.require(institutionId, selectedPersonId, actor(authentication));
    return courses.listOwn(institutionId, selectedPersonId, status, academicStatus, pageable);
  }

  @GetMapping(value = "/self-service/{selectedPersonId}/schedules", version = Version.V1)
  public OwnWeeklySchedulesResponse schedules(
      @PathVariable UUID institutionId,
      @PathVariable UUID selectedPersonId,
      Authentication authentication,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate week) {
    contexts.require(institutionId, selectedPersonId, actor(authentication));
    return schedules.execute(institutionId, selectedPersonId, week);
  }

  private JwtAuthenticatedUser actor(Authentication authentication) {
    callers.ensureInstitutionalPrincipal(authentication);
    return (JwtAuthenticatedUser) authentication.getPrincipal();
  }
}
