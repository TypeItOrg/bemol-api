package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
@Schema(
    requiredProperties = {
      "applicationId",
      "institutionId",
      "personId",
      "submittedByPersonId",
      "applicantFirstName",
      "applicantLastName",
      "applicantDocumentNumber",
      "trainingPathId",
      "studyPlanId",
      "studyPlanName",
      "studyPlanVersion",
      "trainingPathName",
      "academicYearId",
      "academicYear",
      "enrollmentPeriodId",
      "status",
      "isEditable",
      "data",
      "secondarySchool",
      "rejectionReason",
      "resolvedAt",
      "resolvedByPersonId",
      "createdAt",
      "updatedAt",
      "spaces",
      "courses",
      "enrollmentPeriod",
      "periodOpen",
      "canReadAttachments",
      "documents",
      "canApproveProvisionally",
      "canConfirm",
      "documentRequests",
      "canRequestDocuments",
      "admissionHistory",
      "pendingCourses"
    })
public record EnrollmentApplicationResponse(
    @Schema(nullable = true) @Nullable UUID applicationId,
    @Schema(nullable = true) @Nullable UUID institutionId,
    @Schema(nullable = true) @Nullable UUID personId,
    @Schema(nullable = true) @Nullable UUID submittedByPersonId,
    @Schema(nullable = true) @Nullable String applicantFirstName,
    @Schema(nullable = true) @Nullable String applicantLastName,
    @Schema(nullable = true) @Nullable String applicantDocumentNumber,
    @Schema(nullable = true) @Nullable UUID trainingPathId,
    @Schema(nullable = true) @Nullable UUID studyPlanId,
    @Schema(nullable = true) @Nullable String studyPlanName,
    @Schema(nullable = true) @Nullable Integer studyPlanVersion,
    @Schema(nullable = true) @Nullable String trainingPathName,
    @Schema(nullable = true) @Nullable UUID academicYearId,
    @Schema(nullable = true) @Nullable Integer academicYear,
    @Schema(nullable = true) @Nullable UUID enrollmentPeriodId,
    @Schema(nullable = true) @Nullable EnrollmentApplicationStatus status,
    @JsonProperty("isEditable") boolean isEditable,
    @Schema(nullable = true) @Nullable EnrollmentDraftData data,
    @Schema(nullable = true) @Nullable String secondarySchool,
    @Schema(nullable = true) @Nullable String rejectionReason,
    @Schema(nullable = true) @Nullable Instant resolvedAt,
    @Schema(nullable = true) @Nullable UUID resolvedByPersonId,
    @Schema(nullable = true) @Nullable Instant createdAt,
    @Schema(nullable = true) @Nullable Instant updatedAt,
    @Schema(nullable = true) @Nullable List<EnrollmentApplicationSpaceResponse> spaces,
    @Schema(nullable = true) @Nullable List<EnrollmentApplicationCourseResponse> courses,
    @Schema(nullable = true) @Nullable EnrollmentPeriodResponse enrollmentPeriod,
    boolean periodOpen,
    boolean canReadAttachments,
    List<EnrollmentDocumentRequirementResponse> documents,
    boolean canApproveProvisionally,
    boolean canConfirm,
    List<EnrollmentAdmissionHistoryResponse> admissionHistory,
    boolean canRequestDocuments,
    List<EnrollmentDocumentRequestResponse> documentRequests,
    @Schema(
            nullable = true,
            description =
                "Null unless admitted with course selections; true includes the waitlist.")
        @Nullable Boolean pendingCourses) {
  public EnrollmentApplicationResponse {
    documents = documents == null ? List.of() : documents;
    documentRequests = documentRequests == null ? List.of() : documentRequests;
    admissionHistory = admissionHistory == null ? List.of() : admissionHistory;
    spaces = spaces == null ? new ArrayList<>() : spaces;
    courses = courses == null ? new ArrayList<>() : courses;
  }

  public @Nullable UUID getApplicationId() {
    return applicationId;
  }

  public @Nullable UUID getInstitutionId() {
    return institutionId;
  }

  public @Nullable UUID getPersonId() {
    return personId;
  }

  public @Nullable String getApplicantFirstName() {
    return applicantFirstName;
  }

  public @Nullable String getApplicantLastName() {
    return applicantLastName;
  }

  public @Nullable String getApplicantDocumentNumber() {
    return applicantDocumentNumber;
  }

  public @Nullable UUID getTrainingPathId() {
    return trainingPathId;
  }

  public @Nullable UUID getStudyPlanId() {
    return studyPlanId;
  }

  public @Nullable String getStudyPlanName() {
    return studyPlanName;
  }

  public @Nullable String getTrainingPathName() {
    return trainingPathName;
  }

  public @Nullable UUID getAcademicYearId() {
    return academicYearId;
  }

  public @Nullable Integer getAcademicYear() {
    return academicYear;
  }

  public @Nullable UUID getEnrollmentPeriodId() {
    return enrollmentPeriodId;
  }

  public @Nullable EnrollmentApplicationStatus getStatus() {
    return status;
  }

  public @Nullable EnrollmentDraftData getData() {
    return data;
  }

  public @Nullable String getSecondarySchool() {
    return secondarySchool;
  }

  public @Nullable String getRejectionReason() {
    return rejectionReason;
  }

  public @Nullable Instant getResolvedAt() {
    return resolvedAt;
  }

  public @Nullable UUID getResolvedByPersonId() {
    return resolvedByPersonId;
  }

  public @Nullable Instant getCreatedAt() {
    return createdAt;
  }

  public @Nullable Instant getUpdatedAt() {
    return updatedAt;
  }

  public @Nullable List<EnrollmentApplicationSpaceResponse> getSpaces() {
    return spaces;
  }

  public @Nullable List<EnrollmentApplicationCourseResponse> getCourses() {
    return courses;
  }

  public static EnrollmentApplicationResponse from(final EnrollmentApplication application) {
    return from(application, true);
  }

  public static EnrollmentApplicationResponse summary(final EnrollmentApplication application) {
    return EnrollmentApplicationMapper.summary(application);
  }

  public static EnrollmentApplicationResponse from(
      final EnrollmentApplication application, final boolean includeCourseDetails) {
    return from(application, includeCourseDetails, false);
  }

  public static EnrollmentApplicationResponse from(
      final EnrollmentApplication application,
      final boolean includeCourseDetails,
      final boolean includeAttachments) {
    return EnrollmentApplicationMapper.from(application, includeCourseDetails, includeAttachments);
  }
}
