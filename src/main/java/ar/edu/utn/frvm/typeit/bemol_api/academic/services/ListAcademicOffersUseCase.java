package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.AcademicOfferSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAcademicOffersUseCase {
  private static final Map<String, String> SORT_FIELDS =
      Map.of(
          "trainingPathName", "trainingPath.name",
          "studyPlanName", "name",
          "studyPlanVersion", "versionNumber",
          "effectiveFrom", "effectiveFrom",
          "effectiveTo", "effectiveTo");
  private final BusinessDateProvider businessDateProvider;
  private final Clock clock;
  private final EnrollmentPeriodRepository enrollmentPeriodRepository;

  private final StudyPlanRepository studyPlanRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<AcademicOfferSummaryResponse> execute(
      final UUID institutionId, final Pageable pageable) {
    final var plans =
        studyPlanRepository.findAvailableOffers(
            institutionId, businessDateProvider.today(), mapSort(pageable));
    final var planIds = plans.stream().map(plan -> plan.getId()).toList();
    final Set<UUID> openPlanIds =
        planIds.isEmpty()
            ? Set.of()
            : enrollmentPeriodRepository.findStudyPlanIdsWithOpenEnrollment(
                institutionId, planIds, clock.instant());

    return PaginatedResponse.from(
        plans.map(
            plan -> AcademicOfferSummaryResponse.from(plan, openPlanIds.contains(plan.getId()))));
  }

  private static Pageable mapSort(final Pageable pageable) {
    final List<Sort.Order> mappedOrders =
        pageable.getSort().stream().map(ListAcademicOffersUseCase::mapOrder).toList();
    final List<Sort.Order> orders =
        Stream.concat(mappedOrders.stream(), Stream.of(Sort.Order.asc("id"))).toList();
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
  }

  private static Sort.Order mapOrder(final Sort.Order order) {
    final String property = SORT_FIELDS.get(order.getProperty());
    if (property == null) {
      throw new AcademicValidationException(
          AcademicMessages.INVALID_SORT_FIELD, Map.of("sort", order.getProperty()));
    }
    return order.withProperty(property);
  }
}
