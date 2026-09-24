package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentApplicationNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListEnrollmentApplicationTrainingPathsUseCaseTest {

  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private EnrollmentApplicationRepository enrollmentApplicationRepository;
  @Mock private TrainingPathRepository trainingPathRepository;

  @Test
  @DisplayName("Should keep a course-based application in its selected training path")
  void listsSelectedTrainingPathForCourseBasedApplication() {
    final var application = application();
    final var principal = principal(application);
    final var useCase = useCase();
    givenApplicant(principal, application.getApplicantPerson());
    given(
            enrollmentApplicationRepository.findAccessibleByIdAndInstitutionId(
                principal.institutionId(), principal.personId(), application.getId()))
        .willReturn(Optional.of(application));

    final var response = useCase.execute(principal, application.getId());

    verifyNoInteractions(trainingPathRepository);
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().name()).isEqualTo("Base");
    assertThat(response.getFirst().institutionId()).isEqualTo(application.getInstitution().getId());
  }

  @Test
  @DisplayName("Should reject an application the caller neither owns nor guards")
  void throwsWhenApplicationBelongsToAnotherApplicant() {
    final var application = application();
    final var principal = principal(application);
    final var useCase = useCase();
    givenApplicant(principal, application.getApplicantPerson());
    given(
            enrollmentApplicationRepository.findAccessibleByIdAndInstitutionId(
                principal.institutionId(), principal.personId(), application.getId()))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(principal, application.getId()))
        .isInstanceOf(EnrollmentApplicationNotFoundException.class);
  }

  private ListEnrollmentApplicationTrainingPathsUseCase useCase() {
    return new ListEnrollmentApplicationTrainingPathsUseCase(
        new ApplicantEnrollmentGuard(personRepository, personRoleAssignmentRepository),
        enrollmentApplicationRepository);
  }

  private void givenApplicant(final JwtAuthenticatedUser principal, final Person person) {
    given(
            personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
                principal.personId(), principal.institutionId(), "APPLICANT"))
        .willReturn(true);
    given(
            personRepository.findByIdAndInstitution_Id(
                principal.personId(), principal.institutionId()))
        .willReturn(Optional.of(person));
  }

  private static EnrollmentApplication application() {
    final var institution =
        Institution.builder().id(UUID.randomUUID()).name("Conservatorio").build();
    final var person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .documentNumber("12345678")
            .email("ana@example.com")
            .build();
    final var path = TrainingPath.create(institution, "Base", null);
    final var plan = StudyPlan.create(institution, path, "Plan", LocalDate.of(2026, 3, 1), null);
    final var year =
        AcademicYear.create(
            institution,
            2026,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2026, 1, 1));
    return EnrollmentApplication.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .applicantPerson(person)
        .studyPlan(plan)
        .academicYear(year)
        .status(EnrollmentApplicationStatus.DRAFT)
        .build();
  }

  private static JwtAuthenticatedUser principal(final EnrollmentApplication application) {
    return JwtAuthenticatedUser.builder()
        .userId(UUID.randomUUID())
        .personId(application.getApplicantPerson().getId())
        .documentNumber("12345678")
        .institutionId(application.getInstitution().getId())
        .sessionId(UUID.randomUUID())
        .tokenId("token-id")
        .build();
  }
}
