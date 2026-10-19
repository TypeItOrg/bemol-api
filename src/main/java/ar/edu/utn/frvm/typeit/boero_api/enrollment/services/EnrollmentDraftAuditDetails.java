package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory comparison only: private answers must never be copied into audit snapshots. */
final class EnrollmentDraftAuditDetails {
  private EnrollmentDraftAuditDetails() {}

  static Map<String, Object> snapshot(final EnrollmentApplication application) {
    final Map<String, Object> values = new LinkedHashMap<>();
    final var education = application.getEducationBackground();
    values.put(
        "academicBackground",
        education == null
            ? null
            : Arrays.asList(
                education.getSecondarySchool(),
                education.getCurrentlyStudying(),
                education.getEducationLevel(),
                education.getSchoolOrigin(),
                education.getCurrentGradeYear(),
                education.getLevelCompleted(),
                education.getSecondaryCompleted(),
                education.getSecondaryDegreeTitle()));
    final var health = application.getHealthInclusion();
    values.put(
        "healthInclusion",
        health == null
            ? null
            : Arrays.asList(
                health.isReceivesReasonableAdjustments(), health.getAdjustmentDetails()));
    final var responsible = application.getResponsible();
    values.put(
        "responsible",
        responsible == null
            ? null
            : Arrays.asList(
                responsible.getFullName(),
                responsible.getDocumentNumber(),
                responsible.getOccupation(),
                responsible.getPhoneNumber(),
                responsible.getEmail(),
                responsible.getEducationLevel()));
    final var preference = application.getPreference();
    values.put(
        "preference",
        preference == null
            ? null
            : Arrays.asList(
                preference.getPreferredShift(),
                preference.isAllowsImageUse(),
                preference.isReenrolling(),
                preference.getPreviousTeacher()));
    values.put(
        "selectedSpaces",
        application.getSelectedSpaces().stream()
            .map(
                selection ->
                    selection.getStudyPlanSpace().getId()
                        + "/"
                        + (selection.getInstrument() == null
                            ? ""
                            : selection.getInstrument().getId()))
            .sorted()
            .toList());
    values.put(
        "courseSelections",
        application.getCourseSelections().stream()
            .map(
                selection ->
                    selection.getCourse().getId()
                        + "/"
                        + (selection.getPreferredTeacher() == null
                            ? ""
                            : selection.getPreferredTeacher().getId()))
            .sorted()
            .toList());
    return values;
  }
}
