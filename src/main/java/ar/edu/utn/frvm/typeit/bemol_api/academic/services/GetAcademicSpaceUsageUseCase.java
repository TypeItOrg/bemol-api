package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceUsageWarningCode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicSpaceNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsagePlacementResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsagePlanResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsageResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsageSummary;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicSpaceUsageWarning;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAcademicSpaceUsageUseCase {
  private final AcademicSpaceRepository academicSpaceRepository;
  private final StudyPlanRepository studyPlanRepository;
  private final StudyPlanSpaceRepository studyPlanSpaceRepository;

  @Transactional(readOnly = true)
  public AcademicSpaceUsageResponse execute(
      final UUID institutionId, final UUID academicSpaceId, final Pageable pageable) {
    academicSpaceRepository
        .findByIdAndInstitution_Id(academicSpaceId, institutionId)
        .orElseThrow(AcademicSpaceNotFoundException::new);

    final var summaryProjection =
        studyPlanSpaceRepository.summarizeUsage(institutionId, academicSpaceId);
    final var plans =
        studyPlanRepository.findByAcademicSpaceIdAndInstitutionId(
            institutionId, academicSpaceId, pageable);
    final var planIds = plans.getContent().stream().map(plan -> plan.getId()).toList();
    final Map<UUID, List<AcademicSpaceUsagePlacementResponse>> placementsByPlanId =
        planIds.isEmpty()
            ? Map.of()
            : studyPlanSpaceRepository
                .findUsageDetails(institutionId, academicSpaceId, planIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        space -> space.getStudyPlan().getId(),
                        Collectors.mapping(
                            AcademicSpaceUsagePlacementResponse::from, Collectors.toList())));

    final var planResponses =
        plans.getContent().stream()
            .map(
                plan ->
                    AcademicSpaceUsagePlanResponse.from(
                        plan, placementsByPlanId.getOrDefault(plan.getId(), List.of())))
            .toList();
    final var blockingPlanCount =
        summaryProjection.getActivePlans() + summaryProjection.getDraftPlans();
    final var summary =
        new AcademicSpaceUsageSummary(
            summaryProjection.getTotalPlans(),
            summaryProjection.getActivePlans(),
            summaryProjection.getDraftPlans(),
            summaryProjection.getInactivePlans(),
            summaryProjection.getTotalPlacements(),
            summaryProjection.getUnassignedPlacements(),
            blockingPlanCount > 0);
    final var warnings =
        blockingPlanCount == 0
            ? List.<AcademicSpaceUsageWarning>of()
            : List.of(
                new AcademicSpaceUsageWarning(
                    AcademicSpaceUsageWarningCode.USED_IN_ACTIVE_OR_DRAFT_PLAN, blockingPlanCount));

    return new AcademicSpaceUsageResponse(
        summary,
        new PaginatedResponse<>(
            planResponses,
            plans.getNumber(),
            plans.getSize(),
            plans.getTotalElements(),
            plans.getTotalPages()),
        warnings);
  }
}
