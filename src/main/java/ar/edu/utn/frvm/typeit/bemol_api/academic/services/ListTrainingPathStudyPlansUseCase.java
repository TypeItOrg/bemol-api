package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.StudyPlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListTrainingPathStudyPlansUseCase {
  private final StudyPlanRepository studyPlanRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<StudyPlanResponse> execute(
      final UUID institutionId, final UUID trainingPathId, final Pageable pageable) {
    return PaginatedResponse.from(
        studyPlanRepository
            .findByTrainingPath_IdAndInstitution_IdAndDeletedAtIsNull(
                trainingPathId, institutionId, pageable)
            .map(StudyPlanResponse::from));
  }
}
