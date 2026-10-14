package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseResponse;
import ar.edu.utn.frvm.typeit.boero_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCoursesUseCase {
  private final CourseRepository courseRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseResponse> execute(
      final @Nullable UUID institutionId,
      final @Nullable String search,
      final @Nullable CourseStatus status,
      final @Nullable UUID academicSpaceId,
      final @Nullable UUID trainingPathId,
      final @Nullable UUID studyPlanId,
      final @Nullable Integer year,
      final @Nullable UUID academicLevelId,
      final @Nullable AcademicSpaceType type,
      final @Nullable AcademicSpaceFormat format,
      final @Nullable UUID instrumentId,
      final boolean withoutInstrument,
      final boolean deleted,
      final Pageable pageable) {
    if (withoutInstrument && instrumentId != null) {
      throw new AcademicValidationException(
          AcademicMessages.CONFLICTING_INSTRUMENT_FILTERS,
          Map.of("instrumentId", AcademicMessages.CONFLICTING_INSTRUMENT_FILTERS));
    }

    final Pageable repositoryPageable = mapSort(pageable);
    return PaginatedResponse.from(
        courseRepository
            .findByFilters(
                institutionId,
                AcademicNameNormalizer.search(search),
                status,
                academicSpaceId,
                trainingPathId,
                studyPlanId,
                year,
                academicLevelId,
                type,
                format,
                instrumentId,
                withoutInstrument,
                deleted,
                repositoryPageable)
            .map(CourseResponse::from));
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseResponse> enrollmentOptions(
      UUID institutionId, Pageable pageable) {
    return PaginatedResponse.from(
        courseRepository
            .findEnrollmentOptions(
                institutionId, null, CourseStatus.ACTIVE, null, null, null, null, false, pageable)
            .map(CourseResponse::from));
  }

  private static final Map<String, String> SORT_FIELDS =
      Map.of(
          "institution.name",
          "institution.name",
          "academicSpace.name",
          "studyPlanSpace.academicSpace.name",
          "trainingPathName",
          "studyPlanSpace.studyPlan.trainingPath.name",
          "studyPlanName",
          "studyPlanSpace.studyPlan.name",
          "academicYear",
          "academicYear.year",
          "academicLevel",
          "level.displayOrder",
          "academicSpace.type",
          "studyPlanSpace.academicSpace.type",
          "academicSpace.format",
          "studyPlanSpace.academicSpace.format",
          "instrument.name",
          "instrument.name",
          "status",
          "status");

  private static Pageable mapSort(final Pageable pageable) {
    final Sort sort =
        pageable.getSort().stream()
            .map(ListCoursesUseCase::mapOrder)
            .reduce(Sort.unsorted(), (left, right) -> left.and(right))
            .and(Sort.by("id"));
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }

  private static Sort mapOrder(final Sort.Order order) {
    final String property = SORT_FIELDS.get(order.getProperty());
    if (property == null) {
      throw new AcademicValidationException(
          AcademicMessages.INVALID_SORT_FIELD, Map.of("sort", order.getProperty()));
    }
    if (order.getProperty().equals("academicSpace.type")) {
      // Sort by the displayed Spanish labels, not the internal enum names.
      return JpaSort.unsafe(
          order.getDirection(),
          "(case course.studyPlanSpace.academicSpace.type when 'SUBJECT' then 'Asignatura' when 'OTHER' then 'Otro' when 'PRACTICE' then 'Práctica' when 'SEMINAR' then 'Seminario' when 'WORKSHOP' then 'Taller' end)");
    }

    final Sort.Order mapped = order.withProperty(property);
    return Sort.by(
        order.getProperty().equals("academicLevel") || order.getProperty().equals("instrument.name")
            ? mapped.nullsLast()
            : mapped);
  }

  public PaginatedResponse<CourseResponse> execute(
      final UUID institutionId,
      final @Nullable String search,
      final @Nullable CourseStatus status,
      final @Nullable UUID academicSpaceId,
      final @Nullable UUID trainingPathId,
      final @Nullable UUID studyPlanId,
      final @Nullable Integer year,
      final Pageable pageable) {
    return execute(
        institutionId,
        search,
        status,
        academicSpaceId,
        trainingPathId,
        studyPlanId,
        year,
        null,
        null,
        null,
        null,
        false,
        false,
        pageable);
  }
}
