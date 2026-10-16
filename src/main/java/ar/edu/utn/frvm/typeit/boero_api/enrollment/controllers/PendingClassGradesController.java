package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingClassGradesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/course-classes/grades")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class PendingClassGradesController {

  private final CourseEnrollmentGradeService gradeService;

  @GetMapping(value = "/pending-classes", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_PUBLISH)
  public List<PendingClassGradesResponse> pendingClasses(@PathVariable final UUID institutionId) {
    return gradeService.pendingClasses(institutionId);
  }
}
