package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateStudyPlanSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.UpdateStudyPlanSpaceRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CreateStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.DeleteStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListStudyPlanSpacesUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.UpdateStudyPlanSpaceUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/institutions/{institutionId}")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
public class AdminStudyPlanSpaceController {

  private final CreateStudyPlanSpaceUseCase createStudyPlanSpaceUseCase;
  private final ListStudyPlanSpacesUseCase listStudyPlanSpacesUseCase;
  private final GetStudyPlanSpaceUseCase getStudyPlanSpaceUseCase;
  private final UpdateStudyPlanSpaceUseCase updateStudyPlanSpaceUseCase;
  private final DeleteStudyPlanSpaceUseCase deleteStudyPlanSpaceUseCase;

  @PostMapping(value = "/study-plans/{studyPlanId}/spaces", version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public StudyPlanSpaceResponse create(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanId,
      @Valid @RequestBody final CreateStudyPlanSpaceRequest request) {
    return createStudyPlanSpaceUseCase.execute(institutionId, studyPlanId, request);
  }

  @GetMapping(value = "/study-plans/{studyPlanId}/spaces", version = Version.V1)
  public List<StudyPlanSpaceResponse> list(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanId) {
    return listStudyPlanSpacesUseCase.execute(institutionId, studyPlanId);
  }

  @GetMapping(value = "/study-plan-spaces/{studyPlanSpaceId}", version = Version.V1)
  public StudyPlanSpaceResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanSpaceId) {
    return getStudyPlanSpaceUseCase.execute(institutionId, studyPlanSpaceId);
  }

  @PutMapping(value = "/study-plan-spaces/{studyPlanSpaceId}", version = Version.V1)
  public StudyPlanSpaceResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID studyPlanSpaceId,
      @Valid @RequestBody final UpdateStudyPlanSpaceRequest request) {
    return updateStudyPlanSpaceUseCase.execute(institutionId, studyPlanSpaceId, request);
  }

  @DeleteMapping(value = "/study-plan-spaces/{studyPlanSpaceId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanSpaceId) {
    deleteStudyPlanSpaceUseCase.execute(institutionId, studyPlanSpaceId);
  }
}
