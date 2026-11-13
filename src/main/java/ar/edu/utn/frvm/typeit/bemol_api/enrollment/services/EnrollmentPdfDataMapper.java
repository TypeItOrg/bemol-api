package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.CourseEnrollmentCourseMetadata;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Field;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData.Selection;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import java.time.Instant;
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
    final var nativeSections = EnrollmentPdfNativeSections.from(application);
    final var form = application.getFormState();
    final var sections =
        form.getFormVersion() == null
            ? EnrollmentPdfNativeSections.base(nativeSections)
            : EnrollmentPdfFormSections.from(
                form.definition(), nativeSections, form.getCustomAnswers());

    final var selections = new ArrayList<Selection>();
    final boolean coursesSelected = canReadCourses && !application.getCourseSelections().isEmpty();
    if (coursesSelected) {
      for (final var selection : application.getCourseSelections()) {
        final var course = selection.getCourse();
        final var metadata = CourseEnrollmentCourseMetadata.from(course);
        selections.add(
            new Selection(
                metadata.academicSpaceName(),
                levelName(course.getStudyPlanSpace().getAcademicLevel()),
                instrument(metadata.instrumentName()),
                metadata.studyPlanName(),
                switch (selection.getStatus()) {
                  case PENDING -> "Pendiente";
                  case WAITLISTED -> "En lista de espera";
                  case ENROLLED -> "Inscripta";
                  case REJECTED -> "Rechazada";
                  case CANCELLED -> "Cancelada";
                }));
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
        .hasResponsible(
            application.getResponsible() != null
                && EnrollmentPdfNativeSections.hasResponsibleData(application.getResponsible()))
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

  /** Spaces without an instrument do not lack data: the column does not apply to them. */
  static String instrument(final @Nullable String name) {
    return name == null || name.isBlank() ? "No corresponde" : name;
  }

  static Field field(final String label, final @Nullable Object value) {
    return new Field(label, text(value));
  }

  static String text(final @Nullable Object value) {
    return value == null || value.toString().isBlank() ? "Sin informar" : value.toString();
  }

  static String join(final @Nullable String... values) {
    return Stream.of(values)
        .filter(Objects::nonNull)
        .filter(value -> !value.isBlank())
        .collect(Collectors.joining(", "));
  }

  static String answer(final @Nullable Boolean answer) {
    return answer == null ? "Sin informar" : answer ? "Sí" : "No";
  }
}
