package ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces;

import static ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData.createInstitution;
import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * Reproduces the report where non-instrumental courses never reach the enrollment wizard while
 * instrumental ones do. If the {@code instrument} fetch join excludes courses without instrument,
 * this test fails and pinpoints a code bug instead of data.
 */
@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class CourseInstrumentFilterTest {

  @Autowired private EntityManager entityManager;
  @Autowired private CourseRepository courseRepository;

  @Test
  @DisplayName("Should return instrumental and non-instrumental active courses alike")
  void findActiveByTrainingPathAndAcademicYear_includesCoursesWithoutInstrument() {
    Institution institution = createInstitution(entityManager, "boero-course-filter");
    TrainingPath trainingPath = persist(TrainingPath.create(institution, "CAV", null));
    StudyPlan plan =
        persist(
            StudyPlan.create(institution, trainingPath, "Plan", LocalDate.of(2026, 1, 1), null));
    AcademicLevel level = persist(AcademicLevel.create(plan, "Nivel 1", 1, null));
    AcademicSpace instrumentalSpace =
        persist(
            AcademicSpace.create(
                institution,
                "Instrumento individual",
                null,
                AcademicSpaceType.SUBJECT,
                AcademicSpaceFormat.INDIVIDUAL,
                true));
    AcademicSpace plainSpace =
        persist(
            AcademicSpace.create(
                institution,
                "Teoría musical",
                null,
                AcademicSpaceType.SUBJECT,
                AcademicSpaceFormat.GRUPAL));
    StudyPlanSpace instrumentalPlacement =
        persist(
            StudyPlanSpace.create(
                institution,
                plan,
                instrumentalSpace,
                level,
                RequirementType.REQUIRED,
                1,
                ApprovalMode.PROMOTION));
    StudyPlanSpace plainPlacement =
        persist(
            StudyPlanSpace.create(
                institution,
                plan,
                plainSpace,
                level,
                RequirementType.REQUIRED,
                2,
                ApprovalMode.PROMOTION));
    Instrument instrument = persist(Instrument.create(institution, "Guitarra", null));
    AcademicYear academicYear =
        persist(AcademicYear.create(institution, 2026, null, null, LocalDate.of(2026, 1, 15)));
    academicYear.transitionTo(AcademicYearStatus.ACTIVE);
    Course instrumentalCourse =
        persist(Course.create(institution, instrumentalPlacement, academicYear, instrument));
    Course plainCourse = persist(Course.create(institution, plainPlacement, academicYear, null));
    entityManager.flush();
    entityManager.clear();

    final var result =
        courseRepository.findActiveByTrainingPathAndAcademicYear(
            institution.getId(),
            trainingPath.getId(),
            academicYear.getId(),
            null,
            PageRequest.of(0, 50));

    assertThat(result.getContent())
        .extracting(mappedCourse -> mappedCourse.getId())
        .containsExactlyInAnyOrder(instrumentalCourse.getId(), plainCourse.getId());
  }

  private <T> T persist(final T entity) {
    entityManager.persist(entity);
    return entity;
  }
}
