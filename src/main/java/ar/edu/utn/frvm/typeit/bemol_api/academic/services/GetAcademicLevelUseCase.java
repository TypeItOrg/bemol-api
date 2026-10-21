package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicLevelNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAcademicLevelUseCase {
  private final AcademicAccessGuard accessGuard;
  private final AcademicLevelRepository academicLevelRepository;

  @Transactional(readOnly = true)
  public AcademicLevelResponse execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.ACADEMIC_LEVEL, id);

    return academicLevelRepository
        .findByIdAndStudyPlan_Institution_Id(id, institutionId)
        .map(AcademicLevelResponse::from)
        .orElseThrow(AcademicLevelNotFoundException::new);
  }
}
