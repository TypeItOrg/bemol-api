package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@Table(name = "course_class_schedules")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class CourseClassSchedule extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_class_schedule_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_day_id", nullable = false)
  @Audited
  private CourseClassDay day;

  @Column(name = "start_time", nullable = false)
  @Audited
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  @Audited
  private LocalTime endTime;

  public static CourseClassSchedule create(
      final Institution institution,
      final CourseClassDay day,
      final LocalTime startTime,
      final LocalTime endTime) {
    if (!startTime.isBefore(endTime)) {
      throw new AcademicValidationException(
          AcademicMessages.COURSE_SCHEDULE_INVALID,
          Map.of("schedules", AcademicMessages.COURSE_SCHEDULE_INVALID));
    }
    final var schedule =
        CourseClassSchedule.builder()
            .institution(institution)
            .day(day)
            .startTime(startTime)
            .endTime(endTime)
            .build();
    schedule.individualPeriodCount();
    return schedule;
  }

  public int individualPeriodCount() {
    final Integer periodMinutes = day.getPeriodDurationMinutes();
    if (periodMinutes == null) {
      return 0;
    }
    if (periodMinutes <= 0) {
      throw new AcademicValidationException(
          AcademicMessages.COURSE_PERIOD_DURATION_REQUIRED,
          Map.of("classes", AcademicMessages.COURSE_PERIOD_DURATION_REQUIRED));
    }

    final var period = Duration.ofMinutes(periodMinutes);
    final var duration = Duration.between(startTime, endTime);
    final long count = duration.dividedBy(period);
    if (count <= 0 || !period.multipliedBy(count).equals(duration)) {
      throw new AcademicValidationException(
          AcademicMessages.COURSE_PERIOD_DURATION_NOT_DIVISIBLE,
          Map.of("classes", AcademicMessages.COURSE_PERIOD_DURATION_NOT_DIVISIBLE));
    }

    return Math.toIntExact(count);
  }

  public int durationMinutes() {
    return (int) Duration.between(startTime, endTime).toMinutes();
  }

  public boolean overlaps(final CourseClassSchedule other) {
    return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
  }
}
