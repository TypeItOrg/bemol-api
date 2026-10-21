package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollmentHistory;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.AcademicEnrollmentStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.CourseEnrollmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CourseEnrollmentHistoryResponse(
    UUID id,
    @Nullable CourseEnrollmentStatus previousStatus,
    CourseEnrollmentStatus newStatus,
    @Nullable AcademicEnrollmentStatus previousAcademicStatus,
    AcademicEnrollmentStatus newAcademicStatus,
    String operation,
    @Nullable String reason,
    @Nullable UUID authorityPersonId,
    Instant changedAt) {

  public static CourseEnrollmentHistoryResponse from(final CourseEnrollmentHistory history) {
    return new CourseEnrollmentHistoryResponse(
        history.getId(),
        history.getPreviousStatus(),
        history.getNewStatus(),
        history.getPreviousAcademicStatus(),
        history.getNewAcademicStatus(),
        history.getOperation(),
        history.getReason(),
        history.getAuthorityPersonId(),
        history.getChangedAt());
  }
}
