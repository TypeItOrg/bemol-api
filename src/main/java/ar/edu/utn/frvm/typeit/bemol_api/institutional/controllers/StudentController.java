package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.student.StudentSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListStudentsUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/institutions/{institutionId}/students")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class StudentController {

  private final ListStudentsUseCase listStudentsUseCase;

  @GetMapping(version = Version.V1)
  @RequiresAnyPermission({
    PermissionCode.COURSE_ENROLLMENT_READ,
    PermissionCode.COURSE_ENROLLMENT_CREATE,
    PermissionCode.ENROLLMENT_APPLICATION_COURSE_ENROLL
  })
  public PaginatedResponse<StudentSummaryResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) final String search,
      @PageableDefault(size = 20) final Pageable pageable) {
    return listStudentsUseCase.execute(institutionId, search, pageable);
  }
}
