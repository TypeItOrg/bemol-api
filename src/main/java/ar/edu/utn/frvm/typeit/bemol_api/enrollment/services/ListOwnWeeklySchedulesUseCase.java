package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentScheduleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.OwnWeeklySchedulesResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListOwnWeeklySchedulesUseCase {
  private final CourseEnrollmentScheduleRepository scheduleRepository;
  private final BusinessDateProvider businessDateProvider;

  @Transactional(readOnly = true)
  public OwnWeeklySchedulesResponse execute(
      final UUID institutionId, final UUID personId, final LocalDate requestedDate) {
    final var date = requestedDate == null ? businessDateProvider.today() : requestedDate;
    if (date.getYear() < 1900 || date.getYear() > 2100) {
      throw new EnrollmentValidationException(EnrollmentMessages.SCHEDULE_WEEK_INVALID);
    }

    final var weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    final var weekEnd = weekStart.plusDays(6);
    final var assignments =
        scheduleRepository.findOwnInWeek(
            institutionId,
            personId,
            weekStart,
            weekEnd,
            weekStart.atStartOfDay(BusinessDateProvider.BUSINESS_ZONE).toInstant(),
            weekStart.plusWeeks(1).atStartOfDay(BusinessDateProvider.BUSINESS_ZONE).toInstant());
    final var groups = new LinkedHashMap<UUID, List<CourseEnrollmentSchedule>>();

    for (final var assignment : assignments) {
      if (appliesInWeek(assignment, weekStart)) {
        groups
            .computeIfAbsent(assignment.getCourseEnrollment().getId(), key -> new ArrayList<>())
            .add(assignment);
      }
    }

    final var enrollments =
        groups.values().stream()
            .map(
                group ->
                    CourseEnrollmentResponse.from(
                        group.getFirst().getCourseEnrollment(),
                        group.stream().map(CourseEnrollmentScheduleResponse::from).toList()))
            .toList();

    return new OwnWeeklySchedulesResponse(weekStart, weekEnd, enrollments);
  }

  private boolean appliesInWeek(
      final CourseEnrollmentSchedule assignment, final LocalDate weekStart) {
    final var enrollment = assignment.getCourseEnrollment();
    final var year = enrollment.getCourse().getAcademicYear();
    final var date =
        weekStart.with(
            TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(assignment.getDayOfWeek().name())));
    final var startsAt =
        date.atTime(assignment.getStartTime())
            .atZone(BusinessDateProvider.BUSINESS_ZONE)
            .toInstant();

    return !date.isBefore(year.getStartDate())
        && !date.isAfter(year.getEndDate())
        && !startsAt.isBefore(enrollment.getEnrolledAt())
        && !startsAt.isBefore(assignment.getCreatedAt())
        && beforeEnd(startsAt, assignment.getReleasedAt())
        && beforeEnd(startsAt, enrollment.getCompletedAt())
        && beforeEnd(startsAt, enrollment.getWithdrawnAt());
  }

  private boolean beforeEnd(final Instant startsAt, final Instant end) {
    return end == null || startsAt.isBefore(end);
  }
}
