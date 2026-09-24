package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceType;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.ApprovalMode;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.RequirementType;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceInstrumentRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.StudyPlanSpaceRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriod;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.EnrollmentPeriodOffering;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EnrollmentApplicationStatus;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.EnrollmentApplicationRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListEnrollmentApplicationStudyPlanSpacesUseCaseTest {

  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private EnrollmentApplicationRepository enrollmentApplicationRepository;
  @Mock private StudyPlanRepository studyPlanRepository;
  @Mock private StudyPlanSpaceRepository studyPlanSpaceRepository;
  @Mock private StudyPlanSpaceInstrumentRepository studyPlanSpaceInstrumentRepository;

  @Test
  @DisplayName("Should list eligible study plan spaces for an application")
  void listsEligibleStudyPlanSpaces() {
    final var application = application();
    final var principal = principal(application);
    final var academicSpace =
        AcademicSpace.create(
            application.getInstitution(),
            "Armonia I",
            "",
            AcademicSpaceType.SUBJECT,
            AcademicSpaceFormat.INDIVIDUAL);
    final var studyPlanSpace =
        StudyPlanSpace.create(
            application.getInstitution(),
            requireNonNull(application.getStudyPlan()),
            academicSpace,
            null,
            RequirementType.REQUIRED,
            1,
            ApprovalMode.FINAL_EXAM);
    final var studyPlanSpaceId = UUID.randomUUID();
    final var persistedStudyPlanSpace = mock(StudyPlanSpace.class);
    given(persistedStudyPlanSpace.getId()).willReturn(studyPlanSpaceId);
    given(persistedStudyPlanSpace.getStudyPlan()).willReturn(studyPlanSpace.getStudyPlan());
    given(persistedStudyPlanSpace.getAcademicSpace()).willReturn(studyPlanSpace.getAcademicSpace());
    given(persistedStudyPlanSpace.getAcademicLevel()).willReturn(studyPlanSpace.getAcademicLevel());
    given(persistedStudyPlanSpace.getRequirementType())
        .willReturn(studyPlanSpace.getRequirementType());
    given(persistedStudyPlanSpace.getDisplayOrder()).willReturn(studyPlanSpace.getDisplayOrder());
    given(persistedStudyPlanSpace.getApprovalMode()).willReturn(studyPlanSpace.getApprovalMode());
    final var useCase =
        new ListEnrollmentApplicationStudyPlanSpacesUseCase(
            new ApplicantEnrollmentGuard(personRepository, personRoleAssignmentRepository),
            enrollmentApplicationRepository,
            new EnrollmentEffectiveStudyPlanResolver(),
            studyPlanSpaceRepository,
            studyPlanSpaceInstrumentRepository);
    givenApplicant(principal, application.getApplicantPerson());
    given(
            enrollmentApplicationRepository.findAccessibleByIdAndInstitutionId(
                principal.institutionId(), principal.personId(), application.getId()))
        .willReturn(Optional.of(application));
    given(
            studyPlanSpaceRepository.findEligibleByStudyPlanId(
                principal.institutionId(), requireNonNull(application.getStudyPlan()).getId()))
        .willReturn(List.of(persistedStudyPlanSpace));
    given(
            studyPlanSpaceInstrumentRepository.findActiveByStudyPlanSpaceIds(
                principal.institutionId(), List.of(studyPlanSpaceId)))
        .willReturn(List.of());

    final var response = useCase.execute(principal, application.getId());

    assertThat(response).hasSize(1);
    assertThat(response.getFirst().studyPlanId())
        .isEqualTo(requireNonNull(application.getStudyPlan()).getId());
    assertThat(response.getFirst().academicSpaceName()).isEqualTo("Armonia I");
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
    final var institution = Institution.builder().id(UUID.randomUUID()).build();
    final var person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .documentNumber("12345678")
            .email("ana@example.com")
            .build();
    final var plan = mock(StudyPlan.class);
    given(plan.getId()).willReturn(UUID.randomUUID());
    final var period = EnrollmentPeriod.builder().scopeConfigured(true).build();
    final var offering = EnrollmentPeriodOffering.create(period, plan);
    offering.selectLevels(List.of(), true);
    period.getOfferings().add(offering);
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
        .enrollmentPeriod(period)
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
