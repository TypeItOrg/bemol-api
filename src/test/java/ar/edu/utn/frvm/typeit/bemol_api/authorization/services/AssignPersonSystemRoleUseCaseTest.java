package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        .thenReturn(List.of());

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.TEACHER);

    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Should replace existing roles when assigning applicant")
  void execute_replacesExistingRolesWithApplicant() {
    Role applicant = roleWith(SystemRoleCode.APPLICANT);
    Role administrative = roleWith(SystemRoleCode.ADMINISTRATIVE);
    PersonRoleAssignment administrativeAssignment = assignmentWith(administrative);
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name(), institutionId))
        .thenReturn(Optional.of(applicant));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(administrativeAssignment));

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT);

    verify(personRoleAssignmentRepository).delete(administrativeAssignment);
    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Should keep existing roles without adding the exclusive applicant role")
  void executePreservingRoles_doesNotAddApplicantToExistingRoles() {
    Role applicant = roleWith(SystemRoleCode.APPLICANT);
    Role guardian = roleWith(SystemRoleCode.STUDENT);
    PersonRoleAssignment guardianAssignment = assignmentWith(guardian);
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name(), institutionId))
        .thenReturn(Optional.of(applicant));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(guardianAssignment));

    assignPersonSystemRoleUseCase.executePreservingRoles(person, SystemRoleCode.APPLICANT);

    verify(personRoleAssignmentRepository, org.mockito.Mockito.never()).delete(guardianAssignment);
    verify(personRoleAssignmentRepository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
    final var order =
        org.mockito.Mockito.inOrder(institutionRepository, personRoleAssignmentRepository);
    order.verify(institutionRepository).findByIdForUpdate(institutionId);
    order
        .verify(personRoleAssignmentRepository)
        .findByPerson_IdAndInstitution_Id(person.getId(), institutionId);
  }

  @Test
  @DisplayName("Should allow replacing the last institutional authority with applicant")
  void execute_replacesLastAuthorityWithApplicant() {
    Role applicant = roleWith(SystemRoleCode.APPLICANT);
    Role authority = roleWith(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    PersonRoleAssignment authorityAssignment = assignmentWith(authority);
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name(), institutionId))
        .thenReturn(Optional.of(applicant));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(authorityAssignment));
    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT);

    verify(personRoleAssignmentRepository).delete(authorityAssignment);
    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
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

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"APPLICANT", "REPRESENTATIVE"})
  void execute_keepsApplicantAndRepresentativeInEitherOrder(final SystemRoleCode assigned) {
    final var role = roleWith(assigned);
    final var retained =
        assignmentWith(
            roleWith(
                assigned == SystemRoleCode.APPLICANT
                    ? SystemRoleCode.REPRESENTATIVE
                    : SystemRoleCode.APPLICANT));
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, assigned.name(), institutionId))
        .thenReturn(Optional.of(role));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(retained));

    assignPersonSystemRoleUseCase.execute(person, assigned, true);

    verify(personRoleAssignmentRepository, org.mockito.Mockito.never())
        .delete(org.mockito.ArgumentMatchers.any());
    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
    verify(sessionRevocationService)
        .revokeInstitutionalSessionsForPerson(person.getId(), institutionId);
    verify(authorizationCacheInvalidator).evictPerson(person.getId(), institutionId);
  }

  @Test
  void execute_promotesApplicantToStudentWithoutRemovingRepresentative() {
    final var student = roleWith(SystemRoleCode.STUDENT);
    final var applicant = assignmentWith(roleWith(SystemRoleCode.APPLICANT));
    final var representative = assignmentWith(roleWith(SystemRoleCode.REPRESENTATIVE));
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.STUDENT.name(), institutionId))
        .thenReturn(Optional.of(student));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(applicant, representative));

    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.STUDENT, true);

    verify(personRoleAssignmentRepository).delete(applicant);
    verify(personRoleAssignmentRepository, org.mockito.Mockito.never()).delete(representative);
    verify(personRoleAssignmentRepository).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void executePreservingRoles_doesNotAddApplicantToRepresentative() {
    final var applicant = roleWith(SystemRoleCode.APPLICANT);
    final var representative = assignmentWith(roleWith(SystemRoleCode.REPRESENTATIVE));
    when(roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.APPLICANT.name(), institutionId))
        .thenReturn(Optional.of(applicant));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institutionId))
        .thenReturn(List.of(representative));

    assignPersonSystemRoleUseCase.executePreservingRoles(person, SystemRoleCode.APPLICANT);

    verify(personRoleAssignmentRepository, org.mockito.Mockito.never())
        .delete(org.mockito.ArgumentMatchers.any());
    verify(personRoleAssignmentRepository, org.mockito.Mockito.never())
        .save(org.mockito.ArgumentMatchers.any());
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
