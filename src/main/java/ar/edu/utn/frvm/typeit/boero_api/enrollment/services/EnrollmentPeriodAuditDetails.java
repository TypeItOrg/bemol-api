package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

final class EnrollmentPeriodAuditDetails {
  private EnrollmentPeriodAuditDetails() {}

  static Map<String, Object> snapshot(final EnrollmentPeriod period) {
    return Map.of(
        "name",
        period.getName(),
        "academicYearId",
        period.getAcademicYear().getId().toString(),
        "startDate",
        date(period.getStartDate()),
        "endDate",
        date(period.getEndDate()),
        "status",
        period.getStatus().name(),
        "offeringIds",
        period.getOfferings().stream()
            .flatMap(
                offering ->
                    offering.getLevels().stream()
                        .map(
                            level ->
                                offering.getStudyPlan().getId()
                                    + "/"
                                    + (level.levelId() == null ? "unassigned" : level.levelId())))
            .sorted()
            .toList());
  }

  private static String date(final Instant value) {
    // PostgreSQL/JDBC rounds timestamps to microseconds; compare at the stored precision.
    return value.plusNanos(500).truncatedTo(ChronoUnit.MICROS).toString();
  }
}
