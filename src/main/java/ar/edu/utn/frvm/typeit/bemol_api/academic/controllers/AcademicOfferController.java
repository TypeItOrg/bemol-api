package ar.edu.utn.frvm.typeit.bemol_api.academic.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.GetAcademicOfferUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.ListAcademicOffersUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institutions/{institutionId}/academic-offers")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class AcademicOfferController {

  private final ListAcademicOffersUseCase listAcademicOffersUseCase;
  private final GetAcademicOfferUseCase getAcademicOfferUseCase;

  @GetMapping(version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_OFFER_READ)
  public PaginatedResponse<AcademicOfferSummaryResponse> list(
      @PathVariable final UUID institutionId,
      @PageableDefault(sort = "trainingPathName", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listAcademicOffersUseCase.execute(institutionId, pageable);
  }

  @GetMapping(value = "/{studyPlanId}", version = Version.V1)
  @RequiresPermission(PermissionCode.ACADEMIC_OFFER_READ)
  public AcademicOfferDetailResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID studyPlanId) {
    return getAcademicOfferUseCase.execute(institutionId, studyPlanId);
  }
}
