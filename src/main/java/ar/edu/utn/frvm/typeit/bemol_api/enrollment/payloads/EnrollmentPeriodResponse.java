package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentPeriodStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "academicYearId",
      "academicYearNumber",
      "name",
      "startDate",
      "endDate",
      "status",
      "deletedAt",
      "scopeConfigured",
      "offerings",
      "limitedView",
      "canUpdate",
      "canChangeStatus",
      "canDelete"
    })
public record EnrollmentPeriodResponse(
    UUID id,
    UUID institutionId,
    UUID academicYearId,
    int academicYearNumber,
    String name,
    Instant startDate,
    Instant endDate,
    EnrollmentPeriodStatus status,
    @Schema(nullable = true) @Nullable Instant deletedAt,
    boolean scopeConfigured,
    List<EnrollmentPeriodOfferingResponse> offerings,
    boolean limitedView,
    boolean canUpdate,
    boolean canChangeStatus,
    boolean canDelete) {

  public static EnrollmentPeriodResponse from(final EnrollmentPeriod period) {
    return new EnrollmentPeriodResponse(
        period.getId(),
        period.getInstitution().getId(),
        period.getAcademicYear().getId(),
        period.getAcademicYear().getYear(),
        period.getName(),
        period.getStartDate(),
        period.getEndDate(),
        period.getStatus(),
        period.getDeletedAt(),
        period.isScopeConfigured(),
        period.getOfferings().stream().map(EnrollmentPeriodOfferingResponse::from).toList(),
        false,
        false,
        false,
        false);
  }
}
