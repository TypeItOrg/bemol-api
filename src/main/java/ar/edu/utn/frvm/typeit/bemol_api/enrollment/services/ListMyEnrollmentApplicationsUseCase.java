package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentApplicationResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListMyEnrollmentApplicationsUseCase {

  private final EnrollmentApplicationRepository enrollmentApplicationRepository;
  private final EnrollmentApplicationResponseFactory responses;

  @Transactional(readOnly = true)
  public Page<EnrollmentApplicationResponse> execute(
      final UUID institutionId,
      final UUID personId,
      final @Nullable UUID dependentPersonId,
      final @Nullable EnrollmentApplicationStatus status,
      final @Nullable Boolean pendingCourses,
      final Pageable pageable) {
    return enrollmentApplicationRepository
        .findMyApplications(
            institutionId, personId, dependentPersonId, status, pendingCourses, pageable)
        .map(application -> responses.summary(application));
  }
}
