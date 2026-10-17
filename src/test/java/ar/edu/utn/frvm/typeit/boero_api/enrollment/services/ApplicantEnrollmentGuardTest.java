package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.services.OperatingContextResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ApplicantEnrollmentGuardTest {

  @Mock private PersonRepository personRepository;
  @Mock private OperatingContextResolver operatingContext;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;

  private ApplicantEnrollmentGuard guard;
  private JwtAuthenticatedUser principal;

  @BeforeEach
  void setUp() {
    guard =
        new ApplicantEnrollmentGuard(
            personRepository, operatingContext, personRoleAssignmentRepository);
    principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(UUID.randomUUID())
            .documentNumber("12345678")
            .institutionId(UUID.randomUUID())
            .build();
  }

  @Test
  @DisplayName("Should accept a person with the APPLICANT role")
  void requireApplicant_acceptsApplicant() {
    final Person person = Person.builder().build();
    stubRole("APPLICANT", true);
    stubPerson(person);

    assertThat(guard.requireApplicant(principal)).isSameAs(person);
  }

  @Test
  @DisplayName("Should accept a person with the STUDENT role")
  void requireApplicant_acceptsStudent() {
    final Person person = Person.builder().build();
    stubRole("APPLICANT", false);
    stubRole("STUDENT", true);
    stubPerson(person);

    assertThat(guard.requireApplicant(principal)).isSameAs(person);
  }

  @Test
  @DisplayName("Should resolve the operating subject once for role checks and person lookup")
  void requireApplicant_resolvesSelectedPersonOnce() {
    final UUID selectedPersonId = UUID.randomUUID();
    final Person person = Person.builder().id(selectedPersonId).build();
    when(operatingContext.currentPersonId()).thenReturn(Optional.of(selectedPersonId));
    when(personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
            selectedPersonId, principal.institutionId(), "APPLICANT"))
        .thenReturn(false);
    when(personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
            selectedPersonId, principal.institutionId(), "STUDENT"))
        .thenReturn(true);
    when(personRepository.findByIdAndInstitution_Id(selectedPersonId, principal.institutionId()))
        .thenReturn(Optional.of(person));

    assertThat(guard.requireApplicant(principal)).isSameAs(person);
    verify(operatingContext).currentPersonId();
  }

  @Test
  @DisplayName("Should reject a person that is neither applicant nor student")
  void requireApplicant_rejectsOthers() {
    stubRole("APPLICANT", false);
    stubRole("STUDENT", false);

    assertThatThrownBy(() -> guard.requireApplicant(principal))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void stubRole(final String code, final boolean present) {
    when(personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
            principal.personId(), principal.institutionId(), code))
        .thenReturn(present);
  }

  private void stubPerson(final Person person) {
    when(personRepository.findByIdAndInstitution_Id(
            principal.personId(), principal.institutionId()))
        .thenReturn(Optional.of(person));
  }
}
