package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TrainingPathResponse;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentApplicationTrainingPathsUseCase {

  private final ApplicantEnrollmentGuard applicantEnrollmentGuard;
  private final EnrollmentApplicationRepository enrollmentApplicationRepository;

  @Transactional(readOnly = true)
  public List<TrainingPathResponse> execute(
      final JwtAuthenticatedUser principal, final UUID applicationId) {
    final var applicant = applicantEnrollmentGuard.requireApplicant(principal);
    final var application =
        enrollmentApplicationRepository
            .findAccessibleByIdAndInstitutionId(
                principal.institutionId(), applicant.getId(), applicationId)
            .orElseThrow(EnrollmentApplicationNotFoundException::new);

    if (application.getEnrollmentPeriod() == null) {
      return List.of(TrainingPathResponse.from(requireNonNull(application.getTrainingPath())));
    }
    return application.getEnrollmentPeriod().getOfferings().stream()
        .map(offering -> offering.getStudyPlan().getTrainingPath())
        .filter(path -> path.isActive() && path.getDeletedAt() == null)
        .distinct()
        .map(TrainingPathResponse::from)
        .toList();
  }
}
