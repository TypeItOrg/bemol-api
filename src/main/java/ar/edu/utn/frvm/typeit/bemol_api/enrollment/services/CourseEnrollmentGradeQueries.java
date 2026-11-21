package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeHistoryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseEnrollmentGradeRepository.PendingClassProjection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PendingClassGradesResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.security.handlers.SecurityErrorMessages;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeQueries {

  private static final int MAX_HISTORY_PAGE_SIZE = 100;
  private static final Set<String> ALLOWED_HISTORY_SORT_FIELDS = Set.of("changedAt", "operation");

  private final CourseEnrollmentGradeSupport support;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeHistoryRepository historyRepository;

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeResponse> listForManagement(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);
    final List<CourseEnrollmentGrade> grades =
        gradeRepository.findByEnrollment(institutionId, enrollment.getId());

    return support.toManagementResponses(grades);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listPublished(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);

    return gradeRepository.findPublishedByEnrollment(institutionId, enrollment.getId()).stream()
        .map(CourseEnrollmentGradeStudentResponse::from)
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listOwnPublished(
      final UUID institutionId, final UUID enrollmentId, final UUID personId) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);

    if (!enrollment.getStudent().getPerson().getId().equals(personId)) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }

    return gradeRepository.findPublishedByEnrollment(institutionId, enrollment.getId()).stream()
        .map(CourseEnrollmentGradeStudentResponse::from)
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public PendingGradesSummaryResponse pendingSummary(final UUID institutionId, final UUID classId) {
    final var courseClass = support.findClassReadOnly(institutionId, classId);
    final var summary = gradeRepository.summarizePendingByClass(institutionId, courseClass.getId());

    return new PendingGradesSummaryResponse(
        summary.pendingCount(),
        summary.affectedStudents(),
        summary.createdCount(),
        summary.modifiedCount(),
        summary.deletedCount());
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<PendingClassGradesResponse> pendingClasses(final UUID institutionId) {
    return gradeRepository.findPendingClasses(institutionId).stream()
        .map(
            row ->
                new PendingClassGradesResponse(
                    row.courseId(),
                    formatCourseName(row),
                    row.classId(),
                    "Clase " + row.classNumber(),
                    row.pendingChanges(),
                    row.affectedStudents()))
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public PaginatedResponse<CourseEnrollmentGradeHistoryResponse> history(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final Pageable pageable) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);
    final Page<CourseEnrollmentGradeHistory> page =
        historyRepository.findByGrade(
            institutionId, enrollment.getId(), gradeId, sanitizeHistoryPageable(pageable));

    // An out-of-range page arrives empty with totalElements > 0; it is only missing when the
    // current grade does not exist either.
    if (page.getTotalElements() == 0 && !gradeExists(institutionId, enrollment, gradeId)) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_NOT_FOUND);
    }

    final Map<UUID, Person> people = resolveActors(page.getContent());

    return PaginatedResponse.from(
        page.map(event -> CourseEnrollmentGradeHistoryResponse.from(event, people)));
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = support.findEnrollment(institutionId, enrollmentId);
    final List<CourseEnrollmentGradeHistory> latestDeleted =
        historyRepository.findLatestArchivedByEnrollment(institutionId, enrollment.getId());

    return latestDeleted.stream().map(CourseEnrollmentGradeArchivedResponse::from).toList();
  }

  private boolean gradeExists(
      final UUID institutionId, final CourseEnrollment enrollment, final UUID gradeId) {
    return gradeRepository
        .findByIdAndInstitutionId(gradeId, institutionId)
        .filter(grade -> grade.getCourseEnrollment().getId().equals(enrollment.getId()))
        .isPresent();
  }

  private Map<UUID, Person> resolveActors(final List<CourseEnrollmentGradeHistory> events) {
    final Set<UUID> personIds = new HashSet<>();

    for (final CourseEnrollmentGradeHistory event : events) {
      if (event.getActorPersonId() != null) {
        personIds.add(event.getActorPersonId());
      }
    }

    return support.resolvePeople(personIds);
  }

  private static Pageable sanitizeHistoryPageable(final Pageable pageable) {
    final int page = Math.max(0, pageable.getPageNumber());
    final int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_HISTORY_PAGE_SIZE));
    final List<Sort.Order> orders = new ArrayList<>();

    for (final Sort.Order order : pageable.getSort()) {
      if (ALLOWED_HISTORY_SORT_FIELDS.contains(order.getProperty())) {
        orders.add(new Sort.Order(order.getDirection(), order.getProperty()));
      }
    }

    if (orders.isEmpty()) {
      orders.add(Sort.Order.desc("changedAt"));
    }

    final Sort.Direction tieBreakerDirection = orders.get(0).getDirection();
    orders.add(new Sort.Order(tieBreakerDirection, "id"));

    return PageRequest.of(page, size, Sort.by(orders));
  }

  private static String formatCourseName(final PendingClassProjection row) {
    final List<String> parts = new ArrayList<>();
    parts.add(row.academicSpaceName());

    if (row.instrumentName() != null && !row.instrumentName().isBlank()) {
      parts.add(row.instrumentName());
    }

    if (row.academicLevelName() != null && !row.academicLevelName().isBlank()) {
      parts.add(row.academicLevelName());
    }

    return String.join(" · ", parts) + " (" + row.academicYear() + ")";
  }
}
