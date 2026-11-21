package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CreateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.UpdateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeCommands.DeleteOutcome;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.TeacherCourseGradeService;
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
@RequestMapping("/institutions/{institutionId}/teacher/classes")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class TeacherCourseGradeController {

  private final TeacherCourseGradeService gradeService;

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}/grades", version = Version.V1)
  public List<CourseEnrollmentGradeResponse> list(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.list(institutionId, principal.personId(), classId, enrollmentId);
  }

  @PostMapping(value = "/{classId}/enrollments/{enrollmentId}/grades", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public CourseEnrollmentGradeResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @Valid @RequestBody final CreateCourseEnrollmentGradeRequest request,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.create(
        institutionId,
        principal.personId(),
        principal.actorPersonId(),
        classId,
        enrollmentId,
        request.evaluation(),
        request.value());
  }

  @PatchMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}",
      version = Version.V1)
  public CourseEnrollmentGradeResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @Valid @RequestBody final UpdateCourseEnrollmentGradeRequest request,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.update(
        institutionId,
        principal.personId(),
        principal.actorPersonId(),
        classId,
        enrollmentId,
        gradeId,
        request.evaluation(),
        request.value(),
        request.expectedVersion());
  }

  @DeleteMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}",
      version = Version.V1)
  public ResponseEntity<CourseEnrollmentGradeResponse> delete(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @RequestParam(required = false) final @Nullable Long expectedVersion,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return switch (gradeService.delete(
        institutionId,
        principal.personId(),
        principal.actorPersonId(),
        classId,
        enrollmentId,
        gradeId,
        expectedVersion)) {
      case DeleteOutcome.DeletedImmediately ignored -> ResponseEntity.noContent().build();
      case DeleteOutcome.MarkedForDeletion marked -> ResponseEntity.ok(marked.grade());
    };
  }

  @PostMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}/cancel-deletion",
      version = Version.V1)
  public CourseEnrollmentGradeResponse cancelDeletion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @RequestParam(required = false) final @Nullable Long expectedVersion,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.cancelDeletion(
        institutionId,
        principal.personId(),
        principal.actorPersonId(),
        classId,
        enrollmentId,
        gradeId,
        expectedVersion);
  }

  @GetMapping(value = "/{classId}/grades/pending-summary", version = Version.V1)
  public PendingGradesSummaryResponse pendingSummary(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.pendingSummary(institutionId, principal.personId(), classId);
  }

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}/grades/archived", version = Version.V1)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.archived(institutionId, principal.personId(), classId, enrollmentId);
  }

  @GetMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}/history",
      version = Version.V1)
  public PaginatedResponse<CourseEnrollmentGradeHistoryResponse> history(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @PageableDefault(size = 50) final Pageable pageable,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.history(
        institutionId, principal.personId(), classId, enrollmentId, gradeId, pageable);
  }

  @PostMapping(value = "/{classId}/grades/publish", version = Version.V1)
  public PublishGradesResponse publish(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    final var principal = CurrentPrincipal.requireInstitutionalUser(authentication);
    return gradeService.publishClass(
        institutionId, principal.personId(), principal.actorPersonId(), classId);
  }
}
