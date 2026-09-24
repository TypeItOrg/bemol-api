package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.ShiftRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ShiftResponse;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEnrollmentApplicationShiftsUseCase {

  private final ApplicantEnrollmentGuard applicantEnrollmentGuard;
  private final EnrollmentApplicationRepository enrollmentApplicationRepository;
  private final ShiftRepository shiftRepository;

  @Transactional(readOnly = true)
  public List<ShiftResponse> execute(
      final JwtAuthenticatedUser principal, final UUID applicationId) {
    applicantEnrollmentGuard.requireApplicant(principal);
    enrollmentApplicationRepository
        .findAccessibleByIdAndInstitutionId(
            principal.institutionId(), principal.personId(), applicationId)
        .orElseThrow(EnrollmentApplicationNotFoundException::new);

    return shiftRepository
        .findByInstitution_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(
            principal.institutionId())
        .stream()
        .map(ShiftResponse::from)
        .toList();
  }
}
