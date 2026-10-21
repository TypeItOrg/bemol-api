package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.TrainingPathScopeOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAssignableTrainingPathsUseCase {
  private final TrainingPathRepository paths;
  private final ScopedAuthorizationService authorization;

  @Transactional(readOnly = true)
  public PaginatedResponse<TrainingPathScopeOptionResponse> execute(
      UUID institutionId, String search, Pageable pageable) {
    var access = authorization.assignableTrainingPaths();
    return PaginatedResponse.from(
        paths
            .findAssignablePaths(
                institutionId,
                access.institutional(),
                access.trainingPathIds().isEmpty()
                    ? Set.of(new UUID(0, 0))
                    : access.trainingPathIds(),
                SearchNormalization.normalizeSearch(search),
                pageable)
            .map(p -> new TrainingPathScopeOptionResponse(p.getId(), p.getName())));
  }
}
