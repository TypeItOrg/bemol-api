package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleAction;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicLifecycleResource;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.StudyPlanStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.CourseNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.TrainingPathNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicLifecycleRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.ScopedResource;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AcademicAccessGuard;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseEnrollmentRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcademicCurriculumLifecycleUseCase {
  private final AcademicAccessGuard access;
  private final Clock clock;
  private final AcademicYearRepository years;
  private final TrainingPathRepository paths;
  private final StudyPlanRepository plans;
  private final CourseRepository courses;
  private final CourseEnrollmentRepository enrollments;
  private final AcademicLifecycleJournal journal;

  @Transactional
  public void deleteTrainingPath(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(
        PermissionCode.TRAINING_PATH_DELETE, institutionId, ScopedResource.TRAINING_PATH, id);

    final var path =
        paths
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(TrainingPathNotFoundException::new);
    if (!path.isDeleted() && paths.existsCurrentStudyPlan(id)) {
      throw new AcademicConflictException(AcademicMessages.DELETE_REFERENCED_RESOURCE);
    }
    if (path.delete(clock.instant())) {
      journal.record(
          path.getInstitution(),
          AcademicLifecycleResource.TRAINING_PATH,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreTrainingPath(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(
        PermissionCode.TRAINING_PATH_RESTORE, institutionId, ScopedResource.TRAINING_PATH, id);

    final var path =
        paths
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(TrainingPathNotFoundException::new);
    journal.restore(
        path, path.getInstitution(), AcademicLifecycleResource.TRAINING_PATH, id, request);
  }

  @Transactional
  public void deleteStudyPlan(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(PermissionCode.STUDY_PLAN_DELETE, institutionId, ScopedResource.STUDY_PLAN, id);

    final var plan =
        plans
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    if (plan.delete(clock.instant())) {
      journal.record(
          plan.getInstitution(),
          AcademicLifecycleResource.STUDY_PLAN,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreStudyPlan(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(PermissionCode.STUDY_PLAN_RESTORE, institutionId, ScopedResource.STUDY_PLAN, id);

    final var plan =
        plans
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    if (plan.isDeleted()
        && (plan.getTrainingPath().isDeleted() || !plan.getTrainingPath().isActive())) {
      throw new AcademicConflictException(AcademicMessages.RESTORE_PARENT_UNAVAILABLE);
    }
    journal.restore(plan, plan.getInstitution(), AcademicLifecycleResource.STUDY_PLAN, id, request);
  }

  @Transactional
  public void deleteCourse(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(PermissionCode.COURSE_DELETE, institutionId, ScopedResource.COURSE, id);

    lockCourseParents(institutionId, id);
    final var course =
        courses
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    if (enrollments.existsByCourseIncludingHistorical(institutionId, id)) {
      throw new AcademicConflictException(AcademicMessages.DELETE_REFERENCED_RESOURCE);
    }
    if (course.delete(clock.instant())) {
      journal.record(
          course.getInstitution(),
          AcademicLifecycleResource.COURSE,
          id,
          AcademicLifecycleAction.DELETE,
          request);
    }
  }

  @Transactional
  public void restoreCourse(
      final UUID institutionId, final UUID id, final @Nullable AcademicLifecycleRequest request) {
    access.require(PermissionCode.COURSE_RESTORE, institutionId, ScopedResource.COURSE, id);

    final var parents = lockCourseParents(institutionId, id);
    final var course =
        courses
            .findByIdAndInstitution_IdForLifecycle(id, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    if (course.isDeleted()
        && (parents.studyPlan().getStatus() != StudyPlanStatus.ACTIVE
            || parents.academicYear().getStatus() != AcademicYearStatus.ACTIVE)) {
      throw new AcademicConflictException(AcademicMessages.RESTORE_PARENT_UNAVAILABLE);
    }
    journal.restore(course, course.getInstitution(), AcademicLifecycleResource.COURSE, id, request);
  }

  private CourseParents lockCourseParents(final UUID institutionId, final UUID courseId) {
    final var context =
        courses
            .findAcademicContextByIdAndInstitution_Id(courseId, institutionId)
            .orElseThrow(CourseNotFoundException::new);
    final var plan =
        plans
            .findByIdAndInstitution_IdForLifecycle(context.getStudyPlanId(), institutionId)
            .orElseThrow(StudyPlanNotFoundException::new);
    final var year =
        years
            .findByIdAndInstitution_IdForUpdate(context.getAcademicYearId(), institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);

    return new CourseParents(plan, year);
  }

  private record CourseParents(StudyPlan studyPlan, AcademicYear academicYear) {}
}
