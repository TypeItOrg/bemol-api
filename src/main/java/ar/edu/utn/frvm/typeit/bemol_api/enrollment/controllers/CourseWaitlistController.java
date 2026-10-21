package ar.edu.utn.frvm.typeit.bemol_api.enrollment.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseWaitlistCapacity;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseWaitlistEntryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseWaitlistOptionsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.ListCourseWaitlistUseCase;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/courses/{courseId}/waitlist")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class CourseWaitlistController {

  private final ListCourseWaitlistUseCase listCourseWaitlistUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_WAITLIST_READ)
  public List<CourseWaitlistEntryResponse> list(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID courseId,
      @RequestParam(required = false) @Size(max = 100) final @Nullable String search,
      @RequestParam(required = false) final @Nullable CourseWaitlistCapacity capacity,
      @RequestParam(required = false) @Size(max = 150) final @Nullable String shift) {
    return listCourseWaitlistUseCase.execute(institutionId, courseId, search, capacity, shift);
  }

  @GetMapping(value = "/options", version = Version.V1)
  @RequiresPermission(PermissionCode.COURSE_WAITLIST_READ)
  public CourseWaitlistOptionsResponse options(
      @PathVariable final UUID institutionId, @PathVariable final UUID courseId) {
    return listCourseWaitlistUseCase.options(institutionId, courseId);
  }
}
