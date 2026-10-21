package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAvailableEnrollmentTrainingPathsUseCase {

  private final Clock clock;
  private final ApplicantEnrollmentGuard applicantEnrollmentGuard;
  private final TrainingPathRepository trainingPathRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<TrainingPathResponse> execute(
      final JwtAuthenticatedUser principal,
      final @Nullable UUID periodId,
      final Pageable pageable) {
    applicantEnrollmentGuard.requireApplicant(principal);

    final UUID institutionId = principal.institutionId();

    return PaginatedResponse.from(
        trainingPathRepository
            .findOfferedForEnrollment(institutionId, clock.instant(), periodId, pageable)
            .map(TrainingPathResponse::from));
  }
}
