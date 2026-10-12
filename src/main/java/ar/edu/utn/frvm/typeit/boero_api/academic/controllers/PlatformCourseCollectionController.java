package ar.edu.utn.frvm.typeit.boero_api.academic.controllers;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ListCoursesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@Validated
@RequiredArgsConstructor
public class PlatformCourseCollectionController {

  private final ListCoursesUseCase listCoursesUseCase;

  @GetMapping(value = "/courses", version = Version.V1)
  public PaginatedResponse<CourseResponse> listCourses(
      @RequestParam(required = false) final UUID institutionId,
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
      @PageableDefault(
              sort = {"institution.name", "academicSpace.name"},
              direction = Sort.Direction.ASC)
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
}
