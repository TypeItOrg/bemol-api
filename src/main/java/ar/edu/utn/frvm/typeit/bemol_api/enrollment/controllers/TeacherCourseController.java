package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import static ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherCourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.TeacherWeeklySchedulesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.TeacherCourseService;
import java.time.LocalDate;
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

  private UUID personId(final Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }
    return user.personId();
  }
}
