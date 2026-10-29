package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCallerGuard;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilitiesService;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentCourseOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentStudyPlanSpaceInstrumentOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.StartEnrollmentApplicationRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateEnrollmentDraftRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentApplicationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListAvailableEnrollmentTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationCoursesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationPeriodsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationShiftsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationStudyPlanSpaceInstrumentsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationStudyPlanSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListEnrollmentApplicationTrainingPathsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListMyEnrollmentApplicationsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Self-service identity is resolved from the URL and checked without replacing the JWT actor. */
@RestController
@RequestMapping(
    "/institutions/{institutionId}/self-service/{selectedPersonId}/enrollment-applications")
@RequiredArgsConstructor
public class SelfServiceEnrollmentApplicationController {

  private final EnrollmentApplicationService applicationService;
  private final InstitutionalCapabilitiesService capabilities;
  private final OperatingContextResolver operatingContext;
  private final ListMyEnrollmentApplicationsUseCase listMine;
  private final InstitutionalCallerGuard institutionalCallerGuard;
  private final ListEnrollmentApplicationTrainingPathsUseCase
      listEnrollmentApplicationTrainingPathsUseCase;
  private final ListEnrollmentApplicationStudyPlanSpacesUseCase
      listEnrollmentApplicationStudyPlanSpacesUseCase;
  private final ListEnrollmentApplicationShiftsUseCase listEnrollmentApplicationShiftsUseCase;
  private final ListEnrollmentApplicationStudyPlanSpaceInstrumentsUseCase
      listEnrollmentApplicationStudyPlanSpaceInstrumentsUseCase;
  private final ListEnrollmentApplicationPeriodsUseCase listEnrollmentApplicationPeriodsUseCase;
  private final ListAvailableEnrollmentTrainingPathsUseCase
      listAvailableEnrollmentTrainingPathsUseCase;

  private final ListEnrollmentApplicationCoursesUseCase listEnrollmentApplicationCoursesUseCase;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<EnrollmentApplicationResponse> listApplications(
      Authentication authentication,
      @RequestParam(required = false) EnrollmentApplicationStatus status,
      @RequestParam(required = false) Boolean pendingCourses,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    final var principal = requireInstitutionalUser(authentication);
    return PaginatedResponse.from(
        listMine.execute(
            principal.institutionId(),
            operatingContext.currentPersonId().orElseThrow(),
            null,
            status,
            pendingCourses,
            pageable));
  }

  @PostMapping(version = Version.V1)
  public ResponseEntity<EnrollmentApplicationResponse> startOrGetApplication(
      Authentication authentication,
      @Valid @RequestBody StartEnrollmentApplicationRequest request) {
    JwtAuthenticatedUser principal = requireInstitutionalUser(authentication);
    EnrollmentApplicationResponse response =
        applicationService.startOrGetApplication(
            principal.institutionId(), principal.personId(), request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping(value = "/options/periods", version = Version.V1)
  public PaginatedResponse<EnrollmentPeriodResponse> listAvailablePeriods(
      final Authentication authentication,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(required = false) final String search,
      @PageableDefault(sort = "startDate", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    final var principal = requireInstitutionalUser(authentication);

    return listEnrollmentApplicationPeriodsUseCase.execute(
        principal, trainingPathId, search, pageable);
  }

  @GetMapping(value = "/options/training-paths", version = Version.V1)
  public PaginatedResponse<TrainingPathResponse> listAvailableTrainingPaths(
      final Authentication authentication,
      @RequestParam(required = false) final UUID periodId,
      @PageableDefault(sort = "name") final Pageable pageable) {
    final var principal = requireInstitutionalUser(authentication);

    return listAvailableEnrollmentTrainingPathsUseCase.execute(principal, periodId, pageable);
  }

  @GetMapping(value = "/{applicationId}", version = Version.V1)
  public ResponseEntity<EnrollmentApplicationResponse> getApplication(
      Authentication authentication, @PathVariable UUID applicationId) {
    requireInstitutionalUser(authentication);
    EnrollmentApplicationResponse response =
        applicationService.getApplicationById(
            null, operatingContext.currentPersonId().orElseThrow(), applicationId);

    return ResponseEntity.ok(response);
  }

  @PatchMapping(value = "/{applicationId}/draft", version = Version.V1)
  public ResponseEntity<EnrollmentApplicationResponse> updateDraft(
      Authentication authentication,
      @PathVariable UUID applicationId,
      @Valid @RequestBody UpdateEnrollmentDraftRequest request) {
    JwtAuthenticatedUser principal = requireInstitutionalUser(authentication);
    EnrollmentApplicationResponse response =
        applicationService.updateDraft(principal.personId(), applicationId, request);

    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/{applicationId}/cancel", version = Version.V1)
  public ResponseEntity<EnrollmentApplicationResponse> cancelApplication(
      Authentication authentication, @PathVariable UUID applicationId) {
    JwtAuthenticatedUser principal = requireInstitutionalUser(authentication);
    EnrollmentApplicationResponse response =
        applicationService.cancelApplication(principal.personId(), applicationId);

    return ResponseEntity.ok(response);
  }

  @PostMapping(value = "/{applicationId}/submit", version = Version.V1)
  public ResponseEntity<EnrollmentApplicationResponse> submitApplication(
      Authentication authentication, @PathVariable UUID applicationId) {
    JwtAuthenticatedUser principal = requireInstitutionalUser(authentication);
    EnrollmentApplicationResponse response =
        applicationService.submitApplication(principal.personId(), applicationId);

    return ResponseEntity.ok(response);
  }

  @GetMapping(value = "/{applicationId}/training-paths", version = Version.V1)
  public List<TrainingPathResponse> listTrainingPaths(
      final Authentication authentication, @PathVariable final UUID applicationId) {
    final var principal = requireInstitutionalUser(authentication);

    return listEnrollmentApplicationTrainingPathsUseCase.execute(principal, applicationId);
  }

  @GetMapping(value = "/{applicationId}/shifts", version = Version.V1)
  public List<ShiftResponse> listShifts(
      final Authentication authentication, @PathVariable final UUID applicationId) {
    final var principal = requireInstitutionalUser(authentication);

    return listEnrollmentApplicationShiftsUseCase.execute(principal, applicationId);
  }

  @GetMapping(value = "/{applicationId}/study-plan-spaces", version = Version.V1)
  public List<StudyPlanSpaceResponse> listStudyPlanSpaces(
      final Authentication authentication, @PathVariable final UUID applicationId) {
    final var principal = requireInstitutionalUser(authentication);

    return listEnrollmentApplicationStudyPlanSpacesUseCase.execute(principal, applicationId);
  }

  @GetMapping(value = "/{applicationId}/courses", version = Version.V1)
  public PaginatedResponse<EnrollmentCourseOptionResponse> listCourses(
      final Authentication authentication,
      @PathVariable final UUID applicationId,
      @RequestParam(required = false) final String search,
      @RequestParam(required = false) final UUID studyPlanSpaceId,
      @RequestParam(required = false) final Integer academicYear,
      @PageableDefault(size = 50, sort = "studyPlanSpace.academicSpace.name")
          final Pageable pageable) {
    final var principal = requireInstitutionalUser(authentication);
    return PaginatedResponse.from(
        listEnrollmentApplicationCoursesUseCase.execute(
            principal.institutionId(),
            operatingContext.currentPersonId().orElseThrow(),
            applicationId,
            search,
            studyPlanSpaceId,
            academicYear,
            pageable));
  }

  @GetMapping(
      value = "/{applicationId}/study-plan-spaces/{studyPlanSpaceId}/instruments",
      version = Version.V1)
  public EnrollmentStudyPlanSpaceInstrumentOptionsResponse listStudyPlanSpaceInstruments(
      final Authentication authentication,
      @PathVariable final UUID applicationId,
      @PathVariable final UUID studyPlanSpaceId) {
    final var principal = requireInstitutionalUser(authentication);

    return listEnrollmentApplicationStudyPlanSpaceInstrumentsUseCase.execute(
        principal, applicationId, studyPlanSpaceId);
  }

  private JwtAuthenticatedUser requireInstitutionalUser(Authentication authentication) {
    institutionalCallerGuard.ensureInstitutionalPrincipal(authentication);
    final var personId = operatingContext.currentPersonId().orElseThrow();
    final var principal = (JwtAuthenticatedUser) requireNonNull(authentication.getPrincipal());
    capabilities.requireOwnEnrollmentRead(principal.institutionId(), personId);

    return principal;
  }
}
