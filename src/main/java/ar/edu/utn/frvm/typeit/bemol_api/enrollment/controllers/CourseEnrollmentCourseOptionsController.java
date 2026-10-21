package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCoursesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/course-enrollment-options")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class CourseEnrollmentCourseOptionsController {

  private final ListCoursesUseCase listCoursesUseCase;

  @GetMapping(version = Version.V1)
  @RequiresAnyPermission({
    PermissionCode.COURSE_ENROLLMENT_READ,
    PermissionCode.COURSE_ENROLLMENT_CREATE,
    PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL
  })
  public PaginatedResponse<CourseResponse> list(
      @PathVariable final UUID institutionId,
      @PageableDefault(size = 100) final Pageable pageable) {
    return listCoursesUseCase.enrollmentOptions(institutionId, pageable);
  }
}
