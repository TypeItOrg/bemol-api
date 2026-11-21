package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PublishGradesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.CourseEnrollmentGradeCommands.DeleteOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherCourseGradeService {

  private final CourseEnrollmentGradeAccess access;
  private final CourseEnrollmentGradeQueries queries;
  private final CourseEnrollmentGradeCommands commands;
  private final CourseEnrollmentGradePublish publisher;

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeResponse> list(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID classId,
      final UUID enrollmentId) {
    access.requireTeacherReadForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return queries.listForManagement(institutionId, enrollmentId);
  }

  @Transactional
  public CourseEnrollmentGradeResponse create(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID actorPersonId,
      final UUID classId,
      final UUID enrollmentId,
      final String evaluation,
      final BigDecimal value) {
    access.requireTeacherManageForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return commands.create(institutionId, enrollmentId, evaluation, value, actorPersonId);
  }

  @Transactional
  public CourseEnrollmentGradeResponse update(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID actorPersonId,
      final UUID classId,
      final UUID enrollmentId,
      final UUID gradeId,
      final String evaluation,
      final BigDecimal value,
      final @Nullable Long expectedVersion) {
    access.requireTeacherManageForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return commands.update(
        institutionId, enrollmentId, gradeId, evaluation, value, expectedVersion, actorPersonId);
  }

  @Transactional
  public DeleteOutcome delete(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID actorPersonId,
      final UUID classId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion) {
    access.requireTeacherManageForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return commands.deleteDraftOrMark(
        institutionId, enrollmentId, gradeId, expectedVersion, actorPersonId);
  }

  @Transactional
  public CourseEnrollmentGradeResponse cancelDeletion(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID actorPersonId,
      final UUID classId,
      final UUID enrollmentId,
      final UUID gradeId,
      final @Nullable Long expectedVersion) {
    access.requireTeacherManageForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return commands.cancelDeletion(
        institutionId, enrollmentId, gradeId, expectedVersion, actorPersonId);
  }

  @Transactional(readOnly = true)
  public PendingGradesSummaryResponse pendingSummary(
      final UUID institutionId, final UUID teacherPersonId, final UUID classId) {
    access.requireTeacherReadForClass(institutionId, teacherPersonId, classId);
    return queries.pendingSummary(institutionId, classId);
  }

  @Transactional(readOnly = true)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID classId,
      final UUID enrollmentId) {
    access.requireTeacherReadForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return queries.archived(institutionId, enrollmentId);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<CourseEnrollmentGradeHistoryResponse> history(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID classId,
      final UUID enrollmentId,
      final UUID gradeId,
      final Pageable pageable) {
    access.requireTeacherReadForClass(institutionId, teacherPersonId, classId);
    access.requireEnrollmentInClass(institutionId, classId, enrollmentId);
    return queries.history(institutionId, enrollmentId, gradeId, pageable);
  }

  @Transactional
  public PublishGradesResponse publishClass(
      final UUID institutionId,
      final UUID teacherPersonId,
      final UUID actorPersonId,
      final UUID classId) {
    access.requireTeacherManageForClass(institutionId, teacherPersonId, classId);
    return publisher.publishClass(institutionId, classId, actorPersonId);
  }
}
