package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfDataMapper.answer;
import static ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfDataMapper.field;
import static ar.edu.utn.frvm.typeit.bemol_api.enrollment.services.EnrollmentPdfDataMapper.join;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.ApplicantResponsible;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentFormNativeBlock;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Section;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class EnrollmentPdfNativeSections {
  private EnrollmentPdfNativeSections() {}

  static Map<EnrollmentFormNativeBlock, Section> from(final EnrollmentApplication application) {
    final var sections =
        new EnumMap<EnrollmentFormNativeBlock, Section>(EnrollmentFormNativeBlock.class);
    final var person = application.getApplicantPerson();
    final var address = person.getAddress();
    sections.put(
        EnrollmentFormNativeBlock.PERSONAL_DATA,
        new Section(
            "Estudiante",
            List.of(
                field("Apellido y nombre", join(person.getLastName(), person.getFirstName())),
                field("Documento nacional de identidad", person.getDocumentNumber()),
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
    final var academicFields = new ArrayList<Field>();
    academicFields.add(field("Propuesta académica", path == null ? null : path.getName()));
    academicFields.add(field("Ciclo lectivo", year == null ? null : year.getYear()));
    if (application.getEnrollmentPeriod() != null) {
      academicFields.add(field("Período", application.getEnrollmentPeriod().getName()));
    }
    sections.put(
        EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
        new Section("Inscripción", List.copyOf(academicFields)));
    final var education = application.getEducationBackground();
    if (education != null) {
      sections.put(
          EnrollmentFormNativeBlock.SCHOOLING, EnrollmentPdfEducationMapper.from(education));
    }
    final var responsible = application.getResponsible();
    if (responsible != null && hasResponsibleData(responsible)) {
      sections.put(
          EnrollmentFormNativeBlock.RESPONSIBLE,
          new Section(
              "Responsable",
              List.of(
                  field("Apellido y nombre", responsible.getFullName()),
                  field("Documento nacional de identidad", responsible.getDocumentNumber()),
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
      sections.put(
          EnrollmentFormNativeBlock.HEALTH_INCLUSION,
          new Section("Inclusión", List.copyOf(fields)));
    }
    final var preference = application.getPreference();
    if (preference != null) {
      // The shift is printed in the enrollment section, so it is kept apart from other preferences.
      sections.put(
          EnrollmentFormNativeBlock.PREFERRED_SHIFT,
          new Section(
              "Turno preferido",
              List.of(field("Turno preferido", preference.getPreferredShift()))));
      final var fields = new ArrayList<Field>();
      fields.add(field("Reinscripción", answer(preference.isReenrolling())));
      if (preference.isReenrolling() || preference.getPreviousTeacher() != null) {
        fields.add(field("Docente anterior", preference.getPreviousTeacher()));
      }
      fields.add(field("Autoriza el uso de imágenes", answer(preference.isAllowsImageUse())));
      sections.put(
          EnrollmentFormNativeBlock.PREFERENCES,
          new Section("Preferencias y autorizaciones", List.copyOf(fields)));
    }
    return Map.copyOf(sections);
  }

  static boolean hasResponsibleData(final ApplicantResponsible responsible) {
    return Stream.of(
            responsible.getFullName(),
            responsible.getDocumentNumber(),
            responsible.getOccupation(),
            responsible.getPhoneNumber(),
            responsible.getEmail(),
            responsible.getEducationLevel())
        .anyMatch(value -> value != null && !value.toString().isBlank());
  }

  static List<Section> legacy(final Map<EnrollmentFormNativeBlock, Section> sections) {
    final var printed =
        new EnumMap<EnrollmentFormNativeBlock, Section>(EnrollmentFormNativeBlock.class);
    printed.putAll(sections);
    printed.computeIfPresent(
        EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
        (block, section) -> withShift(section, sections));
    return List.of(
            EnrollmentFormNativeBlock.PERSONAL_DATA,
            EnrollmentFormNativeBlock.ACADEMIC_SELECTION,
            EnrollmentFormNativeBlock.SCHOOLING,
            EnrollmentFormNativeBlock.RESPONSIBLE,
            EnrollmentFormNativeBlock.HEALTH_INCLUSION,
            EnrollmentFormNativeBlock.PREFERENCES)
        .stream()
        .map(printed::get)
        .filter(Objects::nonNull)
        .toList();
  }

  /** Appends the preferred shift, when captured, to the always-printed enrollment section. */
  static Section withShift(
      final Section enrollment, final Map<EnrollmentFormNativeBlock, Section> sections) {
    final var shift = sections.get(EnrollmentFormNativeBlock.PREFERRED_SHIFT);
    if (shift == null) {
      return enrollment;
    }
    final var fields = new ArrayList<>(enrollment.fields());
    fields.addAll(shift.fields());
    return new Section(enrollment.title(), List.copyOf(fields), enrollment.allowPageBreaks());
  }
}
