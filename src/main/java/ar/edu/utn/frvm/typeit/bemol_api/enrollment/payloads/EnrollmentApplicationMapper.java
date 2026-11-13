package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import java.time.Instant;
import java.util.ArrayList;

final class EnrollmentApplicationMapper {
  private EnrollmentApplicationMapper() {}

  static EnrollmentApplicationResponse summary(final EnrollmentApplication application) {
    final var path = application.getTrainingPath();
    final var data =
        EnrollmentDraftData.builder()
            .careerSelection(path == null ? null : new CareerSelectionDto(path.getId()))
            .build();

    return base(application).data(data).courses(new ArrayList<>()).build();
  }

  static EnrollmentApplicationResponse from(
      final EnrollmentApplication application,
      final boolean includeCourses,
      final boolean includeAttachments,
      final boolean includeFormDetails) {
    final var form = application.getFormState();
    final var education = application.getEducationBackground();
    return base(application)
        .canReadAttachments(includeAttachments)
        .formDefinition(includeFormDetails ? form.definition() : null)
        .secondarySchool(
            education == null || !form.hasBlock(EnrollmentFormNativeBlock.SCHOOLING)
                ? null
                : education.getSecondarySchool())
        .data(
            EnrollmentDraftDataMapper.from(
                application, includeCourses, includeAttachments, includeFormDetails))
        .courses(
            !includeCourses || application.getCourseSelections() == null
                ? new ArrayList<>()
                : application.getCourseSelections().stream()
                    .map(EnrollmentApplicationCourseResponse::from)
                    .toList())
        .build();
  }

  private static EnrollmentApplicationResponse.EnrollmentApplicationResponseBuilder base(
      final EnrollmentApplication application) {
    final var institution = application.getInstitution();
    final var applicant = application.getApplicantPerson();
    final var path = application.getTrainingPath();
    final var year = application.commonAcademicYear();
    final var period = application.getEnrollmentPeriod();
    final var education = application.getEducationBackground();
    final var submitter = application.getSubmittedByPerson();
    final var form = application.getFormState();

    return EnrollmentApplicationResponse.builder()
        .applicationId(application.getId())
        .institutionId(institution == null ? null : institution.getId())
        .submittedByPersonId(submitter == null ? null : submitter.getId())
        .personId(applicant == null ? null : applicant.getId())
        .applicantFirstName(applicant == null ? null : applicant.getFirstName())
        .applicantLastName(applicant == null ? null : applicant.getLastName())
        .applicantDocumentNumber(applicant == null ? null : applicant.getDocumentNumber())
        .trainingPathId(path == null ? null : path.getId())
        .trainingPathName(path == null ? null : path.getName())
        .academicYearId(year == null ? null : year.getId())
        .academicYear(year == null ? null : year.getYear())
        .enrollmentPeriodId(period == null ? null : period.getId())
        .enrollmentPeriod(period == null ? null : EnrollmentPeriodResponse.from(period))
        .periodOpen(period != null && period.isOpenAt(Instant.now()))
        .status(application.getStatus())
        .pendingCourses(application.getPendingCourses())
        .isEditable(application.isEditable())
        .secondarySchool(
            education == null || form.getFormVersion() != null
                ? null
                : education.getSecondarySchool())
        .formVersionId(form.getFormVersion() == null ? null : form.getFormVersion().getId())
        .draftRevision(form.getDraftRevision())
        .rejectionReason(application.getRejectionReason())
        .resolvedAt(application.getResolvedAt())
        .resolvedByPersonId(application.getResolvedByPersonId())
        .createdAt(application.getCreatedAt())
        .updatedAt(application.getUpdatedAt());
  }
}
