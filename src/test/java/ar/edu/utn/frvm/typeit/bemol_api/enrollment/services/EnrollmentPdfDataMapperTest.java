package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplicationCourse;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationCourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfSettingsResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class EnrollmentPdfDataMapperTest {
  private static final Instant GENERATED_AT = Instant.parse("2026-11-05T12:00:00Z");

  @Test
  void identifiesSelectionTypeAndUsesTheActualLevelOrder() {
    final var application = application(2);

    final var result =
        EnrollmentPdfDataMapper.from(
            application, EnrollmentPdfSettingsResponse.defaults(), true, GENERATED_AT, null);

    assertThat(result.coursesSelected()).isTrue();
    assertThat(result.selections())
        .singleElement()
        .satisfies(
            selection -> {
              assertThat(selection.level()).isEqualTo("Nivel 2");
              assertThat(selection.name()).isEqualTo("Espacio académico 8");
            });
  }

  @Test
  void missingLevelIsNotInferredFromOtherNames() {
    final var result =
        EnrollmentPdfDataMapper.from(
            application(null), EnrollmentPdfSettingsResponse.defaults(), true, GENERATED_AT, null);

    assertThat(result.coursesSelected()).isTrue();
    assertThat(result.selections())
        .extracting(selection -> selection.level())
        .containsExactly("Sin informar");
  }

  @Test
  void permissionControlsBothSelectionDataAndItsType() {
    final var result =
        EnrollmentPdfDataMapper.from(
            application(2), EnrollmentPdfSettingsResponse.defaults(), false, GENERATED_AT, null);

    assertThat(result.selections()).isEmpty();
    assertThat(result.coursesSelected()).isFalse();
  }

  private EnrollmentApplication application(final @Nullable Integer levelOrder) {
    final var country = InstitutionalTestData.country("ARG");
    final var province = InstitutionalTestData.province(country, "1");
    final var institution =
        Institution.builder()
            .id(UUID.randomUUID())
            .name("Institución")
            .city(InstitutionalTestData.city(province, "2"))
            .build();
    final var path = TrainingPath.create(institution, "Trayecto", null);
    final var plan = StudyPlan.create(institution, path, "Plan de estudios Nivel 9", null, null);
    final var year = AcademicYear.create(institution, 2026, null, null, LocalDate.of(2026, 1, 1));
    final var academicSpace = mock(AcademicSpace.class);
    when(academicSpace.getName()).thenReturn("Espacio académico 8");
    when(academicSpace.getFormat()).thenReturn(AcademicSpaceFormat.GRUPAL);
    final var space = mock(StudyPlanSpace.class);
    when(space.getAcademicSpace()).thenReturn(academicSpace);
    when(space.getStudyPlan()).thenReturn(plan);
    when(space.getAcademicLevel())
        .thenReturn(
            levelOrder == null
                ? null
                : AcademicLevel.create(plan, "Primer nivel 99", levelOrder, null));
    final var person =
        Person.builder()
            .institution(institution)
            .firstName("María")
            .lastName("Muñoz")
            .documentNumber("12345678")
            .build();
    final var application =
        EnrollmentApplication.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .applicantPerson(person)
            .trainingPath(path)
            .academicYear(year)
            .status(EnrollmentApplicationStatus.SUBMITTED)
            .build();
    final var course = mock(Course.class);
    when(course.getStudyPlanSpace()).thenReturn(space);
    application.addCourseSelection(
        EnrollmentApplicationCourse.builder()
            .course(course)
            .status(EnrollmentApplicationCourseStatus.PENDING)
            .build());
    return application;
  }
}
