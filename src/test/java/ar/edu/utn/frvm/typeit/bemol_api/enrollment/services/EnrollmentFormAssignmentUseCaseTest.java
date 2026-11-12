package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentFormException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentFormVersionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentFormAssignmentUpdateRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentFormAssignmentUseCaseTest {
  private final UUID institutionId = UUID.randomUUID();
  private final UUID assignmentId = UUID.randomUUID();
  private final EnrollmentFormAssignmentRepository assignments =
      mock(EnrollmentFormAssignmentRepository.class);
  private final EnrollmentFormVersionRepository versions =
      mock(EnrollmentFormVersionRepository.class);
  private final TrainingPathRepository paths = mock(TrainingPathRepository.class);
  private final AcademicYearRepository years = mock(AcademicYearRepository.class);
  private final InstitutionRepository institutions = mock(InstitutionRepository.class);
  private final EnrollmentInstitutionLock lock = mock(EnrollmentInstitutionLock.class);
  private final EnrollmentFormAudit audit = mock(EnrollmentFormAudit.class);
  private final EnrollmentFormAssignmentUseCase service =
      new EnrollmentFormAssignmentUseCase(
          assignments, versions, paths, years, institutions, lock, audit);

  @Test
  void withdrawalUsesTheRevisionAndFlushesBeforeAuditingWithoutRevalidatingHistoricalContext() {
    final var assignment = assignment();
    when(assignments.findByIdAndInstitution_Id(assignmentId, institutionId))
        .thenReturn(Optional.of(assignment));

    service.delete(institutionId, assignmentId, 0);

    final var ordered = inOrder(lock, assignments, audit);
    ordered.verify(lock).lock(institutionId);
    ordered.verify(assignments).findByIdAndInstitution_Id(assignmentId, institutionId);
    ordered.verify(assignments).delete(assignment);
    ordered.verify(assignments).flush();
    ordered
        .verify(audit)
        .assignment(assignment, EnrollmentAuditAction.ENROLLMENT_FORM_UNASSIGNED, null);
    verifyNoInteractions(paths, years, versions);
  }

  @Test
  void aStaleWithdrawalDoesNotDeleteOrAudit() {
    final var assignment = assignment();
    when(assignments.findByIdAndInstitution_Id(assignmentId, institutionId))
        .thenReturn(Optional.of(assignment));

    assertThatThrownBy(() -> service.delete(institutionId, assignmentId, 1))
        .isInstanceOf(EnrollmentFormException.class);
    verify(assignments, never()).delete(any());
    verify(assignments, never()).flush();
    verifyNoInteractions(audit);
  }

  @Test
  void assigningTheSameVersionDoesNotWriteOrAudit() {
    final var assignment = assignment();
    final var path = assignment.getTrainingPath();
    final var year = assignment.getAcademicYear();
    final var version = assignment.getFormVersion();
    when(assignments.findByIdAndInstitution_Id(assignmentId, institutionId))
        .thenReturn(Optional.of(assignment));
    when(paths.findByIdAndInstitution_IdAndActiveTrueAndDeletedAtIsNull(
            path.getId(), institutionId))
        .thenReturn(Optional.of(path));
    when(years.findByIdAndInstitution_Id(year.getId(), institutionId))
        .thenReturn(Optional.of(year));
    when(versions.findByIdAndInstitution_Id(version.getId(), institutionId))
        .thenReturn(Optional.of(version));

    service.replace(
        institutionId,
        assignmentId,
        new EnrollmentFormAssignmentUpdateRequest(version.getId(), 0L));

    assertThat(assignment.getFormVersion()).isSameAs(version);
    verify(assignments, never()).saveAndFlush(any());
    verifyNoInteractions(audit);
  }

  private EnrollmentFormAssignment assignment() {
    final var institution = Institution.builder().id(institutionId).build();
    final var path = mock(TrainingPath.class);
    when(path.getId()).thenReturn(UUID.randomUUID());
    when(path.getInstitution()).thenReturn(institution);
    final var year = mock(AcademicYear.class);
    when(year.getId()).thenReturn(UUID.randomUUID());
    when(year.getInstitution()).thenReturn(institution);
    when(year.getStatus()).thenReturn(AcademicYearStatus.ACTIVE);
    final var form = mock(EnrollmentForm.class);
    when(form.getId()).thenReturn(UUID.randomUUID());
    final var version = mock(EnrollmentFormVersion.class);
    when(version.getId()).thenReturn(UUID.randomUUID());
    when(version.getForm()).thenReturn(form);
    return EnrollmentFormAssignment.create(institution, path, year, version);
  }
}
