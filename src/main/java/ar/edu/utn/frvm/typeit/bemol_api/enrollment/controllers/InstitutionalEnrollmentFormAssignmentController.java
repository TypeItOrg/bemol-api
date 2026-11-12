package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentFormAssignmentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/enrollment-form-assignments")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class InstitutionalEnrollmentFormAssignmentController {
  private final EnrollmentFormAssignmentUseCase assignments;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_READ)
  public PaginatedResponse<EnrollmentFormAssignmentResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) final @Nullable UUID academicYearId,
      @PageableDefault(sort = "id") final Pageable pageable) {
    return PaginatedResponse.from(assignments.list(institutionId, academicYearId, pageable));
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormAssignmentResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final EnrollmentFormAssignmentCreateRequest request) {
    return assignments.create(institutionId, request);
  }

  @PutMapping(value = "/{assignmentId}", version = Version.V1)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public EnrollmentFormAssignmentResponse replace(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID assignmentId,
      @Valid @RequestBody final EnrollmentFormAssignmentUpdateRequest request) {
    return assignments.replace(institutionId, assignmentId, request);
  }

  @DeleteMapping(value = "/{assignmentId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.INSTITUTION_UPDATE)
  public void delete(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID assignmentId,
      @RequestParam @PositiveOrZero final long expectedRevision) {
    assignments.delete(institutionId, assignmentId, expectedRevision);
  }
}
