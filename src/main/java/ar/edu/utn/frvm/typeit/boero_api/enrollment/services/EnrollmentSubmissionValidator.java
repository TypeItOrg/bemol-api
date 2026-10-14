package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantPreference;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EducationLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentSubmissionValidator {
  private final BusinessDateProvider dates;

  public void validate(final EnrollmentApplication application) {
    final Map<String, String> errors = new HashMap<>();
    validateApplicant(application.getApplicantPerson(), application.getResponsible(), errors);
    validateEducation(application.getEducationBackground(), errors);
    if (application.getCourseSelections() == null || application.getCourseSelections().isEmpty()) {
      errors.put("courses", EnrollmentMessages.ENROLLMENT_APPLICATION_SPACES_REQUIRED);
    }
    validatePreference(application.getPreference(), errors);

    if (!errors.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentMessages.SUBMISSION_INCOMPLETE, errors);
    }
  }

  private void validateApplicant(
      final @Nullable Person applicant,
      final @Nullable ApplicantResponsible responsible,
      final Map<String, String> errors) {
    if (applicant == null) {
      errors.put("applicant", EnrollmentMessages.APPLICANT_REQUIRED);
      return;
    }

    requireText(
        applicant.getFirstName(),
        "personalData.firstName",
        EnrollmentMessages.NAME_REQUIRED,
        errors);
    requireText(
        applicant.getLastName(),
        "personalData.lastName",
        EnrollmentMessages.LAST_NAME_REQUIRED,
        errors);
    requireText(
        applicant.getDocumentNumber(),
        "personalData.documentNumber",
        EnrollmentMessages.DOCUMENT_REQUIRED,
        errors);
    requireText(
        applicant.getEmail(), "personalData.email", EnrollmentMessages.EMAIL_REQUIRED, errors);

    if (applicant.getBirthDate() != null
        && Period.between(applicant.getBirthDate(), dates.today()).getYears() < 18) {
      validateResponsible(responsible, errors);
    }
  }

  private void validateResponsible(
      final @Nullable ApplicantResponsible responsible, final Map<String, String> errors) {
    if (responsible == null) {
      errors.put("responsible", EnrollmentMessages.RESPONSIBLE_REQUIRED);
      return;
    }

    requireText(
        responsible.getFullName(),
        "responsible.fullName",
        EnrollmentMessages.RESPONSIBLE_NAME_REQUIRED,
        errors);
    requireText(
        responsible.getDocumentNumber(),
        "responsible.documentNumber",
        EnrollmentMessages.RESPONSIBLE_DOCUMENT_REQUIRED,
        errors);
    requireText(
        responsible.getPhoneNumber(),
        "responsible.phoneNumber",
        EnrollmentMessages.RESPONSIBLE_PHONE_REQUIRED,
        errors);
  }

  private void validateEducation(
      final @Nullable ApplicantEducationBackground education, final Map<String, String> errors) {
    if (education == null || education.getCurrentlyStudying() == null) {
      errors.put(
          "academicBackground.currentlyStudying", EnrollmentMessages.CURRENTLY_STUDYING_REQUIRED);
      return;
    }
    if (education.getEducationLevel() == null) {
      errors.put("academicBackground.educationLevel", EnrollmentMessages.EDUCATION_LEVEL_REQUIRED);
      return;
    }

    final boolean studying = Boolean.TRUE.equals(education.getCurrentlyStudying());
    final var level = education.getEducationLevel();
    if (studying && level == EducationLevel.NO_SCHOOLING) {
      errors.put(
          "academicBackground.educationLevel", EnrollmentMessages.CURRENT_EDUCATION_LEVEL_INVALID);
    }
    if (studying) {
      requireText(
          education.getSchoolOrigin(),
          "academicBackground.schoolOrigin",
          EnrollmentMessages.EDUCATION_INSTITUTION_REQUIRED,
          errors);
    }
    if (!studying
        && level != EducationLevel.NO_SCHOOLING
        && level != EducationLevel.SECONDARY
        && education.getLevelCompleted() == null) {
      errors.put(
          "academicBackground.levelCompleted", EnrollmentMessages.EDUCATION_COMPLETION_REQUIRED);
    }
    if (level.requiresSecondaryCompletionAnswer() && education.getSecondaryCompleted() == null) {
      errors.put(
          "academicBackground.secondaryCompleted",
          EnrollmentMessages.SECONDARY_COMPLETION_REQUIRED);
    }
  }

  private void validatePreference(
      final @Nullable ApplicantPreference preference, final Map<String, String> errors) {
    if (preference == null
        || preference.getPreferredShift() == null
        || preference.getPreferredShift().isBlank()) {
      errors.put("preference.preferredShift", EnrollmentMessages.SHIFT_REQUIRED);
    } else if (preference.isReenrolling()) {
      requireText(
          preference.getPreviousTeacher(),
          "preference.previousTeacher",
          EnrollmentMessages.PREVIOUS_TEACHER_REQUIRED,
          errors);
    }
  }

  private void requireText(
      final @Nullable String value,
      final String field,
      final String message,
      final Map<String, String> errors) {
    if (value == null || value.isBlank()) {
      errors.put(field, message);
    }
  }
}
