package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.exceptions.ErrorCategory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentCreateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentResponse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentFormAssignmentUseCase {
  private final EnrollmentFormAssignmentRepository assignments;
  private final EnrollmentFormVersionRepository versions;
  private final TrainingPathRepository paths;
  private final AcademicYearRepository years;
  private final InstitutionRepository institutions;
  private final EnrollmentInstitutionLock lock;
  private final EnrollmentFormAudit audit;

  @Transactional(readOnly = true)
  public Page<EnrollmentFormAssignmentResponse> list(
      final UUID institutionId, final @Nullable UUID academicYearId, final Pageable pageable) {
    if (!institutions.existsById(institutionId)) {
      throw new InstitutionNotFoundException();
    }
    return assignments.findSummaries(
        institutionId,
        academicYearId,
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id")));
  }

  @Transactional
  public EnrollmentFormAssignmentResponse create(
      final UUID institutionId, final EnrollmentFormAssignmentCreateRequest request) {
    lock.lock(institutionId);
    final var path = path(institutionId, request.trainingPathId());
    final var year = year(institutionId, request.academicYearId());
    final var version = version(institutionId, request.formVersionId());
    if (assignments.existsByInstitution_IdAndTrainingPath_IdAndAcademicYear_Id(
        institutionId, path.getId(), year.getId())) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.ASSIGNMENT_CONFLICT);
    }

    final var assignment =
        EnrollmentFormAssignment.create(path.getInstitution(), path, year, version);
    assignments.saveAndFlush(assignment);
    audit.assignment(assignment, EnrollmentAuditAction.ENROLLMENT_FORM_ASSIGNED, null);
    return EnrollmentFormAssignmentResponse.from(assignment);
  }

  @Transactional
  public EnrollmentFormAssignmentResponse replace(
      final UUID institutionId,
      final UUID assignmentId,
      final EnrollmentFormAssignmentUpdateRequest request) {
    lock.lock(institutionId);
    final var assignment = assignment(institutionId, assignmentId);
    assignment.requireRevision(request.expectedRevision());
    path(institutionId, assignment.getTrainingPath().getId());
    year(institutionId, assignment.getAcademicYear().getId());
    final var version = version(institutionId, request.formVersionId());
    final var previousVersionId = assignment.getFormVersion().getId();
    assignment.assignVersion(version, request.expectedRevision());
    if (!previousVersionId.equals(version.getId())) {
      assignments.saveAndFlush(assignment);
      audit.assignment(
          assignment, EnrollmentAuditAction.ENROLLMENT_FORM_ASSIGNED, previousVersionId);
    }

    return EnrollmentFormAssignmentResponse.from(assignment);
  }

  @Transactional
  public void delete(
      final UUID institutionId, final UUID assignmentId, final long expectedRevision) {
    lock.lock(institutionId);
    final var assignment = assignment(institutionId, assignmentId);
    assignment.requireRevision(expectedRevision);
    assignments.delete(assignment);
    assignments.flush();
    audit.assignment(assignment, EnrollmentAuditAction.ENROLLMENT_FORM_UNASSIGNED, null);
  }

  private EnrollmentFormAssignment assignment(final UUID institutionId, final UUID id) {
    return assignments
        .findByIdAndInstitution_Id(id, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.ASSIGNMENT_NOT_FOUND));
  }

  private EnrollmentFormVersion version(final UUID institutionId, final UUID id) {
    return versions
        .findByIdAndInstitution_Id(id, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.VERSION_NOT_FOUND));
  }

  private TrainingPath path(final UUID institutionId, final UUID id) {
    return paths
        .findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(id, institutionId)
        .orElseThrow(
            () ->
                new EnrollmentFormException(
                    ErrorCategory.NOT_FOUND, EnrollmentFormMessages.ASSIGNMENT_CONTEXT_NOT_FOUND));
  }

  private AcademicYear year(final UUID institutionId, final UUID id) {
    final var year =
        years
            .findByIdAndInstitution_Id(id, institutionId)
            .orElseThrow(
                () ->
                    new EnrollmentFormException(
                        ErrorCategory.NOT_FOUND,
                        EnrollmentFormMessages.ASSIGNMENT_CONTEXT_NOT_FOUND));
    if (year.getStatus() == AcademicYearStatus.CLOSED) {
      throw new EnrollmentFormException(
          ErrorCategory.CONFLICT, EnrollmentFormMessages.ASSIGNMENT_CONTEXT_INVALID);
    }
    return year;
  }
}
