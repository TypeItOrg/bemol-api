package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicConflictException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicIntegrityViolationTranslator;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicYearNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.AcademicYearRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseClosureService;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.EnrollmentInstitutionLock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcademicYearStatusUseCase {

  private final AcademicYearRepository academicYearRepository;
  private final CourseRepository courseRepository;
  private final CourseClosureService courseClosureService;

  private final EnrollmentInstitutionLock enrollmentInstitutionLock;

  @Transactional
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE)
  public void execute(
      final UUID institutionId, final UUID id, final AcademicYearStatusRequest request) {
    enrollmentInstitutionLock.lock(institutionId);
    final var academicYear =
        academicYearRepository
            .findByIdAndInstitution_IdForUpdate(id, institutionId)
            .orElseThrow(AcademicYearNotFoundException::new);
    try {
      change(institutionId, academicYear, request.status());
      academicYearRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw AcademicIntegrityViolationTranslator.translate(exception);
    }
  }

  // Both entry points hold the institution and year locks before requesting a transition.
  @Transactional(propagation = Propagation.MANDATORY)
  @RequiresPermission(PermissionCode.ACADEMIC_YEAR_STATUS_UPDATE)
  public void change(
      final UUID institutionId,
      final AcademicYear academicYear,
      final AcademicYearStatus targetStatus) {
    if (targetStatus == AcademicYearStatus.ACTIVE
        && (academicYear.getStartDate() == null || academicYear.getEndDate() == null)) {
      throw new AcademicValidationException(AcademicMessages.ACADEMIC_YEAR_DATES_REQUIRED);
    }
    if (targetStatus == AcademicYearStatus.ACTIVE
        && academicYear.getStatus() != AcademicYearStatus.ACTIVE
        && academicYearRepository.existsByInstitution_IdAndStatusAndDeletedAtIsNull(
            institutionId, AcademicYearStatus.ACTIVE)) {
      throw AcademicConflictException.forField(
          "status", AcademicMessages.ACADEMIC_YEAR_ACTIVE_CONFLICT);
    }

    academicYear.transitionTo(targetStatus);
    if (targetStatus == AcademicYearStatus.CLOSED) {
      final var courses =
          courseRepository.findByAcademicYear_IdAndInstitution_IdAndDeletedAtIsNull(
              academicYear.getId(), institutionId);
      for (final var course : courses) {
        course.close();
        courseClosureService.close(institutionId, course.getId(), null);
      }
    }
  }
}
