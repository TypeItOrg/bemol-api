package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationSpace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class EnrollmentApplicationMapper {
  private EnrollmentApplicationMapper() {}

  static EnrollmentApplicationResponse summary(final EnrollmentApplication application) {
    final var path = application.getTrainingPath();
    final var data =
        EnrollmentDraftData.builder()
            .careerSelection(path == null ? null : new CareerSelectionDto(path.getId()))
            .build();

    return base(application)
        .data(data)
        .spaces(new ArrayList<>())
        .courses(new ArrayList<>())
        .build();
  }

  static EnrollmentApplicationResponse from(
      final EnrollmentApplication application,
      final boolean includeCourses,
      final boolean includeAttachments) {
    return base(application)
        .canReadAttachments(includeAttachments)
        .data(EnrollmentDraftDataMapper.from(application, includeAttachments))
        .spaces(includeCourses ? spaces(application) : new ArrayList<>())
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
    final var plan = application.getStudyPlan();
    final var year = application.commonAcademicYear();
    final var period = application.getEnrollmentPeriod();
    final var education = application.getEducationBackground();
    final var submitter = application.getSubmittedByPerson();

    return EnrollmentApplicationResponse.builder()
        .applicationId(application.getId())
        .institutionId(institution == null ? null : institution.getId())
        .submittedByPersonId(submitter == null ? null : submitter.getId())
        .personId(applicant == null ? null : applicant.getId())
        .applicantFirstName(applicant == null ? null : applicant.getFirstName())
        .applicantLastName(applicant == null ? null : applicant.getLastName())
        .applicantDocumentNumber(applicant == null ? null : applicant.getDocumentNumber())
        .trainingPathId(path == null ? null : path.getId())
        .studyPlanId(plan == null ? null : plan.getId())
        .studyPlanName(plan == null ? null : plan.getName())
        .studyPlanVersion(plan == null ? null : plan.getVersionNumber())
        .trainingPathName(path == null ? null : path.getName())
        .academicYearId(year == null ? null : year.getId())
        .academicYear(year == null ? null : year.getYear())
        .enrollmentPeriodId(period == null ? null : period.getId())
        .enrollmentPeriod(period == null ? null : EnrollmentPeriodResponse.from(period))
        .periodOpen(period != null && period.isOpenAt(Instant.now()))
        .status(application.getStatus())
        .pendingCourses(application.getPendingCourses())
        .isEditable(application.isEditable())
        .secondarySchool(education == null ? null : education.getSecondarySchool())
        .rejectionReason(application.getRejectionReason())
        .resolvedAt(application.getResolvedAt())
        .resolvedByPersonId(application.getResolvedByPersonId())
        .createdAt(application.getCreatedAt())
        .updatedAt(application.getUpdatedAt());
  }

  private static List<EnrollmentApplicationSpaceResponse> spaces(
      final EnrollmentApplication application) {
    if (application.getSelectedSpaces() == null) {
      return new ArrayList<>();
    }

    return application.getSelectedSpaces().stream()
        .map(EnrollmentApplicationMapper::space)
        .toList();
  }

  private static EnrollmentApplicationSpaceResponse space(
      final EnrollmentApplicationSpace selection) {
    final var space = selection.getStudyPlanSpace();
    final var level = space.getAcademicLevel();
    final var instrument = selection.getInstrument();

    return EnrollmentApplicationSpaceResponse.builder()
        .studyPlanSpaceId(space.getId())
        .spaceName(space.getAcademicSpace().getName())
        .academicLevelName(level == null ? null : level.getName())
        .instrumentId(instrument == null ? null : instrument.getId())
        .instrumentName(instrument == null ? null : instrument.getName())
        .build();
  }
}
