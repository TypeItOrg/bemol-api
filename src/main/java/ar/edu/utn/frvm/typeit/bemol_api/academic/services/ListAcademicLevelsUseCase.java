package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAcademicLevelsUseCase {
  private final AcademicAccessGuard accessGuard;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanRepository studyPlanRepository;

  @Transactional(readOnly = true)
  public List<AcademicLevelResponse> execute(final UUID institutionId, final UUID studyPlanId) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.STUDY_PLAN, studyPlanId);

    studyPlanRepository
        .findByIdAndInstitution_Id(studyPlanId, institutionId)
        .orElseThrow(StudyPlanNotFoundException::new);
    return academicLevelRepository.findByStudyPlan_IdOrderByDisplayOrderAsc(studyPlanId).stream()
        .map(AcademicLevelResponse::from)
        .toList();
  }
}
