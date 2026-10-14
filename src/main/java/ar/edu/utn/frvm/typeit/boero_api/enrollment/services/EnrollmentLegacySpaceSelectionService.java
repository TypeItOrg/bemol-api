package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.InstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplicationSpace;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.EnrollmentDraftData;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentLegacySpaceSelectionService {
  private final EnrollmentDraftDataValidator validator;
  private final EnrollmentCourseSelectionService courses;
  private final StudyPlanSpaceRepository spaces;
  private final InstrumentRepository instruments;

  @Transactional(propagation = Propagation.MANDATORY)
  public void update(final EnrollmentApplication application, final EnrollmentDraftData data) {
    if (application.getStudyPlan() == null
        || application.getEnrollmentPeriod() == null
        || data.getCourses() != null) {
      return;
    }

    final var effectivePlan =
        validator.validate(application.getInstitution().getId(), application, data);
    if (!effectivePlan.getId().equals(application.getStudyPlan().getId())) {
      // A plan change conflicts per course, not merely because another draft uses the path.
      for (final var selection : application.getCourseSelections()) {
        courses.requireAvailable(application, selection.getCourse().getId());
      }
      application.changeStudyPlan(effectivePlan);
    }

    if (data.getAcademicSpaceSelection() == null
        || data.getAcademicSpaceSelection().getStudyPlanSpaceIds() == null) {
      return;
    }

    final Map<UUID, UUID> instrumentIds =
        data.getInstrumentSelection() != null
                && data.getInstrumentSelection().getStudyPlanSpaceInstrumentIds() != null
            ? data.getInstrumentSelection().getStudyPlanSpaceInstrumentIds()
            : Map.of();
    final var desiredIds = new HashSet<>(data.getAcademicSpaceSelection().getStudyPlanSpaceIds());

    // Reconcile in place: Hibernate inserts before orphan deletes, so clear+recreate
    // would violate the application/space unique constraint on an unchanged autosave.
    application
        .getSelectedSpaces()
        .removeIf(selection -> !desiredIds.contains(selection.getStudyPlanSpace().getId()));
    final var existingBySpace =
        application.getSelectedSpaces().stream()
            .collect(
                Collectors.toMap(
                    selection -> selection.getStudyPlanSpace().getId(), Function.identity()));

    for (final var spaceId : desiredIds) {
      final var instrument = findInstrument(instrumentIds.get(spaceId));
      final var existing = existingBySpace.get(spaceId);
      if (existing != null) {
        existing.setInstrument(instrument);
        continue;
      }

      final var space =
          spaces
              .findById(spaceId)
              .orElseThrow(
                  () ->
                      new EnrollmentValidationException(
                          EnrollmentMessages.SPACE_ID_NOT_FOUND + spaceId));
      application.addSelectedSpace(
          EnrollmentApplicationSpace.builder()
              .enrollmentApplication(application)
              .studyPlanSpace(space)
              .instrument(instrument)
              .build());
    }
  }

  private @Nullable Instrument findInstrument(final @Nullable UUID id) {
    if (id == null) {
      return null;
    }

    return instruments
        .findById(id)
        .orElseThrow(
            () ->
                new EnrollmentValidationException(EnrollmentMessages.INSTRUMENT_ID_NOT_FOUND + id));
  }
}
