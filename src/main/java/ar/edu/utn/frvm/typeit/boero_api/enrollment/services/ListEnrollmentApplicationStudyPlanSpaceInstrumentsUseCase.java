package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.StudyPlanSpaceInstrumentOptionResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentStudyPlanSpaceInstrumentOptionsResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentApplicationStudyPlanSpaceInstrumentsUseCase {

  private final ApplicantEnrollmentGuard applicantEnrollmentGuard;
  private final EnrollmentApplicationRepository enrollmentApplicationRepository;
  private final EnrollmentEffectiveStudyPlanResolver enrollmentEffectiveStudyPlanResolver;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;
  private final StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;

  @Transactional(readOnly = true)
  public EnrollmentStudyPlanSpaceInstrumentOptionsResponse execute(
      final JwtAuthenticatedUser principal, final UUID applicationId, final UUID studyPlanSpaceId) {
    applicantEnrollmentGuard.requireApplicant(principal);
    final var application =
        enrollmentApplicationRepository
            .findAccessibleByIdAndInstitutionId(
                principal.institutionId(), principal.personId(), applicationId)
            .orElseThrow(EnrollmentApplicationNotFoundException::new);
    final var effectiveStudyPlan =
        enrollmentEffectiveStudyPlanResolver.resolve(principal.institutionId(), application);
    final var eligibleSpaces =
        studyPlanSpaceRepository.findEligibleByIdInAndStudyPlanId(
            principal.institutionId(), effectiveStudyPlan.getId(), List.of(studyPlanSpaceId));

    if (eligibleSpaces.isEmpty()
        || !requireNonNull(application.getEnrollmentPeriod()).includes(eligibleSpaces.getFirst())) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.ENROLLMENT_APPLICATION_STUDY_PLAN_SPACE_INVALID,
          Map.of(
              "studyPlanSpaceId",
              EnrollmentMessages.ENROLLMENT_APPLICATION_STUDY_PLAN_SPACE_INVALID));
    }

    final var instruments =
        studyPlanSpaceInstrumentRepository.findActiveByStudyPlanSpaceId(
            principal.institutionId(), studyPlanSpaceId);
    final var options =
        instruments.stream()
            .map(
                relation ->
                    new StudyPlanSpaceInstrumentOptionResponse(
                        relation.getInstrument().getId(), relation.getInstrument().getName()))
            .toList();

    return new EnrollmentStudyPlanSpaceInstrumentOptionsResponse(
        studyPlanSpaceId, !options.isEmpty(), options);
  }
}
