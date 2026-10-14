package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignPersonSystemRoleUseCaseTest {

  @Mock private RoleRepository roleRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private SessionRevocationService sessionRevocationService;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private AuthorizationCacheInvalidator authorizationCacheInvalidator;

  @InjectMocks private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private final UUID institutionId = UUID.randomUUID();
  private final Institution institution = Institution.builder().id(institutionId).build();
  private final Person person =
      Person.builder().id(UUID.randomUUID()).institution(institution).build();

  @BeforeEach
  void setUp() {
    when(institutionRepository.findByIdForUpdate(institutionId))
        .thenReturn(Optional.of(institution));
  }

  @Test
  @DisplayName("Should assign role when current roles are compatible")
  void execute_assignsCompatibleRole() {
    Role teacher = roleWith(SystemRoleCode.TEACHER);
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.TEACHER.name(), institutionId))
        .thenReturn(Optional.of(teacher));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(assignmentWith(roleWith(SystemRoleCode.ADMINISTRATIVE))));

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.TEACHER);

    assertSavedAssignment(teacher);
    verify(personRoleAssignmentRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Applicant replacement removes all other roles, including the last authority")
  void execute_replacesAllOtherRolesWithApplicant() {
    Role applicant = roleWith(SystemRoleCode.APPLICANT);
    Role administrative = roleWith(SystemRoleCode.ADMINISTRATIVE);
    PersonRoleAssignment administrativeAssignment = assignmentWith(administrative);
    PersonRoleAssignment authorityAssignment =
        assignmentWith(roleWith(SystemRoleCode.INSTITUTIONAL_AUTHORITY));
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name(), institutionId))
        .thenReturn(Optional.of(applicant));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(administrativeAssignment, authorityAssignment));

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT);

    verify(personRoleAssignmentRepository).delete(administrativeAssignment);
    verify(personRoleAssignmentRepository).delete(authorityAssignment);
    assertSavedAssignment(applicant);
  }

  @Test
  @DisplayName("Should replace applicant when assigning another role")
  void execute_replacesApplicantWithNewRole() {
    Role applicant = roleWith(SystemRoleCode.APPLICANT);
    Role administrative = roleWith(SystemRoleCode.ADMINISTRATIVE);
    PersonRoleAssignment applicantAssignment = assignmentWith(applicant);
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.ADMINISTRATIVE.name(), institutionId))
        .thenReturn(Optional.of(administrative));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(applicantAssignment));

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.ADMINISTRATIVE);

    verify(personRoleAssignmentRepository).delete(applicantAssignment);
    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
  }

  private void assertSavedAssignment(Role expectedRole) {
    ArgumentCaptor<PersonRoleAssignment> saved =
        ArgumentCaptor.forClass(PersonRoleAssignment.class);
    verify(personRoleAssignmentRepository).save(saved.capture());
    assertThat(saved.getValue().getPerson()).isSameAs(person);
    assertThat(saved.getValue().getInstitution()).isSameAs(institution);
    assertThat(saved.getValue().getRole()).isSameAs(expectedRole);
    assertThat(saved.getValue().getAccessScope()).isEqualTo(AccessScope.INSTITUTION);
    assertThat(saved.getValue().getTrainingPathIds()).isEmpty();
    verify(authorizationCacheInvalidator).evictPerson(person.getId(), institutionId);
    verifyNoInteractions(sessionRevocationService);
  }

  private Role roleWith(SystemRoleCode roleCode) {
    return Role.builder()
        .id(UUID.randomUUID())
        .code(roleCode.name())
        .name(roleCode.getDisplayName())
        .scope(RoleScope.INSTITUTION)
        .system(true)
        .institution(institution)
        .build();
  }

  private PersonRoleAssignment assignmentWith(Role role) {
    return PersonRoleAssignment.builder()
        .person(person)
        .role(role)
        .institution(institution)
        .build();
  }
}
