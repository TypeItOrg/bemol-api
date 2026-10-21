package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListCoursesUseCaseTest {

  @Mock private CourseRepository courseRepository;
  @InjectMocks private ListCoursesUseCase useCase;

  private Pageable capturePageable() {
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(courseRepository)
        .findByFilters(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(false),
            eq(false),
            pageable.capture());
    return pageable.getValue();
  }

  @Test
  @DisplayName("Should map public sort fields to entity paths")
  void mapsPublicSortFields() {
    given(
            courseRepository.findByFilters(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(false), eq(false), any()))
        .willReturn(Page.empty());

    useCase.execute(
        UUID.randomUUID(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        PageRequest.of(
            0,
            10,
            Sort.by(
                Sort.Order.asc("academicSpace.name"),
                Sort.Order.desc("trainingPathName"),
                Sort.Order.asc("studyPlanName"),
                Sort.Order.desc("academicYear"),
                Sort.Order.asc("institution.name"))));

    final var sort = capturePageable().getSort();
    assertThat(sort.getOrderFor("studyPlanSpace.academicSpace.name"))
        .extracting(mappedOrder -> mappedOrder.getDirection())
        .isEqualTo(Sort.Direction.ASC);
    assertThat(sort.getOrderFor("studyPlanSpace.studyPlan.trainingPath.name"))
        .extracting(mappedOrder -> mappedOrder.getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(sort.getOrderFor("studyPlanSpace.studyPlan.name"))
        .extracting(mappedOrder -> mappedOrder.getDirection())
        .isEqualTo(Sort.Direction.ASC);
    assertThat(sort.getOrderFor("academicYear.year"))
        .extracting(mappedOrder -> mappedOrder.getDirection())
        .isEqualTo(Sort.Direction.DESC);
    assertThat(sort.getOrderFor("institution.name")).isNotNull();
  }

  @Test
  @DisplayName("Should reject unknown sort fields instead of failing on the query")
  void rejectsUnknownSortField() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    PageRequest.of(0, 10, Sort.by(Sort.Order.asc("year")))))
        .isInstanceOf(AcademicValidationException.class);
  }
}
