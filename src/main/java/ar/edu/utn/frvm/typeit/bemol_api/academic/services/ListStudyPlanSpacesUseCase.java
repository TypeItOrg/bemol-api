package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceInstrumentOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListStudyPlanSpacesUseCase {
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;

  @Transactional(readOnly = true)
  public List<StudyPlanSpaceResponse> execute(final UUID institutionId, final UUID studyPlanId) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.STUDY_PLAN, studyPlanId);

    studyPlanRepository
        .findByIdAndInstitution_Id(studyPlanId, institutionId)
        .orElseThrow(StudyPlanNotFoundException::new);
    final var instrumentsBySpace =
        studyPlanSpaceInstrumentRepository
            .findByStudyPlanIdWithInstruments(institutionId, studyPlanId)
            .stream()
            .collect(
                Collectors.groupingBy(
                    relation -> relation.getStudyPlanSpace().getId(),
                    Collectors.mapping(
                        relation ->
                            new StudyPlanSpaceInstrumentOptionResponse(
                                relation.getInstrument().getId(),
                                relation.getInstrument().getName()),
                        Collectors.toList())));

    return studyPlanSpaceRepository.findByStudyPlanIdWithDetails(studyPlanId).stream()
        .map(
            space ->
                StudyPlanSpaceResponse.from(
                    space, instrumentsBySpace.getOrDefault(space.getId(), List.of())))
        .toList();
  }
}
