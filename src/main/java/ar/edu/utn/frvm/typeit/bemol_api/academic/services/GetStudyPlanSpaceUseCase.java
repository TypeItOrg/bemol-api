package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceInstrumentOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetStudyPlanSpaceUseCase {
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;

  @Transactional(readOnly = true)
  public StudyPlanSpaceResponse execute(final UUID institutionId, final UUID id) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.STUDY_PLAN_SPACE, id);

    final var space =
        studyPlanSpaceRepository
            .findDetailsByIdAndInstitutionId(id, institutionId)
            .orElseThrow(StudyPlanSpaceNotFoundException::new);
    final var instruments =
        studyPlanSpaceInstrumentRepository
            .findByStudyPlanSpace_IdOrderByInstrument_Name(id)
            .stream()
            .map(
                relation ->
                    new StudyPlanSpaceInstrumentOptionResponse(
                        relation.getInstrument().getId(), relation.getInstrument().getName()))
            .toList();

    return StudyPlanSpaceResponse.from(space, instruments);
  }
}
