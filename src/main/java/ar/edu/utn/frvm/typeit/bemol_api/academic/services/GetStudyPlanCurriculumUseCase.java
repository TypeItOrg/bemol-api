package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelCurriculumResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.PrerequisiteResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanCurriculumResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetStudyPlanCurriculumUseCase {
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanRepository studyPlanRepository;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final PrerequisiteRepository prerequisiteRepository;

  @Transactional(readOnly = true)
  public StudyPlanCurriculumResponse execute(final UUID institutionId, final UUID studyPlanId) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_READ, institutionId, ScopedResource.STUDY_PLAN, studyPlanId);

    final var plan =
        studyPlanRepository
            .findByIdAndInstitution_Id(studyPlanId, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    final var levels =
        academicLevelRepository.findByStudyPlan_IdOrderByDisplayOrderAsc(studyPlanId);
    final var spaces = studyPlanSpaceRepository.findByStudyPlanIdWithDetails(studyPlanId);
    final var prerequisites = prerequisiteRepository.findByStudyPlan_Id(studyPlanId);
    final var spaceResponses = spaces.stream().map(StudyPlanSpaceResponse::from).toList();
    final Map<UUID, List<StudyPlanSpaceResponse>> spacesByLevel =
        spaceResponses.stream()
            .filter(space -> space.academicLevelId() != null)
            .collect(
                Collectors.groupingBy(
                    mappedStudyPlanSpaceResponse ->
                        mappedStudyPlanSpaceResponse.academicLevelId()));
    final var levelResponses =
        levels.stream()
            .map(
                level ->
                    new AcademicLevelCurriculumResponse(
                        AcademicLevelResponse.from(level),
                        spacesByLevel.getOrDefault(level.getId(), List.of())))
            .toList();
    final var unassignedSpaces =
        spaceResponses.stream().filter(space -> space.academicLevelId() == null).toList();
    return new StudyPlanCurriculumResponse(
        StudyPlanResponse.from(plan),
        levelResponses,
        unassignedSpaces,
        prerequisites.stream().map(PrerequisiteResponse::from).toList());
  }
}
