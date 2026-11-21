package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeHistoryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeAudit.GradeSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeJournal {

  private final CourseEnrollmentGradeHistoryRepository history;
  private final CourseEnrollmentGradeAudit audit;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final CourseEnrollment enrollment,
      final UUID gradeId,
      final CourseEnrollmentGradeHistoryOperation operation,
      final @Nullable GradeSnapshot before,
      final @Nullable GradeSnapshot after,
      final @Nullable UUID actorPersonId) {
    history.save(
        CourseEnrollmentGradeHistory.create(
            institution,
            enrollment,
            gradeId,
            operation,
            before == null ? null : before.evaluation(),
            before == null ? null : before.value(),
            after == null ? null : after.evaluation(),
            after == null ? null : after.value(),
            before == null ? null : before.publishedEvaluation(),
            before == null ? null : before.publishedValue(),
            after == null ? null : after.publishedEvaluation(),
            after == null ? null : after.publishedValue(),
            before == null ? null : before.status(),
            after == null ? null : after.status(),
            actorPersonId,
            clock.instant()));
    audit.record(institution, enrollment, gradeId, operation, before, after);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreated(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.CREATED,
        null,
        GradeSnapshot.of(grade),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordUpdated(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final GradeSnapshot before,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.UPDATED,
        before,
        GradeSnapshot.of(grade),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDeletedDraft(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.DELETED_DRAFT,
        GradeSnapshot.of(grade),
        null,
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordMarked(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final GradeSnapshot before,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.MARKED_FOR_DELETION,
        before,
        GradeSnapshot.of(grade),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCancelled(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final GradeSnapshot before,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.CANCELLED_DELETION,
        before,
        GradeSnapshot.of(grade),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordPublished(
      final CourseEnrollmentGrade grade,
      final GradeSnapshot before,
      final @Nullable UUID publisherPersonId) {
    record(
        grade.getInstitution(),
        grade.getCourseEnrollment(),
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.PUBLISHED,
        before,
        GradeSnapshot.of(grade),
        publisherPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDeletedPublished(
      final CourseEnrollmentGrade grade, final @Nullable UUID publisherPersonId) {
    record(
        grade.getInstitution(),
        grade.getCourseEnrollment(),
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.DELETED_PUBLISHED,
        GradeSnapshot.of(grade),
        null,
        publisherPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDiscardedDraftOnExit(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT,
        GradeSnapshot.of(grade),
        null,
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordRevertedOnExit(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final GradeSnapshot before,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT,
        before,
        GradeSnapshot.of(grade),
        actorPersonId);
  }
}
