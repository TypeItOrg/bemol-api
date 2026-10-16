package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/course-classes/{classId}/grades")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class CourseClassGradeController {

  private final CourseEnrollmentGradeService gradeService;
  private final AcademicAccessGuard accessGuard;

  @GetMapping(value = "/pending-summary", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_READ)
  public PendingGradesSummaryResponse pendingSummary(
      @PathVariable final UUID institutionId, @PathVariable final UUID classId) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH,
        institutionId,
        ScopedResource.COURSE_CLASS,
        classId);

    return gradeService.pendingSummary(institutionId, classId);
  }

  @PostMapping(value = "/publish", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH)
  public PublishGradesResponse publish(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH,
        institutionId,
        ScopedResource.COURSE_CLASS,
        classId);

    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser principal)) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }

    return gradeService.publishClass(institutionId, classId, principal.personId());
  }
}
