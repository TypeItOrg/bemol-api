package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
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

  private final CourseEnrollmentGradeStore store;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeResponseMapper responses;
  private final CourseEnrollmentGradeJournal journal;

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGradeResponse create(
      final UUID institutionId,
      final UUID enrollmentId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable UUID actorPersonId) {
    final Institution institution = store.lockInstitution(institutionId);
    final CourseEnrollment enrollment = store.findEnrollmentForUpdate(institutionId, enrollmentId);
    store.requireEnrolled(enrollment);

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
      throw store.translateIntegrityViolation(exception);
    }

    journal.recordCreated(institution, enrollment, grade, actorPersonId);

    log.info(
        "[Grades] Created draft, institution: {}, enrollment: {}, grade: {}",
        institutionId,
        enrollmentId,
        grade.getId());

    return responses.toManagementResponses(List.of(grade)).get(0);
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
    store.lockInstitution(institutionId);

    final CourseEnrollment enrollment = store.findEnrollmentForUpdate(institutionId, enrollmentId);
    store.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = store.findGradeForUpdate(institutionId, gradeId);
    store.requireGradeBelongsToEnrollment(grade, enrollment);
    store.ensureExpectedVersion(grade.getVersion(), expectedVersion);

    final String normalizedEvaluation = CourseEnrollmentGrade.normalizeEvaluation(evaluation);
    final BigDecimal normalizedValue = CourseEnrollmentGrade.normalizeValue(value);

    if (gradeRepository.existsDuplicateEvaluation(
        enrollment.getId(), normalizedEvaluation, grade.getId())) {
      throw new EnrollmentValidationException(GradeMessages.EVALUATION_DUPLICATE);
    }

    final String evaluationBefore = grade.getEvaluation();
    final BigDecimal valueBefore = grade.getValue();
    final String publishedEvaluationBefore = grade.getPublishedEvaluation();
    final BigDecimal publishedValueBefore = grade.getPublishedValue();
    final var previousStatus = grade.publicationStatus();

    if (evaluationBefore.equals(normalizedEvaluation)
        && valueBefore.compareTo(normalizedValue) == 0) {
      return responses.toManagementResponses(List.of(grade)).get(0);
    }

    grade.updateWorkingCopy(evaluation, value, actorPersonId);

    try {
      gradeRepository.flush();
    } catch (final DataIntegrityViolationException exception) {
      throw store.translateIntegrityViolation(exception);
    }

    journal.recordUpdated(
        enrollment.getInstitution(),
        enrollment,
        grade,
        evaluationBefore,
        valueBefore,
        publishedEvaluationBefore,
        publishedValueBefore,
        previousStatus,
        actorPersonId);

    return responses.toManagementResponses(List.of(grade)).get(0);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public DeleteOutcome deleteDraftOrMark(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    store.lockInstitution(institutionId);

    final CourseEnrollment enrollment = store.findEnrollmentForUpdate(institutionId, enrollmentId);
    store.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = store.findGradeForUpdate(institutionId, gradeId);
    store.requireGradeBelongsToEnrollment(grade, enrollment);
    store.ensureExpectedVersion(grade.getVersion(), expectedVersion);

    if (grade.isDraft()) {
      journal.recordDeletedDraft(enrollment.getInstitution(), enrollment, grade, actorPersonId);

      gradeRepository.delete(grade);
      gradeRepository.flush();

      return new DeleteOutcome(true, null);
    }

    final var previousStatus = grade.publicationStatus();

    if (previousStatus == CourseEnrollmentGradePublicationStatus.PENDING_DELETION) {
      return new DeleteOutcome(false, responses.toManagementResponses(List.of(grade)).get(0));
    }

    final String evaluationBefore = grade.getEvaluation();
    final BigDecimal valueBefore = grade.getValue();

    grade.markForDeletion(actorPersonId);
    gradeRepository.flush();

    journal.recordMarked(
        enrollment.getInstitution(),
        enrollment,
        grade,
        evaluationBefore,
        valueBefore,
        previousStatus,
        actorPersonId);

    return new DeleteOutcome(false, responses.toManagementResponses(List.of(grade)).get(0));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public CourseEnrollmentGradeResponse cancelDeletion(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable UUID actorPersonId) {
    store.lockInstitution(institutionId);

    final CourseEnrollment enrollment = store.findEnrollmentForUpdate(institutionId, enrollmentId);
    store.requireEnrolled(enrollment);

    final CourseEnrollmentGrade grade = store.findGradeForUpdate(institutionId, gradeId);
    store.requireGradeBelongsToEnrollment(grade, enrollment);

    final var previousStatus = grade.publicationStatus();

    grade.cancelDeletion(actorPersonId);
    gradeRepository.flush();

    final var newStatus = grade.publicationStatus();

    if (previousStatus == newStatus) {
      return responses.toManagementResponses(List.of(grade)).get(0);
    }

    journal.recordCancelled(
        enrollment.getInstitution(), enrollment, grade, previousStatus, actorPersonId);

    return responses.toManagementResponses(List.of(grade)).get(0);
  }

  public record DeleteOutcome(
      boolean deletedImmediately, @Nullable CourseEnrollmentGradeResponse grade) {}
}
