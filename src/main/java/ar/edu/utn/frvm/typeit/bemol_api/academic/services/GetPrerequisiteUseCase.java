package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.PrerequisiteNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.PrerequisiteResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPrerequisiteUseCase {
  private final AcademicAccessGuard accessGuard;
  private final PrerequisiteRepository prerequisiteRepository;

  @Transactional(readOnly = true)
  public PrerequisiteResponse execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.PREREQUISITE, id);

    return prerequisiteRepository
        .findByIdAndStudyPlan_Institution_Id(id, institutionId)
        .map(PrerequisiteResponse::from)
        .orElseThrow(PrerequisiteNotFoundException::new);
  }
}
