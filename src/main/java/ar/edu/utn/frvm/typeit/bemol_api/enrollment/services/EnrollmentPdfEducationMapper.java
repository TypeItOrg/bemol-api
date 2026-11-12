package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfDataMapper.answer;
import static ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfDataMapper.field;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantEducationBackground;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EducationLevel;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class EnrollmentPdfEducationMapper {
  private EnrollmentPdfEducationMapper() {}

  static Section from(final ApplicantEducationBackground education) {
    final var fields = new ArrayList<Field>();
    final var level = education.getEducationLevel();
    final var attending = education.getCurrentlyStudying();
    fields.add(field("Asiste actualmente", answer(attending)));
    fields.add(
        field(
            Boolean.TRUE.equals(attending)
                ? "Nivel actual"
                : Boolean.FALSE.equals(attending) ? "Máximo nivel alcanzado" : "Nivel educativo",
            educationLevel(level)));
    if (level != EducationLevel.NO_SCHOOLING || education.getSchoolOrigin() != null) {
      fields.add(
          field(
              Boolean.FALSE.equals(attending)
                  ? "Última institución educativa"
                  : "Institución educativa",
              education.getSchoolOrigin()));
    }
    if (education.getCurrentGradeYear() != null
        || Boolean.TRUE.equals(attending)
            && level != null
            && level != EducationLevel.NO_SCHOOLING) {
      fields.add(field("Sala, grado o año", education.getCurrentGradeYear()));
    }
    // At secondary level "Secundario completo" already answers this question.
    if (level != EducationLevel.SECONDARY
        && (education.getLevelCompleted() != null
            || Boolean.FALSE.equals(attending)
                && level != null
                && level != EducationLevel.NO_SCHOOLING)) {
      fields.add(field("Nivel completado", answer(education.getLevelCompleted())));
    }
    if (education.getSecondaryCompleted() != null
        || level != null && level.requiresSecondaryCompletionAnswer()) {
      fields.add(field("Secundario completo", answer(education.getSecondaryCompleted())));
    }
    if (education.getSecondaryDegreeTitle() != null
        || Boolean.TRUE.equals(education.getSecondaryCompleted())) {
      fields.add(field("Título secundario obtenido", education.getSecondaryDegreeTitle()));
    }
    if (education.getSecondarySchool() != null) {
      fields.add(field("Escuela secundaria", education.getSecondarySchool()));
    }
    return new Section("Escolaridad", List.copyOf(fields));
  }

  private static String educationLevel(final @Nullable EducationLevel level) {
    if (level == null) {
      return "Sin informar";
    }
    return switch (level) {
      case NO_SCHOOLING -> "Sin escolarización";
      case INITIAL -> "Inicial";
      case PRIMARY -> "Primario";
      case SECONDARY -> "Secundario";
      case NON_UNIVERSITY_HIGHER -> "Superior no universitario";
      case UNIVERSITY -> "Universitario";
    };
  }
}
