package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CreateStudyPlanVersionRequest;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateStudyPlanVersionUseCase {
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanRepository studyPlanRepository;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final PrerequisiteRepository prerequisiteRepository;
  private final AcademicLifecycleService academicLifecycleService;

  @Transactional
  public StudyPlanResponse execute(
      final UUID institutionId, final UUID sourceId, final CreateStudyPlanVersionRequest request) {
    accessGuard.require(
        PermissionCode.STUDY_PLAN_CREATE, institutionId, ScopedResource.STUDY_PLAN, sourceId);

    final var source =
        studyPlanRepository
            .findByIdAndInstitution_IdForUpdate(sourceId, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    if (source.getStatus() == StudyPlanStatus.DRAFT) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_SOURCE_INVALID);
    }
    if (studyPlanRepository.existsByPreviousVersion_IdAndDeletedAtIsNull(sourceId)) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_ALREADY_EXISTS);
    }
    validateDates(source, request);
    final var name = AcademicNameNormalizer.display(request.name());
    final var versionNumber = source.getVersionNumber() + 1;
    if (studyPlanRepository.existsByNormalizedNameAndVersion(
        source.getTrainingPath().getId(), name, versionNumber)) {
      throw AcademicConflictException.forField("name", AcademicMessages.DUPLICATE_NAME);
    }

    try {
      final var version =
          studyPlanRepository.save(
              StudyPlan.createVersion(
                  source, name, request.effectiveFrom(), request.effectiveTo(), versionNumber));
      studyPlanRepository.flush();
      copyCurriculum(source, version);
      academicLifecycleService.recordStudyPlanVersionCreated(
          version.getInstitution(),
          version.getId(),
          source.getId(),
          Map.of(
              "entityName",
              version.getName(),
              "sourceVersionId",
              source.getId().toString(),
              "before",
              AcademicAuditDetails.plan(source),
              "after",
              AcademicAuditDetails.plan(version)));
      return StudyPlanResponse.from(version);
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  private void copyCurriculum(final StudyPlan source, final StudyPlan version) {
    final Map<UUID, AcademicLevel> levelsBySourceId = new HashMap<>();
    for (final var sourceLevel :
        academicLevelRepository.findByStudyPlan_IdOrderByDisplayOrderAsc(source.getId())) {
      final var level =
          academicLevelRepository.save(
              AcademicLevel.create(
                  version,
                  sourceLevel.getName(),
                  sourceLevel.getDisplayOrder(),
                  sourceLevel.getDescription()));
      levelsBySourceId.put(sourceLevel.getId(), level);
    }
    academicLevelRepository.flush();

    final Map<UUID, StudyPlanSpace> spacesBySourceId = new HashMap<>();
    for (final var sourceSpace :
        studyPlanSpaceRepository.findByStudyPlanIdWithDetails(source.getId())) {
      final var level =
          sourceSpace.getAcademicLevel() == null
              ? null
              : levelsBySourceId.get(sourceSpace.getAcademicLevel().getId());
      final var space =
          studyPlanSpaceRepository.save(
              StudyPlanSpace.create(
                  version.getInstitution(),
                  version,
                  sourceSpace.getAcademicSpace(),
                  level,
                  sourceSpace.getRequirementType(),
                  sourceSpace.getDisplayOrder(),
                  sourceSpace.getApprovalMode()));
      spacesBySourceId.put(sourceSpace.getId(), space);
    }
    studyPlanSpaceRepository.flush();

    for (final var sourcePrerequisite : prerequisiteRepository.findByStudyPlan_Id(source.getId())) {
      prerequisiteRepository.save(
          Prerequisite.create(
              version,
              requireNonNull(
                  spacesBySourceId.get(sourcePrerequisite.getTargetStudyPlanSpace().getId())),
              requireNonNull(
                  spacesBySourceId.get(sourcePrerequisite.getRequiredStudyPlanSpace().getId())),
              sourcePrerequisite.getRequirementStage(),
              sourcePrerequisite.getRequiredCondition()));
    }
    prerequisiteRepository.flush();
  }

  private static void validateDates(
      final StudyPlan source, final CreateStudyPlanVersionRequest request) {
    if (request.effectiveFrom() == null) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_START_REQUIRED);
    }
    if (request.effectiveTo() != null && request.effectiveFrom().isAfter(request.effectiveTo())) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_DATES_INVALID);
    }
    if (source.getStatus() == StudyPlanStatus.INACTIVE
        && (source.getEffectiveTo() == null
            || !request.effectiveFrom().isAfter(source.getEffectiveTo()))) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_DATE_OVERLAP);
    }
    if (source.getStatus() == StudyPlanStatus.ACTIVE
        && source.getEffectiveFrom() != null
        && !request.effectiveFrom().isAfter(source.getEffectiveFrom())) {
      throw new AcademicConflictException(AcademicMessages.STUDY_PLAN_VERSION_DATE_OVERLAP);
    }
  }
}
