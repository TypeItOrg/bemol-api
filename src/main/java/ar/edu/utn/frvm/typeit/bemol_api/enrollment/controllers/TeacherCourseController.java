package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherCourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherWeeklySchedulesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateAcademicEnrollmentStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.TeacherCourseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/institutions/{institutionId}/teacher/classes")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class TeacherCourseController {
  private final TeacherCourseService service;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<TeacherCourseClassResponse> list(
      @PathVariable final UUID institutionId,
      final Authentication authentication,
      @PageableDefault(size = 20) final Pageable pageable) {
    return service.list(institutionId, personId(authentication), pageable);
  }

  @GetMapping(value = "/schedules", version = Version.V1)
  public TeacherWeeklySchedulesResponse schedules(
      @PathVariable final UUID institutionId,
      final Authentication authentication,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          final LocalDate week) {
    return service.weeklySchedules(institutionId, personId(authentication), week);
  }

  @GetMapping(value = "/{classId}/enrollments", version = Version.V1)
  public PaginatedResponse<CourseEnrollmentResponse> enrollments(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication,
      @PageableDefault(size = 20) final Pageable pageable) {
    return service.listEnrollments(institutionId, personId(authentication), classId, pageable);
  }

  @GetMapping(value = "/{classId}", version = Version.V1)
  public TeacherCourseClassResponse getClass(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    return service.getClass(institutionId, personId(authentication), classId);
  }

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}", version = Version.V1)
  public CourseEnrollmentResponse enrollment(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    return service.getEnrollment(institutionId, personId(authentication), classId, enrollmentId);
  }

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}/history", version = Version.V1)
  public List<CourseEnrollmentHistoryResponse> history(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    return service.enrollmentHistory(
        institutionId, personId(authentication), classId, enrollmentId);
  }

  @PatchMapping(
      value = "/{classId}/enrollments/{enrollmentId}/academic-status",
      version = Version.V1)
  public CourseEnrollmentResponse updateResult(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @Valid @RequestBody final UpdateAcademicEnrollmentStatusRequest request,
      final Authentication authentication) {
    return service.updateEnrollmentResult(
        institutionId, personId(authentication), classId, enrollmentId, request);
  }

  private UUID personId(final Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    return user.personId();
  }
}
