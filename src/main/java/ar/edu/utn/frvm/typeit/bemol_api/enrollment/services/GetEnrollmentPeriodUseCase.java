package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentPeriodNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentPeriodRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPeriodResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetEnrollmentPeriodUseCase {
  private final EnrollmentPeriodAccessService periodAccess;

  private final EnrollmentPeriodRepository periodRepository;

  @Transactional(readOnly = true)
  public EnrollmentPeriodResponse execute(final UUID institutionId, final UUID periodId) {
    final var period =
        periodRepository
            .findByIdAndInstitutionIdAndDeletedAtIsNull(periodId, institutionId)
            .orElseThrow(EnrollmentPeriodNotFoundException::new);

    return periodAccess.response(period);
  }
}
