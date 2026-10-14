package ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces;

import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.createInstitution;
import static ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData.persist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentPeriodStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import ar.edu.utn.frvm.typeit.boero_api.support.JpaAuditingTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Guards against the Hibernate 7 failure combining PESSIMISTIC_WRITE with a fetch graph on
 * EnrollmentApplication ("Unable to determine TableReference ... educationBackground"), which broke
 * the approve/reject flows with a 500.
 */
@DataJpaTest
@Import(JpaAuditingTestConfig.class)
class EnrollmentApplicationLockingTest {

  @Autowired private EntityManager entityManager;
  @Autowired private EnrollmentApplicationRepository applicationRepository;

  @Test
  @DisplayName(
      "Should lock an application for update without resolving one-to-one table references")
  void findByIdAndInstitutionIdForUpdate_doesNotFailOnOneToOneAssociations() {
    Institution institution = createInstitution(entityManager, "boero-locking");
    Person person = persist(entityManager, InstitutionalTestData.person(institution, "12345678"));
    TrainingPath trainingPath =
        persist(entityManager, TrainingPath.create(institution, "CAV", null));
    AcademicYear academicYear =
        persist(
            entityManager,
            AcademicYear.create(
                institution,
                2026,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 1, 15)));
    academicYear.transitionTo(
        ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus.ACTIVE);
    EnrollmentPeriod period =
        persist(
            entityManager,
            EnrollmentPeriod.builder()
                .institution(institution)
                .academicYear(academicYear)
                .name("Inscripción 2026")
                .startDate(Instant.now())
                .endDate(Instant.now().plusSeconds(3600))
                .status(EnrollmentPeriodStatus.OPEN)
                .build());
    EnrollmentApplication application =
        persist(
            entityManager,
            EnrollmentApplication.builder()
                .institution(institution)
                .applicantPerson(person)
                .trainingPathId(trainingPath.getId())
                .trainingPath(trainingPath)
                .academicYear(academicYear)
                .enrollmentPeriod(period)
                .status(EnrollmentApplicationStatus.SUBMITTED)
                .build());
    entityManager.flush();
    entityManager.clear();

    assertThatCode(
            () ->
                applicationRepository.findByIdAndInstitutionIdForUpdate(
                    institution.getId(), application.getId()))
        .doesNotThrowAnyException();
    assertThat(
            applicationRepository.findByIdAndInstitutionIdForUpdate(
                institution.getId(), application.getId()))
        .isPresent();
  }
}
