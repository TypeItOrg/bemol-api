package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradeHistoryOperation;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Audits the grade cleanup performed when an enrollment leaves {@code ENROLLED}. */
@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeExitJournal {

  private final CourseEnrollmentGradeJournal journal;

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDiscardedDraft(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final @Nullable UUID actorPersonId) {
    journal.record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.DISCARDED_DRAFT_ON_EXIT,
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
  public void recordRevertedToPublished(
      final Institution institution,
      final CourseEnrollment enrollment,
      final CourseEnrollmentGrade grade,
      final String evaluationBefore,
      final BigDecimal valueBefore,
      final CourseEnrollmentGradePublicationStatus previousStatus,
      final @Nullable UUID actorPersonId) {
    journal.record(
        institution,
        enrollment,
        grade.getId(),
        CourseEnrollmentGradeHistoryOperation.REVERTED_TO_PUBLISHED_ON_EXIT,
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
}
