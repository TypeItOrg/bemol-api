package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "id",
      "institutionId",
      "trainingPathId",
      "academicYearId",
      "formId",
      "formVersionId",
      "versionNumber",
      "revision",
      "trainingPathName",
      "academicYear",
      "formName"
    })
public record EnrollmentFormAssignmentResponse(
    UUID id,
    UUID institutionId,
    UUID trainingPathId,
    UUID academicYearId,
    UUID formId,
    UUID formVersionId,
    int versionNumber,
    long revision,
    String trainingPathName,
    int academicYear,
    String formName) {
  public static EnrollmentFormAssignmentResponse from(final EnrollmentFormAssignment value) {
    return new EnrollmentFormAssignmentResponse(
        value.getId(),
        value.getInstitution().getId(),
        value.getTrainingPath().getId(),
        value.getAcademicYear().getId(),
        value.getFormVersion().getForm().getId(),
        value.getFormVersion().getId(),
        value.getFormVersion().getVersionNumber(),
        value.getRevision(),
        value.getTrainingPath().getName(),
        value.getAcademicYear().getYear(),
        value.getFormVersion().getForm().getName());
  }
}
