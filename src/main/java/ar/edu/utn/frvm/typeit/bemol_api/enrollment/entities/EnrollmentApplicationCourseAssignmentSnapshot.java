package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
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
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "enrollment_application_course_assignment_snapshots")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentApplicationCourseAssignmentSnapshot extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_application_course_assignment_snapshot_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_course_id", nullable = false)
  private EnrollmentApplicationCourse applicationCourse;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course course;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_id", nullable = false)
  private CourseClass courseClass;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_schedule_id", nullable = false)
  private CourseClassSchedule schedule;

  @Column(name = "course_individual_slot_id")
  private @Nullable UUID individualSlotId;

  @Column(name = "course_name", nullable = false)
  private String courseName;

  @Column(name = "training_path_name", nullable = false)
  private String trainingPathName;

  @Column(name = "study_plan_name")
  private String studyPlanName;

  @Column(name = "academic_space_name", nullable = false)
  private String academicSpaceName;

  @Column(name = "academic_level_name")
  private @Nullable String academicLevelName;

  @Column(name = "instrument_name")
  private @Nullable String instrumentName;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 20)
  private CourseDay dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "assigned_by_person_id")
  private @Nullable UUID assignedByPersonId;

  @Column(nullable = false, length = 80)
  private String operation;
}
