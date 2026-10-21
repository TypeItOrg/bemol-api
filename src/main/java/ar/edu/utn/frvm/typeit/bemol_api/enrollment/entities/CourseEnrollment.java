package ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentSource;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Student;
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
@Table(name = "course_enrollments")
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CourseEnrollment extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "course_enrollment_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Student student;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Course course;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_class_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private CourseClass courseClass;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Audited
  private CourseEnrollmentSource source;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "enrollment_application_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private @Nullable EnrollmentApplication enrollmentApplication;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "enrollment_application_course_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private EnrollmentApplicationCourse applicationCourse;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  @Builder.Default
  @Audited
  private CourseEnrollmentStatus status = CourseEnrollmentStatus.ENROLLED;

  @Enumerated(EnumType.STRING)
  @Column(name = "academic_status", nullable = false, length = 30)
  @Builder.Default
  @Audited
  private AcademicEnrollmentStatus academicStatus = AcademicEnrollmentStatus.IN_PROGRESS;

  @Column(name = "enrolled_at", nullable = false)
  @Audited
  private Instant enrolledAt;

  @Column(name = "completed_at")
  @Audited
  private Instant completedAt;

  @Column(name = "withdrawn_at")
  @Audited
  private Instant withdrawnAt;

  @Column(name = "action_authority_person_id")
  @Audited
  private @Nullable UUID actionAuthorityPersonId;

  @Column(name = "action_reason")
  private String actionReason;

  @Version
  @Column(nullable = false)
  private long version;

  public static CourseEnrollment create(
      final Institution institution,
      final Student student,
      final Course course,
      final CourseClass courseClass,
      final CourseEnrollmentSource source,
      final @Nullable EnrollmentApplication application,
      final Instant enrolledAt) {
    return CourseEnrollment.builder()
        .institution(institution)
        .student(student)
        .course(course)
        .courseClass(courseClass)
        .source(source)
        .enrollmentApplication(application)
        .status(CourseEnrollmentStatus.ENROLLED)
        .academicStatus(AcademicEnrollmentStatus.IN_PROGRESS)
        .enrolledAt(enrolledAt)
        .build();
  }

  public void assignApplicationCourse(final EnrollmentApplicationCourse selection) {
    if (enrollmentApplication == null
        || !selection.getEnrollmentApplication().getId().equals(enrollmentApplication.getId())
        || !selection.getCourse().getId().equals(course.getId())) {
      throw new EnrollmentValidationException(EnrollmentMessages.COURSE_APPLICATION_NOT_FOUND);
    }
    applicationCourse = selection;
  }

  public void complete(final Instant completedAt) {
    status = CourseEnrollmentStatus.COMPLETED;
    this.completedAt = completedAt;
    if (academicStatus == AcademicEnrollmentStatus.IN_PROGRESS) {
      academicStatus = AcademicEnrollmentStatus.PENDING_RESULT;
    }
  }

  public void withdraw(
      final CourseEnrollmentStatus target,
      final Instant withdrawnAt,
      final @Nullable UUID authorityPersonId,
      final String reason) {
    status = target;
    academicStatus = AcademicEnrollmentStatus.NOT_APPLICABLE;
    this.withdrawnAt = withdrawnAt;
    this.actionAuthorityPersonId = authorityPersonId;
    this.actionReason = reason;
  }

  public void recordAcademicResult(
      final AcademicEnrollmentStatus target, final Instant completedAt, final String reason) {
    if (status == CourseEnrollmentStatus.WITHDRAWN
        || status == CourseEnrollmentStatus.ADMINISTRATIVELY_WITHDRAWN
        || target == AcademicEnrollmentStatus.IN_PROGRESS
        || target == AcademicEnrollmentStatus.PENDING_RESULT
        || target == AcademicEnrollmentStatus.NOT_APPLICABLE) {
      throw new EnrollmentValidationException(EnrollmentMessages.ACADEMIC_TRANSITION_INVALID);
    }
    if (status == CourseEnrollmentStatus.ENROLLED) {
      status = CourseEnrollmentStatus.COMPLETED;
      this.completedAt = completedAt;
    }
    academicStatus = target;
    actionReason = reason;
  }
}
