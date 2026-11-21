package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
  private final OperatingContextResolver operatingContext;

  @GetMapping(version = Version.V1)
  public List<CourseEnrollmentGradeStudentResponse> list(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    operatingContext.requireStudent(institutionId, principal.personId(), principal);

    return gradeService.listOwnPublished(institutionId, enrollmentId, principal.personId());
  }
}
