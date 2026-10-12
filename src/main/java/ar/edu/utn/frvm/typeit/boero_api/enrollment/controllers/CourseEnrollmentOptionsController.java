package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentAssignmentOptionsResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.ListCourseEnrollmentOptionsUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/courses")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class CourseEnrollmentOptionsController {

  private final ListCourseEnrollmentOptionsUseCase listCourseEnrollmentOptionsUseCase;

  @GetMapping(value = "/{courseId}/enrollment-options", version = Version.V1)
  @RequiresAnyPermission({
    PermissionCode.COURSE_READ,
    PermissionCode.COURSE_ENROLLMENT_READ,
    PermissionCode.COURSE_ENROLLMENT_CREATE,
    PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL
  })
  public CourseEnrollmentAssignmentOptionsResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID courseId) {
    return listCourseEnrollmentOptionsUseCase.execute(institutionId, courseId);
  }
}
