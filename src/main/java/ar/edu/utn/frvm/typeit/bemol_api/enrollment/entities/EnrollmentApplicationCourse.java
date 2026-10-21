package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.WaitlistReason;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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
@Table(name = "enrollment_application_courses")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EnrollmentApplicationCourse extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "enrollment_application_course_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_application_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentApplication enrollmentApplication;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "enrollment_period_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable EnrollmentPeriod enrollmentPeriod;

  public void assignPeriod(final EnrollmentPeriod period) {
    if (enrollmentPeriod != null && !enrollmentPeriod.getId().equals(period.getId())) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_OUTSIDE_PERIOD);
    }
    enrollmentPeriod = period;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "preferred_teacher_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable Person preferredTeacher;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  @Audited
  private EnrollmentApplicationCourseStatus status = EnrollmentApplicationCourseStatus.PENDING;

  @Column(name = "requested_at")
  @Audited
  private Instant requestedAt;

  @Column(name = "submitted_with_capacity")
  @Audited
  private Boolean submittedWithCapacity;

  @Column(name = "waitlist_number")
  @Audited
  private Integer waitlistNumber;

  @Column(name = "waitlisted_at")
  @Audited
  private Instant waitlistedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "waitlist_reason", length = 80)
  @Audited
  private WaitlistReason waitlistReason;

  @Column(name = "resolved_at")
  @Audited
  private Instant resolvedAt;

  @Column(name = "resolved_by_person_id")
  @Audited
  private @Nullable UUID resolvedByPersonId;

  @Column(name = "resolution_reason_code", length = 80)
  @Audited
  private String resolutionReasonCode;

  @Column(name = "resolution_reason_text")
  private String resolutionReasonText;

  @Version
  @Column(nullable = false)
  private long version;

  public static EnrollmentApplicationCourse create(
      final Institution institution,
      final EnrollmentApplication application,
      final Course course,
      final @Nullable Person preferredTeacher) {
    return EnrollmentApplicationCourse.builder()
        .institution(institution)
        .enrollmentApplication(application)
        .course(course)
        .enrollmentPeriod(application.getEnrollmentPeriod())
        .preferredTeacher(preferredTeacher)
        .status(EnrollmentApplicationCourseStatus.PENDING)
        .build();
  }

  public boolean isPendingResolution() {
    return status == EnrollmentApplicationCourseStatus.PENDING
        || status == EnrollmentApplicationCourseStatus.WAITLISTED;
  }

  public void ensurePendingResolution() {
    if (!isPendingResolution()) {
      throw new EnrollmentValidationException(
          EnrollmentMessages.COURSE_APPLICATION_ALREADY_RESOLVED);
    }
  }

  public void markRequested(final Instant requestedAt, final boolean submittedWithCapacity) {
    this.requestedAt = requestedAt;
    this.submittedWithCapacity = submittedWithCapacity;
  }

  public void changePreferredTeacher(final @Nullable Person preferredTeacher) {
    this.preferredTeacher = preferredTeacher;
  }

  public void waitlist(
      final int waitlistNumber, final WaitlistReason reason, final Instant waitlistedAt) {
    ensurePendingResolution();
    status = EnrollmentApplicationCourseStatus.WAITLISTED;
    this.waitlistNumber = waitlistNumber;
    this.waitlistReason = reason;
    this.waitlistedAt = waitlistedAt;
  }

  public void enroll(final Instant resolvedAt, final @Nullable UUID resolvedByPersonId) {
    ensurePendingResolution();
    status = EnrollmentApplicationCourseStatus.ENROLLED;
    this.resolvedAt = resolvedAt;
    this.resolvedByPersonId = resolvedByPersonId;
  }

  public void reject(
      final String reasonCode,
      final String reasonText,
      final Instant resolvedAt,
      final @Nullable UUID resolvedByPersonId) {
    ensurePendingResolution();
    status = EnrollmentApplicationCourseStatus.REJECTED;
    this.resolutionReasonCode = reasonCode;
    this.resolutionReasonText = reasonText;
    this.resolvedAt = resolvedAt;
    this.resolvedByPersonId = resolvedByPersonId;
  }

  public void cancel(final Instant resolvedAt, final @Nullable UUID resolvedByPersonId) {
    ensurePendingResolution();
    status = EnrollmentApplicationCourseStatus.CANCELLED;
    this.resolvedAt = resolvedAt;
    this.resolvedByPersonId = resolvedByPersonId;
  }
}
