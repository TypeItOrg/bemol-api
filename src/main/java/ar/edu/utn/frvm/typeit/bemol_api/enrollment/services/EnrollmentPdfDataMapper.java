package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentCourseMetadata;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Selection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class EnrollmentPdfDataMapper {
  private EnrollmentPdfDataMapper() {}

  static EnrollmentPdfData from(
      final EnrollmentApplication application,
      final EnrollmentPdfSettingsResponse settings,
      final boolean canReadCourses,
      final Instant generatedAt,
      final @Nullable Instant submittedAt) {
    final var person = application.getApplicantPerson();
    final var address = person.getAddress();
    final var sections = new ArrayList<Section>();
    sections.add(
        new Section(
            "Estudiante",
            List.of(
                field("Apellido y nombre", join(person.getLastName(), person.getFirstName())),
                field("Documento", person.getDocumentNumber()),
                field(
                    "Fecha de nacimiento",
                    person.getBirthDate() == null
                        ? null
                        : person.getBirthDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                field(
                    "Lugar de nacimiento",
                    person.getBirthCity() == null ? null : person.getBirthCity().getName()),
                field(
                    "Nacionalidad",
                    person.getNationalityCountry() == null
                        ? null
                        : person.getNationalityCountry().getName()),
                field(
                    "Domicilio",
                    address == null
                        ? null
                        : join(
                            address.getStreet(),
                            address.getNumber(),
                            address.getFloor(),
                            address.getApartment(),
                            address.getNeighborhood())),
                field(
                    "Localidad y provincia",
                    address == null
                        ? null
                        : join(
                            address.getCity().getName(),
                            address.getCity().getProvince().getName())),
                field("Teléfono", person.getPhoneNumber()),
                field("Correo electrónico", person.getEmail()))));
    final var year = application.commonAcademicYear();
    final var path = application.getTrainingPath();
    sections.add(
        new Section(
            "Inscripción",
            List.of(
                field("Propuesta académica", path == null ? null : path.getName()),
                field("Ciclo lectivo", year == null ? null : year.getYear()),
                field(
                    "Período",
                    application.getEnrollmentPeriod() == null
                        ? null
                        : application.getEnrollmentPeriod().getName()))));
    final var education = application.getEducationBackground();
    if (education != null) {
      sections.add(EnrollmentPdfEducationMapper.from(education));
    }

    final var responsible = application.getResponsible();
    if (responsible != null) {
      sections.add(
          new Section(
              "Responsable",
              List.of(
                  field("Apellido y nombre", responsible.getFullName()),
                      field("Documento", responsible.getDocumentNumber()),
                  field("Ocupación", responsible.getOccupation()),
                      field("Teléfono", responsible.getPhoneNumber()),
                  field("Correo electrónico", responsible.getEmail()),
                      field("Nivel de instrucción", responsible.getEducationLevel()))));
    }
    final var health = application.getHealthInclusion();
    if (health != null) {
      final var fields = new ArrayList<Field>();
      fields.add(
          field("Recibe ajustes razonables", answer(health.isReceivesReasonableAdjustments())));
      if (health.isReceivesReasonableAdjustments() || health.getAdjustmentDetails() != null) {
        fields.add(field("Detalle", health.getAdjustmentDetails()));
      }
      sections.add(new Section("Inclusión", List.copyOf(fields)));
    }
    final var preference = application.getPreference();
    if (preference != null) {
      final var fields = new ArrayList<Field>();
      fields.add(field("Turno preferido", preference.getPreferredShift()));
      fields.add(field("Reinscripción", answer(preference.isReenrolling())));
      if (preference.isReenrolling() || preference.getPreviousTeacher() != null) {
        fields.add(field("Docente anterior", preference.getPreviousTeacher()));
      }
      fields.add(field("Autoriza el uso de imágenes", answer(preference.isAllowsImageUse())));
      sections.add(new Section("Preferencias y autorizaciones", List.copyOf(fields)));
    }

    final var selections = new ArrayList<Selection>();
    final boolean coursesSelected = canReadCourses && !application.getCourseSelections().isEmpty();
    if (canReadCourses) {
      if (coursesSelected) {
        for (final var selection : application.getCourseSelections()) {
          final var course = selection.getCourse();
          final var metadata = CourseEnrollmentCourseMetadata.from(course);
          selections.add(
              new Selection(
                  metadata.academicSpaceName(),
                  levelName(course.getStudyPlanSpace().getAcademicLevel()),
                  text(metadata.instrumentName()),
                  metadata.studyPlanName(),
                  switch (selection.getStatus()) {
                    case PENDING -> "Pendiente";
                    case WAITLISTED -> "En lista de espera";
                    case ENROLLED -> "Inscripta";
                    case REJECTED -> "Rechazada";
                    case CANCELLED -> "Cancelada";
                  }));
        }
      } else {
        for (final var selection : application.getSelectedSpaces()) {
          final var space = selection.getStudyPlanSpace();
          selections.add(
              new Selection(
                  space.getAcademicSpace().getName(),
                  levelName(space.getAcademicLevel()),
                  text(
                      selection.getInstrument() == null
                          ? null
                          : selection.getInstrument().getName()),
                  space.getStudyPlan().getName(),
                  "Solicitada"));
        }
      }
    }
    return EnrollmentPdfData.builder()
        .institution(institution(application.getInstitution()))
        .generatedAt(generatedAt)
        .submittedAt(submittedAt)
        .settings(settings)
        .sections(List.copyOf(sections))
        .selections(List.copyOf(selections))
        .coursesSelected(coursesSelected)
        .hasResponsible(responsible != null)
        .build();
  }

  private static String levelName(final @Nullable AcademicLevel level) {
    return level == null ? "Sin informar" : AcademicLevel.derivedName(level.getDisplayOrder());
  }

  static EnrollmentPdfData.InstitutionDetails institution(final Institution institution) {
    return new EnrollmentPdfData.InstitutionDetails(
        institution.getName(),
        text(
            join(
                institution.getStreet(),
                institution.getNumber(),
                institution.getNeighborhood(),
                institution.getCity().getName(),
                institution.getCity().getProvince().getName())),
        text(institution.getPhoneNumber()),
        text(institution.getEmail()));
  }

  static Field field(final String label, final @Nullable Object value) {
    return new Field(label, text(value));
  }

  static String text(final @Nullable Object value) {
    return value == null || value.toString().isBlank() ? "Sin informar" : value.toString();
  }

  private static String join(final @Nullable String... values) {
    return Stream.of(values)
        .filter(Objects::nonNull)
        .filter(value -> !value.isBlank())
        .collect(Collectors.joining(", "));
  }

  static String answer(final @Nullable Boolean answer) {
    return answer == null ? "Sin informar" : answer ? "Sí" : "No";
  }
}
