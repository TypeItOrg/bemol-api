package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseSpaceOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCourseSpaceOptionsUseCase {
  private final AcademicAccessGuard accessGuard;
  private final StudyPlanRepository studyPlanRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;

  @Transactional(readOnly = true)
  public List<CourseSpaceOptionResponse> execute(
      final UUID institutionId, final UUID studyPlanId, final @Nullable String search) {
    accessGuard.requireAny(
        Set.of(
            PermissionCode.COURSE_READ, PermissionCode.COURSE_CREATE, PermissionCode.COURSE_UPDATE),
        institutionId,
        ScopedResource.STUDY_PLAN,
        studyPlanId);
    final var plan =
        studyPlanRepository
            .findByIdAndInstitution_Id(studyPlanId, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    if (plan.getStatus() == StudyPlanStatus.DRAFT) {
      throw new AcademicConflictException(AcademicMessages.COURSE_STUDY_PLAN_NOT_ACTIVE);
    }
    final var normalizedSearchValue = AcademicNameNormalizer.search(search);
    final var normalizedSearch =
        normalizedSearchValue == null
            ? null
            : SearchNormalization.normalizeForComparison(normalizedSearchValue);
    final var spaces = new LinkedHashMap<UUID, CourseSpaceOptionResponse>();
    for (final var space : studyPlanSpaceRepository.findByStudyPlanIdWithDetails(studyPlanId)) {
      final var academicSpace = space.getAcademicSpace();
      if (normalizedSearch != null
          && !SearchNormalization.normalizeForComparison(academicSpace.getName())
              .contains(normalizedSearch)) {
        continue;
      }
      spaces.putIfAbsent(space.getId(), CourseSpaceOptionResponse.from(space));
    }
    return List.copyOf(spaces.values());
  }
}
