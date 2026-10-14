package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.LastPersonRoleRevocationException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.PersonNotFoundInInstitutionException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokePersonRoleUseCaseTest {
  @Mock private SessionRevocationService sessionRevocationService;
  @Mock private RoleAssignmentScopeValidator roleAssignmentScopeValidator;
  @Mock private RoleAdministrationLock roleAdministrationLock;

  @Mock private InstitutionPersonResolver institutionPersonResolver;
  @Mock private RoleRepository roleRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private AuthorizationCacheInvalidator authorizationCacheInvalidator;

  @InjectMocks private RevokePersonRoleUseCase revokePersonRoleUseCase;

  @Test
  @DisplayName("Should revoke role from person")
  void execute_revokesRole() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    Role role = roleWith(SystemRoleCode.TEACHER);
    final var assignment =
        PersonRoleAssignment.builder()
            .person(person)
            .role(role)
            .institution(person.getInstitution())
            .build();
    final var remaining =
        PersonRoleAssignment.builder()
            .person(person)
            .role(roleWith(SystemRoleCode.STUDENT))
            .institution(person.getInstitution())
            .build();

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            role.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(role));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(assignment, remaining));
    when(personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId))
        .thenReturn(Optional.of(assignment));

    revokePersonRoleUseCase.execute(institutionId, personId, role.getId(), false);

    verify(roleAdministrationLock).lock(institutionId);
    verify(roleAssignmentScopeValidator).requireOperation(PermissionCode.INSTITUTION_ROLE_REVOKE);
    verify(roleAssignmentScopeValidator).requireDelegation(assignment);
    verify(personRoleAssignmentRepository).delete(assignment);
    verify(personRoleAssignmentRepository, never()).delete(remaining);
    verify(sessionRevocationService).revokeInstitutionalSessionsForPerson(personId, institutionId);
    verify(authorizationCacheInvalidator).evictPerson(personId, institutionId);
  }

  @Test
  @DisplayName("Should throw when revoking the person's only role")
  void execute_throwsWhenRevokingOnlyRole() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    Role role = roleWith(SystemRoleCode.TEACHER);
    PersonRoleAssignment assignment =
        PersonRoleAssignment.builder()
            .person(person)
            .role(role)
            .institution(person.getInstitution())
            .build();

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            role.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(role));
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(assignment));

    assertThatThrownBy(
            () -> revokePersonRoleUseCase.execute(institutionId, personId, role.getId(), false))
        .isInstanceOf(LastPersonRoleRevocationException.class);

    verify(personRoleAssignmentRepository, never()).delete(any());
    verifyNoInteractions(sessionRevocationService, authorizationCacheInvalidator);
  }

  @Test
  @DisplayName("Should allow revoking the last institutional authority")
  void execute_revokesLastInstitutionalAuthority() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    Role role = roleWith(SystemRoleCode.INSTITUTIONAL_AUTHORITY);

    when(institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId))
        .thenReturn(person);
    when(roleRepository.findByIdAndScopeAndInstitution_Id(
            role.getId(), RoleScope.INSTITUTION, institutionId))
        .thenReturn(Optional.of(role));
    Role otherRole = roleWith(SystemRoleCode.TEACHER);
    PersonRoleAssignment authorityAssignment =
        PersonRoleAssignment.builder()
            .person(person)
            .role(role)
            .institution(person.getInstitution())
            .build();
    PersonRoleAssignment otherAssignment =
        PersonRoleAssignment.builder()
            .person(person)
            .role(otherRole)
            .institution(person.getInstitution())
            .build();
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(authorityAssignment, otherAssignment));
    when(personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId))
        .thenReturn(Optional.of(authorityAssignment));

    revokePersonRoleUseCase.execute(institutionId, personId, role.getId(), true);

    verify(personRoleAssignmentRepository).delete(authorityAssignment);
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
                revokePersonRoleUseCase.execute(institutionId, personId, UUID.randomUUID(), false))
        .isInstanceOf(PersonNotFoundInInstitutionException.class);
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
}
