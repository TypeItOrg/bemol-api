package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.AcademicLifecycleService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class AcademicLifecycleController {

  private final AcademicLifecycleService lifecycleService;

  @DeleteMapping(value = "/academic-years/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_DELETE)
  public void deleteAcademicYear(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteAcademicYear(institutionId, id, request);
  }

  @PostMapping(value = "/academic-years/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_RESTORE)
  public void restoreAcademicYear(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreAcademicYear(institutionId, id, request);
  }

  @DeleteMapping(value = "/training-paths/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.TRAINING_PATH_DELETE)
  public void deleteTrainingPath(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteTrainingPath(institutionId, id, request);
  }

  @PostMapping(value = "/training-paths/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.TRAINING_PATH_RESTORE)
  public void restoreTrainingPath(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreTrainingPath(institutionId, id, request);
  }

  @DeleteMapping(value = "/study-plans/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.STUDY_PLAN_DELETE)
  public void deleteStudyPlan(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteStudyPlan(institutionId, id, request);
  }

  @PostMapping(value = "/study-plans/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.STUDY_PLAN_RESTORE)
  public void restoreStudyPlan(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreStudyPlan(institutionId, id, request);
  }

  @DeleteMapping(value = "/academic-spaces/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ACADEMIC_SPACE_DELETE)
  public void deleteAcademicSpace(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteAcademicSpace(institutionId, id, request);
  }

  @PostMapping(value = "/academic-spaces/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.ACADEMIC_SPACE_RESTORE)
  public void restoreAcademicSpace(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreAcademicSpace(institutionId, id, request);
  }

  @DeleteMapping(value = "/instruments/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.INSTRUMENT_DELETE)
  public void deleteInstrument(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteInstrument(institutionId, id, request);
  }

  @PostMapping(value = "/instruments/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.INSTRUMENT_RESTORE)
  public void restoreInstrument(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreInstrument(institutionId, id, request);
  }

  @DeleteMapping(value = "/shifts/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.SHIFT_DELETE)
  public void deleteShift(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteShift(institutionId, id, request);
  }

  @PostMapping(value = "/shifts/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.SHIFT_RESTORE)
  public void restoreShift(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreShift(institutionId, id, request);
  }

  @DeleteMapping(value = "/courses/{id}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.COURSE_DELETE)
  public void deleteCourse(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.deleteCourse(institutionId, id, request);
  }

  @PostMapping(value = "/courses/{id}/restore", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.COURSE_RESTORE)
  public void restoreCourse(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID id,
      @Valid @RequestBody(required = false) final AcademicLifecycleRequest request) {
    lifecycleService.restoreCourse(institutionId, id, request);
  }
}
