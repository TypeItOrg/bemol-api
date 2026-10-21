package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCourseLevelOptionsUseCase {
  private final AcademicLevelRepository academicLevelRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicLevelResponse> execute(
      final UUID institutionId,
      final UUID studyPlanId,
      final @Nullable String search,
      final @Nullable UUID id,
      final Pageable pageable) {
    return PaginatedResponse.from(
        academicLevelRepository
            .findCourseFilterOptions(
                institutionId,
                studyPlanId,
                AcademicNameNormalizer.search(search),
                id,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
            .map(AcademicLevelResponse::from));
  }
}
