package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode.*;

import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.ScopedResourceNotFoundException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record AcademicSelectionSource(
    String entity, @Nullable String pathExpression, Set<PermissionCode> permissions) {
  static AcademicSelectionSource forResource(final String resource) {
    // Only these server-owned entity/path expressions may enter the dynamic JPQL query.
    return switch (resource) {
      case "training-paths" ->
          new AcademicSelectionSource(
              "TrainingPath",
              "item.id",
              Set.of(
                  TRAINING_PATH_READ,
                  STUDY_PLAN_READ,
                  STUDY_PLAN_CREATE,
                  STUDY_PLAN_UPDATE,
                  COURSE_READ,
                  COURSE_CREATE,
                  ENROLLMENT_PERIOD_READ,
                  ENROLLMENT_PERIOD_CREATE,
                  ENROLLMENT_PERIOD_UPDATE,
                  ENROLLMENT_APPLICATION_READ,
                  COURSE_ENROLLMENT_READ));
      case "study-plans" ->
          new AcademicSelectionSource(
              "StudyPlan",
              "item.trainingPath.id",
              Set.of(
                  STUDY_PLAN_READ,
                  COURSE_CREATE,
                  COURSE_UPDATE,
                  COURSE_READ,
                  ENROLLMENT_PERIOD_READ,
                  ENROLLMENT_PERIOD_CREATE,
                  ENROLLMENT_PERIOD_UPDATE,
                  COURSE_ENROLLMENT_READ));
      case "academic-years" ->
          new AcademicSelectionSource(
              "AcademicYear",
              null,
              Set.of(
                  ACADEMIC_YEAR_READ,
                  COURSE_READ,
                  COURSE_CREATE,
                  COURSE_UPDATE,
                  ENROLLMENT_PERIOD_READ,
                  ENROLLMENT_PERIOD_CREATE,
                  ENROLLMENT_PERIOD_UPDATE,
                  COURSE_ENROLLMENT_READ));
      case "academic-spaces" ->
          new AcademicSelectionSource(
              "AcademicSpace",
              null,
              Set.of(ACADEMIC_SPACE_READ, STUDY_PLAN_CURRICULUM_UPDATE, COURSE_READ));
      case "instruments" ->
          new AcademicSelectionSource(
              "Instrument",
              null,
              Set.of(INSTRUMENT_READ, COURSE_READ, COURSE_CREATE, COURSE_UPDATE));
      default -> throw new ScopedResourceNotFoundException();
    };
  }
}
