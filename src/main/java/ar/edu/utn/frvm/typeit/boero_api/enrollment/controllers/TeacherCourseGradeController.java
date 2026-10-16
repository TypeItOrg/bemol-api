package ar.edu.utn.frvm.typeit.boero_api.enrollment.controllers;

import static ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CreateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.UpdateCourseEnrollmentGradeRequest;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseEnrollmentGradeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

  private final CourseEnrollmentGradeService gradeService;

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}/grades", version = Version.V1)
  public List<CourseEnrollmentGradeResponse> list(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.listForManagement(institutionId, enrollmentId);
  }

  @PostMapping(value = "/{classId}/enrollments/{enrollmentId}/grades", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public CourseEnrollmentGradeResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @Valid @RequestBody final CreateCourseEnrollmentGradeRequest request,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.create(
        institutionId, enrollmentId, request.evaluation(), request.value(), personId);
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
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.update(
        institutionId,
        enrollmentId,
        gradeId,
        request.evaluation(),
        request.value(),
        request.expectedVersion(),
        personId);
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
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    final var result =
        gradeService.delete(institutionId, enrollmentId, gradeId, expectedVersion, personId);

    if (result.deletedImmediately()) {
      return ResponseEntity.noContent().build();
    }

    return ResponseEntity.ok(result.grade());
  }

  @PostMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}/cancel-deletion",
      version = Version.V1)
  public CourseEnrollmentGradeResponse cancelDeletion(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.cancelDeletion(institutionId, enrollmentId, gradeId, personId);
  }

  @GetMapping(value = "/{classId}/grades/pending-summary", version = Version.V1)
  public PendingGradesSummaryResponse pendingSummary(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);

    return gradeService.pendingSummary(institutionId, classId);
  }

  @GetMapping(value = "/{classId}/enrollments/{enrollmentId}/grades/archived", version = Version.V1)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.archived(institutionId, enrollmentId);
  }

  @GetMapping(
      value = "/{classId}/enrollments/{enrollmentId}/grades/{gradeId}/history",
      version = Version.V1)
  public Page<CourseEnrollmentGradeHistoryResponse> history(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      @PathVariable final UUID enrollmentId,
      @PathVariable final UUID gradeId,
      @PageableDefault(size = 50) final Pageable pageable,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);
    gradeService.requireEnrollmentInClass(institutionId, classId, enrollmentId);

    return gradeService.history(institutionId, enrollmentId, gradeId, pageable);
  }

  @PostMapping(value = "/{classId}/grades/publish", version = Version.V1)
  public PublishGradesResponse publish(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID classId,
      final Authentication authentication) {
    final UUID personId = personId(authentication);
    gradeService.requireTeacherForClass(institutionId, personId, classId);

    return gradeService.publishClass(institutionId, classId, personId);
  }

  private UUID personId(final Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof JwtAuthenticatedUser user)) {
      throw new AccessDeniedException(DEFAULT_FORBIDDEN_MESSAGE);
    }

    return user.personId();
  }
}
