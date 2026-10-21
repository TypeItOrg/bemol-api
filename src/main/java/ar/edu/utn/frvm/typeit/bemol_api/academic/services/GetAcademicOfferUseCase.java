package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicLevelRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferDetailResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferLevelResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferSpaceResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAcademicOfferUseCase {
  private final AcademicAccessGuard accessGuard;
  private final BusinessDateProvider businessDateProvider;
  private final Clock clock;
  private final EnrollmentPeriodRepository enrollmentPeriodRepository;

  private final StudyPlanRepository studyPlanRepository;
  private final AcademicLevelRepository academicLevelRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;

  @Transactional(readOnly = true)
  public AcademicOfferDetailResponse execute(final UUID institutionId, final UUID studyPlanId) {
    accessGuard.require(
        PermissionCode.ACADEMIC_OFFER_READ, institutionId, ScopedResource.STUDY_PLAN, studyPlanId);

    final var plan =
        studyPlanRepository
            .findAvailableOfferById(institutionId, studyPlanId, businessDateProvider.today())
            .orElseThrow(StudyPlanNotFoundException::new);
    final var spaces =
        studyPlanSpaceRepository.findActiveByStudyPlanIdWithDetails(studyPlanId).stream()
            .map(AcademicOfferSpaceResponse::from)
            .toList();
    final Map<UUID, List<AcademicOfferSpaceResponse>> spacesByLevel =
        spaces.stream()
            .filter(space -> space.academicLevelId() != null)
            .collect(
                Collectors.groupingBy(
                    mappedAcademicOfferSpaceResponse ->
                        mappedAcademicOfferSpaceResponse.academicLevelId()));
    final var levels =
        academicLevelRepository.findByStudyPlan_IdOrderByDisplayOrderAsc(studyPlanId).stream()
            .map(
                level ->
                    AcademicOfferLevelResponse.from(
                        level, spacesByLevel.getOrDefault(level.getId(), List.of())))
            .toList();
    final var unassignedSpaces =
        spaces.stream().filter(space -> space.academicLevelId() == null).toList();
    final boolean enrollmentOpen =
        enrollmentPeriodRepository
            .findStudyPlanIdsWithOpenEnrollment(
                institutionId, List.of(studyPlanId), clock.instant())
            .contains(studyPlanId);

    return new AcademicOfferDetailResponse(
        AcademicOfferSummaryResponse.from(plan, enrollmentOpen), levels, unassignedSpaces);
  }
}
