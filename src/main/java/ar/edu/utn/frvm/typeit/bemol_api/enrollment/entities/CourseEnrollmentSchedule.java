package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "course_enrollment_schedules")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CourseEnrollmentSchedule extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_enrollment_schedule_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_enrollment_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private CourseEnrollment courseEnrollment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_schedule_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private CourseClassSchedule schedule;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_individual_slot_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable CourseIndividualSlot individualSlot;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 20)
  @Audited
  private CourseDay dayOfWeek;

  @Column(name = "start_time", nullable = false)
  @Audited
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  @Audited
  private LocalTime endTime;

  @Column(name = "released_at")
  @Audited
  private Instant releasedAt;

  public static CourseEnrollmentSchedule create(
      final Institution institution,
      final CourseEnrollment courseEnrollment,
      final CourseClassSchedule schedule,
      final @Nullable CourseIndividualSlot individualSlot,
      final CourseDay dayOfWeek,
      final LocalTime startTime,
      final LocalTime endTime) {
    return CourseEnrollmentSchedule.builder()
        .institution(institution)
        .courseEnrollment(courseEnrollment)
        .schedule(schedule)
        .individualSlot(individualSlot)
        .dayOfWeek(dayOfWeek)
        .startTime(startTime)
        .endTime(endTime)
        .build();
  }

  public void release(final Instant releasedAt) {
    this.releasedAt = releasedAt;
  }
}
