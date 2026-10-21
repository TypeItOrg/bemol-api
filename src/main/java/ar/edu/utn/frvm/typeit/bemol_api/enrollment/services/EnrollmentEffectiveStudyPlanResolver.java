package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.StudyPlanNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for which {@link StudyPlan} an enrollment application is effectively
 * working against. Read flows (listing spaces/instruments) resolve against the application's
 * current plan; the draft validator resolves against a candidate {@code trainingPathId} before the
 * applicant's career selection is actually persisted.
 */
@Component
public class EnrollmentEffectiveStudyPlanResolver {

  public StudyPlan resolve(final UUID institutionId, final EnrollmentApplication application) {
    return resolveForTrainingPath(institutionId, application, null);
  }

  public StudyPlan resolveForTrainingPath(
      final UUID institutionId,
      final EnrollmentApplication application,
      final @Nullable UUID trainingPathId) {
    if (application.getStudyPlan() == null || application.getEnrollmentPeriod() == null) {
      throw new StudyPlanNotFoundException();
    }
    if (trainingPathId == null
        || trainingPathId.equals(application.getStudyPlan().getTrainingPath().getId())) {
      return application.getStudyPlan();
    }

    final var candidates =
        application.getEnrollmentPeriod().getOfferings().stream()
            .map(offering -> offering.getStudyPlan())
            .filter(
                plan ->
                    plan.getInstitution().getId().equals(institutionId)
                        && plan.getTrainingPath().getId().equals(trainingPathId))
            .toList();
    if (candidates.size() != 1) {
      throw new StudyPlanNotFoundException();
    }
    return candidates.getFirst();
  }
}
