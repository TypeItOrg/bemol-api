package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CreateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeCommands.DeleteOutcome;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/course-enrollments/{enrollmentId}/grades")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class CourseEnrollmentGradeController {

  private final CourseEnrollmentGradeService gradeService;
  private final AcademicAccessGuard accessGuard;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_READ)
  public List<CourseEnrollmentGradeResponse> list(
      @PathVariable final UUID institutionId, @PathVariable final UUID enrollmentId) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_READ,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.listForManagement(institutionId, enrollmentId);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_CREATE)
  public CourseEnrollmentGradeResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      @Valid @RequestBody final CreateCourseEnrollmentGradeRequest request,
      final Authentication authentication) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_CREATE,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.create(
        institutionId,
        enrollmentId,
        request.evaluation(),
        request.value(),
        actorPersonId(authentication));
  }

  @PatchMapping(value = "/{gradeId}", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_UPDATE)
  public CourseEnrollmentGradeResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @Valid @RequestBody final UpdateCourseEnrollmentGradeRequest request,
      final Authentication authentication) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_UPDATE,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.update(
        institutionId,
        enrollmentId,
        gradeId,
        request.evaluation(),
        request.value(),
        request.expectedVersion(),
        actorPersonId(authentication));
  }

  @DeleteMapping(value = "/{gradeId}", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_DELETE)
  public ResponseEntity<CourseEnrollmentGradeResponse> delete(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @RequestParam(required = false) final @Nullable Long expectedVersion,
      final Authentication authentication) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_DELETE,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return switch (gradeService.delete(
        institutionId, enrollmentId, gradeId, expectedVersion, actorPersonId(authentication))) {
      case DeleteOutcome.DeletedImmediately ignored -> ResponseEntity.noContent().build();
      case DeleteOutcome.MarkedForDeletion marked -> ResponseEntity.ok(marked.grade());
    };
  }

  @PostMapping(value = "/{gradeId}/cancel-deletion", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_DELETE)
  public CourseEnrollmentGradeResponse cancelDeletion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @RequestParam(required = false) final @Nullable Long expectedVersion,
      final Authentication authentication) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_DELETE,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.cancelDeletion(
        institutionId, enrollmentId, gradeId, expectedVersion, actorPersonId(authentication));
  }

  @GetMapping(value = "/archived", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_READ)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      @PathVariable final UUID institutionId, @PathVariable final UUID enrollmentId) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_READ,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.archived(institutionId, enrollmentId);
  }

  @GetMapping(value = "/{gradeId}/history", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_ENROLLMENT_GRADE_READ)
  public PaginatedResponse<CourseEnrollmentGradeHistoryResponse> history(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @PageableDefault(size = 50) final Pageable pageable) {
    accessGuard.require(
        PermissionCode.COURSE_ENROLLMENT_GRADE_READ,
        institutionId,
        ScopedResource.COURSE_ENROLLMENT,
        enrollmentId);

    return gradeService.history(institutionId, enrollmentId, gradeId, pageable);
  }

  private @Nullable UUID actorPersonId(final Authentication authentication) {
    return CurrentPrincipal.institutionalUser(authentication)
        .map(entry -> entry.actorPersonId())
        .orElse(null);
  }
}
