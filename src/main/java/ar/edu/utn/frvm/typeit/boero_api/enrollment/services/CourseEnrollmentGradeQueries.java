package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.CourseEnrollmentGradePublicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.GradeMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeHistoryRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentGradeRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeArchivedResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeHistoryResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeStudentResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingClassGradesResponse;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.PendingGradesSummaryResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.security.handlers.SecurityErrorMessages;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeQueries {

  private final CourseEnrollmentGradeStore store;
  private final CourseEnrollmentGradeRepository gradeRepository;
  private final CourseEnrollmentGradeHistoryRepository historyRepository;
  private final CourseEnrollmentGradeResponseMapper responses;
  private final CourseEnrollmentGradeHistorySupport historySupport;

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeResponse> listForManagement(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);
    final List<CourseEnrollmentGrade> grades =
        gradeRepository.findByEnrollment(institutionId, enrollment.getId());

    return responses.toManagementResponses(grades);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listPublished(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);

    return gradeRepository.findPublishedByEnrollment(institutionId, enrollment.getId()).stream()
        .map(grade -> CourseEnrollmentGradeStudentResponse.from(grade))
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeStudentResponse> listOwnPublished(
      final UUID institutionId, final UUID enrollmentId, final UUID personId) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);

    if (!enrollment.getStudent().getPerson().getId().equals(personId)) {
      throw new AccessDeniedException(SecurityErrorMessages.DEFAULT_FORBIDDEN_MESSAGE);
    }

    return gradeRepository.findPublishedByEnrollment(institutionId, enrollment.getId()).stream()
        .map(grade -> CourseEnrollmentGradeStudentResponse.from(grade))
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public PendingGradesSummaryResponse pendingSummary(final UUID institutionId, final UUID classId) {
    final var courseClass = store.findClassReadOnly(institutionId, classId);
    final List<CourseEnrollmentGrade> pending =
        gradeRepository.findPendingByClass(institutionId, courseClass.getId());

    final Set<UUID> students = new HashSet<>();
    long created = 0;
    long modified = 0;
    long deleted = 0;

    for (final CourseEnrollmentGrade grade : pending) {
      students.add(grade.getCourseEnrollment().getId());
      final var status = grade.publicationStatus();

      if (status == CourseEnrollmentGradePublicationStatus.PENDING_DELETION) {
        deleted++;
      } else if (status == CourseEnrollmentGradePublicationStatus.DRAFT) {
        created++;
      } else {
        modified++;
      }
    }

    return new PendingGradesSummaryResponse(
        pending.size(), students.size(), created, modified, deleted);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<PendingClassGradesResponse> pendingClasses(final UUID institutionId) {
    final List<CourseEnrollmentGrade> pending = gradeRepository.findPendingDetails(institutionId);
    final Map<UUID, List<CourseEnrollmentGrade>> byClass =
        pending.stream()
            .collect(
                Collectors.groupingBy(
                    grade -> grade.getCourseEnrollment().getCourseClass().getId()));

    return byClass.values().stream()
        .map(
            classGrades -> {
              final var first = classGrades.get(0);
              final var courseClass = first.getCourseEnrollment().getCourseClass();
              final var course = courseClass.getCourse();
              final Set<UUID> students = new HashSet<>();

              for (final CourseEnrollmentGrade grade : classGrades) {
                students.add(grade.getCourseEnrollment().getId());
              }

              return new PendingClassGradesResponse(
                  course.getId(),
                  course.getAcademicSpace().getName(),
                  courseClass.getId(),
                  courseClass.displayName(),
                  classGrades.size(),
                  students.size());
            })
        .sorted(
            (left, right) -> {
              final int byCourse = left.courseName().compareToIgnoreCase(right.courseName());
              return byCourse != 0
                  ? byCourse
                  : left.classLabel().compareToIgnoreCase(right.classLabel());
            })
        .toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Page<CourseEnrollmentGradeHistoryResponse> history(
      final UUID institutionId,
      final UUID enrollmentId,
      final UUID gradeId,
      final Pageable pageable) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);
    final Page<CourseEnrollmentGradeHistory> page =
        historyRepository.findByGrade(
            institutionId, enrollment.getId(), gradeId, historySupport.withDefaultSort(pageable));

    // An out-of-range page arrives empty with totalElements > 0; it is only missing when the
    // current grade does not exist either.
    if (page.getTotalElements() == 0 && !gradeExists(institutionId, enrollment, gradeId)) {
      throw new EnrollmentValidationException(GradeMessages.GRADE_NOT_FOUND);
    }

    final Map<UUID, Person> people = historySupport.resolveActors(page.getContent());

    return page.map(event -> CourseEnrollmentGradeHistoryResponse.from(event, people));
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeArchivedResponse> archived(
      final UUID institutionId, final UUID enrollmentId) {
    final CourseEnrollment enrollment = store.findEnrollment(institutionId, enrollmentId);
    final List<CourseEnrollmentGradeHistory> latestDeletions =
        historyRepository.findLatestArchivedByEnrollment(institutionId, enrollment.getId());
    final List<CourseEnrollmentGradeArchivedResponse> archived = new ArrayList<>();

    for (final CourseEnrollmentGradeHistory latest : latestDeletions) {
      final String lastEvaluation =
          latest.getEvaluationBefore() != null
              ? latest.getEvaluationBefore()
              : Objects.requireNonNullElse(latest.getEvaluationAfter(), "");
      archived.add(
          new CourseEnrollmentGradeArchivedResponse(
              latest.getGradeId(),
              lastEvaluation,
              latest.getValueBefore() != null ? latest.getValueBefore() : latest.getValueAfter(),
              latest.getChangedAt(),
              latest.getOperation()));
    }

    return archived;
  }

  private boolean gradeExists(
      final UUID institutionId, final CourseEnrollment enrollment, final UUID gradeId) {
    return gradeRepository
        .findByIdAndInstitutionId(gradeId, institutionId)
        .map(grade -> grade.getCourseEnrollment().getId().equals(enrollment.getId()))
        .orElse(false);
  }
}
