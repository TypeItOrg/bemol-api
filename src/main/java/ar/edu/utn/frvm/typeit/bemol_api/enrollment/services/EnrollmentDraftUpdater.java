package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantHealthInclusion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantPreference;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentNativeFormMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.AcademicBackgroundDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentDraftData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.HealthInclusionDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.PreferenceDto;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.ResponsibleDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnrollmentDraftUpdater {
  private final EnrollmentLegacySpaceSelectionService spaces;
  private final EnrollmentCourseSelectionService courses;

  @Transactional(propagation = Propagation.MANDATORY)
  public void update(final EnrollmentApplication application, final EnrollmentDraftData data) {
    requireEnabledBlocks(application, data);

    // Personal data belongs to the institutional Person register, not draft autosaves.
    updateEducation(application, data.getAcademicBackground());
    updateHealth(application, data.getHealthInclusion());
    updateResponsible(application, data.getResponsible());
    updatePreference(application, data.getPreference());
    spaces.update(application, data);

    // A legacy plan does not prevent selecting concrete courses.
    if (data.getCourses() != null && courses.changed(application, data.getCourses())) {
      courses.update(application, data.getCourses());
    }
  }

  private void requireEnabledBlocks(
      final EnrollmentApplication application, final EnrollmentDraftData data) {
    final var form = application.getFormState();
    final Map<String, String> errors = new HashMap<>();
    if (data.getAcademicBackground() != null
        && !form.hasBlock(EnrollmentFormNativeBlock.SCHOOLING)) {
      errors.put("academicBackground", EnrollmentNativeFormMessages.BLOCK_DISABLED);
    }
    if (data.getHealthInclusion() != null
        && !form.hasBlock(EnrollmentFormNativeBlock.HEALTH_INCLUSION)) {
      errors.put("healthInclusion", EnrollmentNativeFormMessages.BLOCK_DISABLED);
    }
    final var preference = data.getPreference();
    if (preference != null
        && !form.hasBlock(EnrollmentFormNativeBlock.PREFERENCES)
        && (!form.hasBlock(EnrollmentFormNativeBlock.PREFERRED_SHIFT)
            || preference.getAllowsImageUse() != null
            || preference.getIsReenrolling() != null
            || preference.getPreviousTeacher() != null)) {
      errors.put("preference", EnrollmentNativeFormMessages.BLOCK_DISABLED);
    }
    if (!errors.isEmpty()) {
      throw new EnrollmentValidationException(EnrollmentNativeFormMessages.BLOCK_DISABLED, errors);
    }
  }

  private void updateEducation(
      final EnrollmentApplication application, final @Nullable AcademicBackgroundDto data) {
    if (data == null) {
      return;
    }

    var background = application.getEducationBackground();
    if (background == null) {
      background =
          ApplicantEducationBackground.builder().enrollmentApplication(application).build();
      application.setEducationBackground(background);
    }
    if (data.getSecondarySchool() != null) {
      background.setSecondarySchool(data.getSecondarySchool());
    }
    background.updateSchooling(
        data.getCurrentlyStudying(),
        data.getEducationLevel(),
        data.getSchoolOrigin(),
        data.getCurrentGradeYear(),
        data.getLevelCompleted(),
        data.getSecondaryCompleted(),
        data.getSecondaryDegreeTitle());
  }

  private void updateHealth(
      final EnrollmentApplication application, final @Nullable HealthInclusionDto data) {
    if (data == null) {
      return;
    }

    var health = application.getHealthInclusion();
    if (health == null) {
      health = ApplicantHealthInclusion.builder().enrollmentApplication(application).build();
      application.setHealthInclusion(health);
    }
    if (data.getReceivesReasonableAdjustments() != null) {
      health.setReceivesReasonableAdjustments(data.getReceivesReasonableAdjustments());
    }
    if (data.getAdjustmentDetails() != null) {
      health.setAdjustmentDetails(data.getAdjustmentDetails());
    }
  }

  private void updateResponsible(
      final EnrollmentApplication application, final @Nullable ResponsibleDto data) {
    if (data == null) {
      return;
    }

    var responsible = application.getResponsible();
    if (responsible == null) {
      responsible = ApplicantResponsible.builder().enrollmentApplication(application).build();
      application.setResponsible(responsible);
    }
    if (data.getFullName() != null) {
      responsible.setFullName(data.getFullName());
    }
    if (data.getDocumentNumber() != null) {
      responsible.setDocumentNumber(data.getDocumentNumber());
    }
    if (data.getOccupation() != null) {
      responsible.setOccupation(data.getOccupation());
    }
    if (data.getPhoneNumber() != null) {
      responsible.setPhoneNumber(data.getPhoneNumber());
    }
    if (data.getEmail() != null) {
      responsible.setEmail(data.getEmail());
    }
    if (data.getEducationLevel() != null) {
      responsible.setEducationLevel(data.getEducationLevel());
    }
  }

  private void updatePreference(
      final EnrollmentApplication application, final @Nullable PreferenceDto data) {
    if (data == null) {
      return;
    }

    var preference = application.getPreference();
    if (preference == null) {
      preference = ApplicantPreference.builder().enrollmentApplication(application).build();
      application.setPreference(preference);
    }
    if (data.getPreferredShift() != null) {
      preference.setPreferredShift(data.getPreferredShift());
    }
    if (data.getAllowsImageUse() != null) {
      preference.setAllowsImageUse(data.getAllowsImageUse());
    }
    if (data.getIsReenrolling() != null) {
      preference.setIsReenrolling(data.getIsReenrolling());
    }
    if (data.getPreviousTeacher() != null) {
      preference.setPreviousTeacher(data.getPreviousTeacher());
    }
  }
}
