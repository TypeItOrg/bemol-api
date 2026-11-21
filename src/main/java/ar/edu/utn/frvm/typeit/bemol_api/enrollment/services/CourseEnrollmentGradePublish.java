package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeAudit.GradeSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class CourseEnrollmentGradePublish {

  private final CourseEnrollmentGradeSupport support;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeJournal journal;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public PublishGradesResponse publishClass(
      final UUID institutionId, final UUID classId, final @Nullable UUID publisherPersonId) {
    support.lockInstitution(institutionId);

    final var courseClass = support.findClassForUpdate(institutionId, classId);
    final List<CourseEnrollmentGrade> grades =
        gradeRepository.findByClassForUpdate(institutionId, courseClass.getId());

    final List<CourseEnrollmentGrade> pending = new ArrayList<>();

    for (final CourseEnrollmentGrade grade : grades) {
      if (grade.hasPendingChanges()) {
        pending.add(grade);
      }
    }

    if (pending.isEmpty()) {
      throw new EnrollmentValidationException(GradeMessages.PUBLISH_NO_CHANGES);
    }

    for (final CourseEnrollmentGrade grade : pending) {
      support.requireEnrolled(grade.getCourseEnrollment());
    }

    final Instant now = clock.instant();
    final Set<UUID> affectedEnrollments = new HashSet<>();
    long published = 0;
    long deleted = 0;

    for (final CourseEnrollmentGrade grade : pending) {
      affectedEnrollments.add(grade.getCourseEnrollment().getId());

      if (grade.publicationStatus() == CourseEnrollmentGradePublicationStatus.PENDING_DELETION) {
        journal.recordDeletedPublished(grade, publisherPersonId);

        gradeRepository.delete(grade);
        deleted++;
        continue;
      }

      final GradeSnapshot before = GradeSnapshot.of(grade);

      grade.publish(publisherPersonId, now);
      published++;

      journal.recordPublished(grade, before, publisherPersonId);
    }

    try {
      gradeRepository.flush();
    } catch (final DataIntegrityViolationException exception) {
      throw support.translateIntegrityViolation(exception);
    }

    log.info(
        "[Grades] Published class, institution: {}, class: {}, published: {}, deleted: {}",
        institutionId,
        classId,
        published,
        deleted);

    return new PublishGradesResponse(published, deleted, affectedEnrollments.size());
  }
}
