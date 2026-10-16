package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/course-enrollments/mine/{enrollmentId}/grades")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class OwnCourseEnrollmentGradeController {

  private final CourseEnrollmentGradeService gradeService;

  @GetMapping(version = Version.V1)
  public List<CourseEnrollmentGradeStudentResponse> list(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser principal)) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }

    return gradeService.listOwnPublished(institutionId, enrollmentId, principal.personId());
  }
}
