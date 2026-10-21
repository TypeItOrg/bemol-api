package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListAcademicOffersUseCaseTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();

  @Mock private StudyPlanRepository studyPlanRepository;
  @Mock private EnrollmentPeriodRepository enrollmentPeriodRepository;
  @Mock private Clock clock;

  @Spy
  private BusinessDateProvider businessDateProvider =
      new BusinessDateProvider(Clock.fixed(Instant.parse("2026-09-09T02:00:00Z"), ZoneOffset.UTC));

  @InjectMocks private ListAcademicOffersUseCase useCase;

  @Test
  @DisplayName("Should map public sort fields and append the id tie breaker")
  void execute_mapsSortAndAppendsId() {
    final Pageable pageable =
        PageRequest.of(
            2, 10, Sort.by(Sort.Order.desc("trainingPathName"), Sort.Order.asc("effectiveFrom")));
    when(studyPlanRepository.findAvailableOffers(
            eq(INSTITUTION_ID), any(LocalDate.class), any(Pageable.class)))
        .thenReturn(Page.empty());

    useCase.execute(INSTITUTION_ID, pageable);

    final var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(studyPlanRepository)
        .findAvailableOffers(eq(INSTITUTION_ID), any(LocalDate.class), pageableCaptor.capture());
    final Pageable mappedPageable = pageableCaptor.getValue();
    assertThat(mappedPageable.getPageNumber()).isEqualTo(2);
    assertThat(mappedPageable.getPageSize()).isEqualTo(10);
    assertThat(mappedPageable.getSort().toList())
        .extracting(mappedOrder -> mappedOrder.getProperty())
        .containsExactly("trainingPath.name", "effectiveFrom", "id");
    assertThat(requireNonNull(mappedPageable.getSort().getOrderFor("id")).getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }

  @Test
  @DisplayName("Should reject sort fields that are not part of the public offer contract")
  void execute_rejectsUnsupportedSortField() {
    final Pageable pageable = PageRequest.of(0, 10, Sort.by("trainingPath.name"));

    assertThatThrownBy(() -> useCase.execute(INSTITUTION_ID, pageable))
        .isInstanceOf(AcademicValidationException.class);
  }
}
