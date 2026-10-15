package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeHistoryRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.math.BigDecimal;
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
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      final Institution institution,
      final CourseEnrollment enrollment,
      final UUID gradeId,
      final CourseEnrollmentGradeHistoryOperation operation,
      final @Nullable String evaluationBefore,
      final @Nullable BigDecimal valueBefore,
      final @Nullable String evaluationAfter,
      final @Nullable BigDecimal valueAfter,
      final @Nullable String publishedEvaluationBefore,
      final @Nullable BigDecimal publishedValueBefore,
      final @Nullable String publishedEvaluationAfter,
      final @Nullable BigDecimal publishedValueAfter,
      final @Nullable CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable CourseEnrollmentGradePublicationStatus newStatus,
      final @Nullable UUID actorPersonId) {
    history.save(
        CourseEnrollmentGradeHistory.create(
            institution,
            enrollment,
            gradeId,
            operation,
            evaluationBefore,
            valueBefore,
            evaluationAfter,
            valueAfter,
            publishedEvaluationBefore,
            publishedValueBefore,
            publishedEvaluationAfter,
            publishedValueAfter,
            previousStatus,
            newStatus,
            actorPersonId,
            clock.instant()));
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
        null,
        grade.getEvaluation(),
        grade.getValue(),
        null,
        null,
        null,
        null,
        null,
        grade.publicationStatus(),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordUpdated(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final String evaluationBefore,
      final BigDecimal valueBefore,
      final @Nullable String publishedEvaluationBefore,
      final @Nullable BigDecimal publishedValueBefore,
      final CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.UPDATED,
        evaluationBefore,
        valueBefore,
        grade.getEvaluation(),
        grade.getValue(),
        publishedEvaluationBefore,
        publishedValueBefore,
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        previousStatus,
        grade.publicationStatus(),
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
        grade.getEvaluation(),
        grade.getValue(),
        null,
        null,
        null,
        null,
        null,
        null,
        grade.publicationStatus(),
        null,
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordMarked(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final String evaluationBefore,
      final BigDecimal valueBefore,
      final CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.MARKED_FOR_DELETION,
        evaluationBefore,
        valueBefore,
        grade.getEvaluation(),
        grade.getValue(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        previousStatus,
        grade.publicationStatus(),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCancelled(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable UUID actorPersonId) {
    record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.CANCELLED_DELETION,
        grade.getEvaluation(),
        grade.getValue(),
        grade.getEvaluation(),
        grade.getValue(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        previousStatus,
        grade.publicationStatus(),
        actorPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordPublished(
      final CourseEnrollmentGrade grade,
      final @Nullable String publishedEvaluationBefore,
      final @Nullable BigDecimal publishedValueBefore,
      final CourseEnrollmentGradePublicationStatus previousStatus,
      final UUID publisherPersonId) {
    record(
        grade.getInstitution(),
        grade.getCourseEnrollment(),
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.PUBLISHED,
        grade.getEvaluation(),
        grade.getValue(),
        grade.getEvaluation(),
        grade.getValue(),
        publishedEvaluationBefore,
        publishedValueBefore,
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        previousStatus,
        grade.publicationStatus(),
        publisherPersonId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDeletedPublished(
      final CourseEnrollmentGrade grade, final UUID publisherPersonId) {
    record(
        grade.getInstitution(),
        grade.getCourseEnrollment(),
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.DELETED_PUBLISHED,
        grade.getEvaluation(),
        grade.getValue(),
        null,
        null,
        grade.getPublishedEvaluation(),
        grade.getPublishedValue(),
        null,
        null,
        grade.publicationStatus(),
        null,
        publisherPersonId);
  }
}
