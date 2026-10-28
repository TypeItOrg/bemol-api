package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccessScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PermissionDelegationNotAllowedException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.PersonNotFoundInInstitutionException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignPersonRoleUseCaseTest {
  @org.mockito.Spy
  private PersonRoleResponseFactory responseFactory =
      new PersonRoleResponseFactory(org.mockito.Mockito.mock(TrainingPathRepository.class));

  @Mock private RoleAssignmentScopeValidator roleAssignmentScopeValidator;
  @Mock private RoleAdministrationLock roleAdministrationLock;

  @Mock private InstitutionPersonResolver institutionPersonResolver;
  @Mock private RoleRepository roleRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  @Mock private AuditEventRecorder auditEventRecorder;

  @InjectMocks private AssignPersonRoleUseCase assignPersonRoleUseCase;

  @Test
  @DisplayName("Should assign role to person in institution")
  void execute_assignsRole() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    Role role = roleWith(SystemRoleCode.TEACHER);
    PersonRoleAssignment assignment = assignmentWith(person, role, institutionId);

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            role.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(role));
    when(personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId))
        .thenReturn(Optional.of(assignment));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(assignment));

    var response =
        assignPersonRoleUseCase.execute(
            institutionId,
            personId,
            new AssignRoleRequest(role.getId(), AccessScope.INSTITUTION, Set.of()),
            false);

    verify(assignPersonSystemRoleUseCase).execute(person, role, true);
    assertThat(response.technicalCode()).isEqualTo(SystemRoleCode.TEACHER);
    assertThat(response.displayName()).isEqualTo("Profesor");
  }

  @Test
  @DisplayName("Should throw when person does not belong to institution")
  void execute_throwsWhenPersonNotFound() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenThrow(PersonNotFoundInInstitutionException.class);

    assertThatThrownBy(
            () ->
                assignPersonRoleUseCase.execute(
                    institutionId,
                    personId,
                    new AssignRoleRequest(UUID.randomUUID(), AccessScope.INSTITUTION, Set.of()),
                    false))
        .isInstanceOf(PersonNotFoundInInstitutionException.class);
  }

  @Test
  @DisplayName("Should throw when role is not assignable")
  void execute_throwsWhenRoleNotAssignable() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    UUID roleId = UUID.randomUUID();
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            roleId, RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                assignPersonRoleUseCase.execute(
                    institutionId,
                    personId,
                    new AssignRoleRequest(roleId, AccessScope.INSTITUTION, Set.of()),
                    false))
        .isInstanceOf(RoleNotAssignableException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = SystemRoleCode.class,
      names = {"APPLICANT", "REPRESENTATIVE"})
  void execute_doesNotRequireRevocationForCompatiblePair(final SystemRoleCode assigned) {
    final UUID institutionId = UUID.randomUUID(), personId = UUID.randomUUID();
    final var person = personWith(institutionId, personId);
    final var role = roleWith(assigned);
    final var retained =
        roleWith(
            assigned == SystemRoleCode.APPLICANT
                ? SystemRoleCode.REPRESENTATIVE
                : SystemRoleCode.APPLICANT);
    final var current = assignmentWith(person, retained, institutionId);
    final var added = assignmentWith(person, role, institutionId);
    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            role.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(role));
    when(personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(added));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(current))
        .thenReturn(List.of(current, added));

    assignPersonRoleUseCase.execute(
        institutionId,
        personId,
        new AssignRoleRequest(role.getId(), AccessScope.INSTITUTION, Set.of()),
        false);

    verify(roleAssignmentScopeValidator).requireOperation(PermissionCode.INSTITUTION_ROLE_ASSIGN);
    verify(roleAssignmentScopeValidator, org.mockito.Mockito.never())
        .requireOperation(PermissionCode.INSTITUTION_ROLE_REVOKE);
    verify(roleAssignmentScopeValidator, org.mockito.Mockito.never()).requireDelegation(current);
    verify(assignPersonSystemRoleUseCase).execute(person, role, true);
  }

  @Test
  void execute_rejectsRepresentativeWithoutFreshDelegation() {
    final UUID institutionId = UUID.randomUUID(), personId = UUID.randomUUID();
    final var person = personWith(institutionId, personId);
    final var representative = roleWith(SystemRoleCode.REPRESENTATIVE);
    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            representative.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(representative));
    org.mockito.Mockito.doThrow(new PermissionDelegationNotAllowedException())
        .when(roleAssignmentScopeValidator)
        .requireDelegation(representative, AccessScope.INSTITUTION, Set.of());

    assertThatThrownBy(
            () ->
                assignPersonRoleUseCase.execute(
                    institutionId,
                    personId,
                    new AssignRoleRequest(
                        representative.getId(), AccessScope.INSTITUTION, Set.of()),
                    false))
        .isInstanceOf(PermissionDelegationNotAllowedException.class);
    verify(assignPersonSystemRoleUseCase, org.mockito.Mockito.never())
        .execute(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(Role.class),
            org.mockito.ArgumentMatchers.anyBoolean());
  }

  private static Person personWith(UUID institutionId, UUID personId) {
    return Person.builder()
        .id(personId)
        .institution(Institution.builder().id(institutionId).build())
        .build();
  }

  private static Role roleWith(SystemRoleCode roleCode) {
    return Role.builder()
        .id(UUID.randomUUID())
        .code(roleCode.name())
        .name(roleCode.getDisplayName())
        .scope(RoleScope.INSTITUTION)
        .system(true)
        .build();
  }

  private static PersonRoleAssignment assignmentWith(Person person, Role role, UUID institutionId) {
    return PersonRoleAssignment.builder()
        .person(person)
        .role(role)
        .institution(person.getInstitution())
        .build();
  }
}
