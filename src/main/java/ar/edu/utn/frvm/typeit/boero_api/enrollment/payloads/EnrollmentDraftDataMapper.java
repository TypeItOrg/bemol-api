package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantHealthInclusion;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantPreference;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class EnrollmentDraftDataMapper {
  private EnrollmentDraftDataMapper() {}

  static EnrollmentDraftData from(
      final EnrollmentApplication application, final boolean includeAttachments) {
    final var path = application.getTrainingPath();

    return EnrollmentDraftData.builder()
        .personalData(personal(application.getApplicantPerson()))
        .academicBackground(education(application.getEducationBackground()))
        .healthInclusion(health(application.getHealthInclusion()))
        .responsible(responsible(application.getResponsible()))
        .preference(preference(application.getPreference()))
        .careerSelection(
            path == null ? new CareerSelectionDto() : new CareerSelectionDto(path.getId()))
        .academicSpaceSelection(spaces(application))
        .instrumentSelection(instruments(application))
        .courses(
            application.getCourseSelections() == null
                ? new ArrayList<>()
                : application.getCourseSelections().stream()
                    .map(
                        selection ->
                            new CourseSelectionDto(
                                selection.getCourse().getId(),
                                selection.getPreferredTeacher() == null
                                    ? null
                                    : selection.getPreferredTeacher().getId()))
                    .toList())
        .attachments(attachments(application, includeAttachments))
        .build();
  }

  private static PersonalDataDto personal(final @Nullable Person person) {
    if (person == null) {
      return new PersonalDataDto();
    }

    return PersonalDataDto.builder()
        .firstName(person.getFirstName())
        .lastName(person.getLastName())
        .documentNumber(person.getDocumentNumber())
        .birthDate(person.getBirthDate())
        .phoneNumber(person.getPhoneNumber())
        .email(person.getEmail())
        .build();
  }

  private static AcademicBackgroundDto education(
      final @Nullable ApplicantEducationBackground background) {
    if (background == null) {
      return new AcademicBackgroundDto();
    }

    return AcademicBackgroundDto.builder()
        .secondarySchool(background.getSecondarySchool())
        .currentlyStudying(background.getCurrentlyStudying())
        .educationLevel(background.getEducationLevel())
        .schoolOrigin(background.getSchoolOrigin())
        .currentGradeYear(background.getCurrentGradeYear())
        .levelCompleted(background.getLevelCompleted())
        .secondaryCompleted(background.getSecondaryCompleted())
        .secondaryDegreeTitle(background.getSecondaryDegreeTitle())
        .build();
  }

  private static HealthInclusionDto health(final @Nullable ApplicantHealthInclusion health) {
    if (health == null) {
      return new HealthInclusionDto();
    }

    return HealthInclusionDto.builder()
        .receivesReasonableAdjustments(health.isReceivesReasonableAdjustments())
        .adjustmentDetails(health.getAdjustmentDetails())
        .build();
  }

  private static ResponsibleDto responsible(final @Nullable ApplicantResponsible responsible) {
    if (responsible == null) {
      return new ResponsibleDto();
    }

    return ResponsibleDto.builder()
        .fullName(responsible.getFullName())
        .documentNumber(responsible.getDocumentNumber())
        .occupation(responsible.getOccupation())
        .phoneNumber(responsible.getPhoneNumber())
        .email(responsible.getEmail())
        .educationLevel(responsible.getEducationLevel())
        .build();
  }

  private static PreferenceDto preference(final @Nullable ApplicantPreference preference) {
    if (preference == null) {
      return new PreferenceDto();
    }

    return PreferenceDto.builder()
        .preferredShift(preference.getPreferredShift())
        .allowsImageUse(preference.isAllowsImageUse())
        .isReenrolling(preference.isReenrolling())
        .previousTeacher(preference.getPreviousTeacher())
        .build();
  }

  private static AcademicSpaceSelectionDto spaces(final EnrollmentApplication application) {
    if (application.getSelectedSpaces() == null || application.getSelectedSpaces().isEmpty()) {
      return new AcademicSpaceSelectionDto();
    }

    return new AcademicSpaceSelectionDto(
        application.getSelectedSpaces().stream()
            .map(selection -> selection.getStudyPlanSpace().getId())
            .toList());
  }

  private static InstrumentSelectionDto instruments(final EnrollmentApplication application) {
    if (application.getSelectedSpaces() == null || application.getSelectedSpaces().isEmpty()) {
      return new InstrumentSelectionDto();
    }

    return new InstrumentSelectionDto(
        application.getSelectedSpaces().stream()
            .filter(selection -> selection.getInstrument() != null)
            .collect(
                Collectors.toMap(
                    selection -> selection.getStudyPlanSpace().getId(),
                    selection -> selection.getInstrument().getId())));
  }

  private static List<AttachmentDto> attachments(
      final EnrollmentApplication application, final boolean includeAttachments) {
    if (!includeAttachments || application.getAttachments() == null) {
      return new ArrayList<>();
    }

    return application.getAttachments().stream()
        .filter(attachment -> attachment.getDeletedAt() == null && attachment.isCurrent())
        .map(
            attachment ->
                AttachmentDto.builder()
                    .id(attachment.getId())
                    .requirementId(attachment.getRequirement().getId())
                    .originalFileName(attachment.getOriginalFileName())
                    .storagePath(null)
                    .contentType(attachment.getContentType())
                    .fileSize(attachment.getFileSize())
                    .createdAt(attachment.getCreatedAt())
                    .build())
        .toList();
  }
}
