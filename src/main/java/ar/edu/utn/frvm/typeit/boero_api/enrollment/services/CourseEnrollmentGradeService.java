package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingClassGradesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PublishGradesResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentGradeService {

  private final CourseEnrollmentGradeQueries queries;
  private final CourseEnrollmentGradeCommands commands;
  private final CourseEnrollmentGradePublish publisher;
  private final CourseEnrollmentGradeAccess access;

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeResponse> listForManagement(
      final UUID institutionId, final UUID enrollmentId) {
    return queries.listForManagement(institutionId, enrollmentId);
  }

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listPublished(
      final UUID institutionId, final UUID enrollmentId) {
    return queries.listPublished(institutionId, enrollmentId);
  }

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listOwnPublished(
      final UUID institutionId, final UUID enrollmentId, final UUID personId) {
    return queries.listOwnPublished(institutionId, enrollmentId, personId);
  }

  @Transactional
  public CourseEnrollmentGradeResponse create(
      final UUID institutionId,
      final UUID enrollmentId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable UUID actorPersonId) {
    return commands.create(institutionId, enrollmentId, evaluation, value, actorPersonId);
  }

  @Transactional
  public CourseEnrollmentGradeResponse update(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    return commands.update(
        institutionId, enrollmentId, gradeId, evaluation, value, expectedVersion, actorPersonId);
  }

  @Transactional
  public DeleteResult delete(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion,
      final @Nullable UUID actorPersonId) {
    final var outcome =
        commands.deleteDraftOrMark(
            institutionId, enrollmentId, gradeId, expectedVersion, actorPersonId);

    return new DeleteResult(outcome.deletedImmediately(), outcome.grade());
  }

  @Transactional
  public CourseEnrollmentGradeResponse cancelDeletion(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable UUID actorPersonId) {
    return commands.cancelDeletion(institutionId, enrollmentId, gradeId, actorPersonId);
  }

  @Transactional(readOnly = true)
  public PendingGradesSummaryResponse pendingSummary(final UUID institutionId, final UUID classId) {
    return queries.pendingSummary(institutionId, classId);
  }

  @Transactional(readOnly = true)
  public List<PendingClassGradesResponse> pendingClasses(final UUID institutionId) {
    return queries.pendingClasses(institutionId);
  }

  @Transactional
  public PublishGradesResponse publishClass(
      final UUID institutionId, final UUID classId, final UUID publisherPersonId) {
    return publisher.publishClass(institutionId, classId, publisherPersonId);
  }

  @Transactional(readOnly = true)
  public Page<CourseEnrollmentGradeHistoryResponse> history(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final Pageable pageable) {
    return queries.history(institutionId, enrollmentId, gradeId, pageable);
  }

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      final UUID institutionId, final UUID enrollmentId) {
    return queries.archived(institutionId, enrollmentId);
  }

  @Transactional(readOnly = true)
  public void requireTeacherForClass(
      final UUID institutionId, final UUID personId, final UUID classId) {
    access.requireTeacherForClass(institutionId, personId, classId);
  }

  @Transactional(readOnly = true)
  public void requireEnrollmentInClass(
      final UUID institutionId, final UUID classId, final UUID enrollmentId) {
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
  }

  public record DeleteResult(
      boolean deletedImmediately, @Nullable CourseEnrollmentGradeResponse grade) {}
}
