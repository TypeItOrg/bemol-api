package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeAudit.GradeSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourseEnrollmentGradeCommands {

  private final CourseEnrollmentGradeSupport support;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeJournal journal;

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGradeResponse create(
      final UUID institutionId,
      final UUID enrollmentId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable UUID actorPersonId) {
    support.lockInstitution(institutionId);
    final CourseEnrollment enrollment =
        support.findEnrollmentForUpdate(institutionId, enrollmentId);
    support.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade =
        CourseEnrollmentGrade.create(
            enrollment.getInstitution(), enrollment, evaluation, value, actorPersonId);

    if (gradeRepository.existsDuplicateEvaluation(
        enrollment.getId(), grade.getEvaluation(), null)) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_DUPLICATE);
    }

    try {
      gradeRepository.saveAndFlush(grade);
    } catch (final DataIntegrityViolationException exception) {
      throw support.translateIntegrityViolation(exception);
    }

    journal.recordCreated(enrollment.getInstitution(), enrollment, grade, actorPersonId);

    log.info(
        "[Grades] Created draft, institution: {}, enrollment: {}, grade: {}",
        institutionId,
        enrollmentId,
        grade.getId());

    return support.toManagementResponses(List.of(grade)).get(0);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGradeResponse update(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    support.lockInstitution(institutionId);

    final CourseEnrollment enrollment =
        support.findEnrollmentForUpdate(institutionId, enrollmentId);
    support.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = support.findGradeForUpdate(institutionId, gradeId);
    support.requireGradeBelongsToEnrollment(grade, enrollment);
    support.ensureExpectedVersion(grade.getVersion(), expectedVersion);

    grade.requireEditable();

    final String normalizedEvaluation = CourseEnrollmentGrade.normalizeEvaluation(evaluation);
    final BigDecimal normalizedValue = CourseEnrollmentGrade.normalizeValue(value);

    if (gradeRepository.existsDuplicateEvaluation(
        enrollment.getId(), normalizedEvaluation, grade.getId())) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_DUPLICATE);
    }

    final GradeSnapshot before = GradeSnapshot.of(grade);

    if (grade.getEvaluation().equals(normalizedEvaluation)
        && grade.getValue().compareTo(normalizedValue) == 0) {
      return support.toManagementResponses(List.of(grade)).get(0);
    }

    grade.applyNormalizedWorkingCopy(normalizedEvaluation, normalizedValue, actorPersonId);

    try {
      gradeRepository.flush();
    } catch (final DataIntegrityViolationException exception) {
      throw support.translateIntegrityViolation(exception);
    }

    journal.recordUpdated(enrollment.getInstitution(), enrollment, grade, before, actorPersonId);

    return support.toManagementResponses(List.of(grade)).get(0);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public DeleteOutcome deleteDraftOrMark(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    support.lockInstitution(institutionId);

    final CourseEnrollment enrollment =
        support.findEnrollmentForUpdate(institutionId, enrollmentId);
    support.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = support.findGradeForUpdate(institutionId, gradeId);
    support.requireGradeBelongsToEnrollment(grade, enrollment);
    support.ensureExpectedVersion(grade.getVersion(), expectedVersion);

    if (grade.isDraft()) {
      journal.recordDeletedDraft(enrollment.getInstitution(), enrollment, grade, actorPersonId);

      gradeRepository.delete(grade);
      gradeRepository.flush();

      return DeleteOutcome.DeletedImmediately.INSTANCE;
    }

    if (grade.publicationStatus() == CourseEnrollmentGradePublicationStatus.PENDING_DELETION) {
      return new DeleteOutcome.MarkedForDeletion(
          support.toManagementResponses(List.of(grade)).get(0));
    }

    final GradeSnapshot before = GradeSnapshot.of(grade);

    grade.markForDeletion(actorPersonId);
    gradeRepository.flush();

    journal.recordMarked(enrollment.getInstitution(), enrollment, grade, before, actorPersonId);

    return new DeleteOutcome.MarkedForDeletion(
        support.toManagementResponses(List.of(grade)).get(0));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGradeResponse cancelDeletion(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    support.lockInstitution(institutionId);

    final CourseEnrollment enrollment =
        support.findEnrollmentForUpdate(institutionId, enrollmentId);
    support.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = support.findGradeForUpdate(institutionId, gradeId);
    support.requireGradeBelongsToEnrollment(grade, enrollment);
    support.ensureExpectedVersion(grade.getVersion(), expectedVersion);

    if (!grade.isPendingDeletion()) {
      return support.toManagementResponses(List.of(grade)).get(0);
    }

    final GradeSnapshot before = GradeSnapshot.of(grade);

    grade.cancelDeletion(actorPersonId);
    gradeRepository.flush();

    journal.recordCancelled(enrollment.getInstitution(), enrollment, grade, before, actorPersonId);

    return support.toManagementResponses(List.of(grade)).get(0);
  }

  public sealed interface DeleteOutcome {
    enum DeletedImmediately implements DeleteOutcome {
      INSTANCE
    }

    record MarkedForDeletion(CourseEnrollmentGradeResponse grade) implements DeleteOutcome {}
  }
}
