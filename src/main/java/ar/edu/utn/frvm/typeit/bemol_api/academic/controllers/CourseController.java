package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseSpaceOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseStatusRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateCourseRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TeacherOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateCourseUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetCourseUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseLevelOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseSpaceOptionsUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCourseTeachersUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListCoursesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ReplaceCourseClassesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateCourseStatusUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}")
@RequiresInstitutionAccess
@Validated
@RequiredArgsConstructor
public class CourseController {

  private final CreateCourseUseCase createCourseUseCase;
  private final ListCoursesUseCase listCoursesUseCase;
  private final ListCourseLevelOptionsUseCase listCourseLevelOptionsUseCase;
  private final GetCourseUseCase getCourseUseCase;
  private final UpdateCourseStatusUseCase updateCourseStatusUseCase;
  private final ReplaceCourseClassesUseCase replaceCourseClassesUseCase;
  private final ListCourseTeachersUseCase listCourseTeachersUseCase;
  private final ListCourseSpaceOptionsUseCase listCourseSpaceOptionsUseCase;

  @PostMapping(value = "/courses", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission(PermissionCode.COURSE_CREATE)
  public CourseResponse createCourse(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreateCourseRequest request) {
    return createCourseUseCase.execute(institutionId, request);
  }

  @GetMapping(value = "/courses", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_READ)
  public PaginatedResponse<CourseResponse> listCourses(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final CourseStatus status,
      @RequestParam(required = false) final UUID academicSpaceId,
      @RequestParam(required = false) final UUID trainingPathId,
      @RequestParam(required = false) final UUID studyPlanId,
      @RequestParam(required = false) final Integer year,
      @RequestParam(required = false) final UUID academicLevelId,
      @RequestParam(required = false) final AcademicSpaceType type,
      @RequestParam(required = false) final AcademicSpaceFormat format,
      @RequestParam(required = false) final UUID instrumentId,
      @RequestParam(defaultValue = "false") final boolean withoutInstrument,
      @RequestParam(defaultValue = "false") final boolean deleted,
      @PageableDefault(sort = "academicSpace.name", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listCoursesUseCase.execute(
        institutionId,
        search,
        status,
        academicSpaceId,
        trainingPathId,
        studyPlanId,
        year,
        academicLevelId,
        type,
        format,
        instrumentId,
        withoutInstrument,
        deleted,
        pageable);
  }

  @GetMapping(value = "/courses/levels", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_READ)
  public PaginatedResponse<AcademicLevelResponse> listLevels(
      @PathVariable final UUID institutionId,
      @RequestParam @NotNull final UUID studyPlanId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final UUID id,
      @PageableDefault(size = 20) final Pageable pageable) {
    return listCourseLevelOptionsUseCase.execute(institutionId, studyPlanId, search, id, pageable);
  }

  @GetMapping(value = "/courses/teachers", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_READ)
  public PaginatedResponse<TeacherOptionResponse> listTeachers(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @PageableDefault(size = 50, sort = "firstName", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listCourseTeachersUseCase.execute(institutionId, search, pageable);
  }

  @GetMapping(value = "/courses/spaces", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_READ)
  public List<CourseSpaceOptionResponse> listSpaces(
      @PathVariable final UUID institutionId,
      @RequestParam @NotNull final UUID studyPlanId,
      @RequestParam(required = false) @Size(max = 100) final String search) {
    return listCourseSpaceOptionsUseCase.execute(institutionId, studyPlanId, search);
  }

  @GetMapping(value = "/courses/{courseId}", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_READ)
  public CourseResponse getCourse(
      @PathVariable final UUID institutionId, @PathVariable final UUID courseId) {
    return getCourseUseCase.execute(institutionId, courseId);
  }

  @PatchMapping(value = "/courses/{courseId}/status", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission(PermissionCode.COURSE_STATUS_UPDATE)
  public void updateCourseStatus(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID courseId,
      @Valid @RequestBody final CourseStatusRequest request) {
    updateCourseStatusUseCase.execute(institutionId, courseId, request);
  }

  @PutMapping(value = "/courses/{courseId}/classes", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_UPDATE)
  public CourseResponse replaceCourseClasses(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID courseId,
      @Valid @RequestBody final ReplaceCourseClassesRequest request) {
    return replaceCourseClassesUseCase.execute(institutionId, courseId, request);
  }
}
