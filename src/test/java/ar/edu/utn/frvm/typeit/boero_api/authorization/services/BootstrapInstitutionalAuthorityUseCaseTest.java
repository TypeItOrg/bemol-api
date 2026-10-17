package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BootstrapInstitutionalAuthorityUseCaseTest {

  @Mock private InstitutionPersonResolver institutionPersonResolver;
  @Mock private InstitutionalSystemRoleResolver institutionalSystemRoleResolver;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  @Mock private AuditEventRecorder auditEventRecorder;

  @InjectMocks
  private BootstrapInstitutionalAuthorityUseCase bootstrapInstitutionalAuthorityUseCase;

  @Test
  @DisplayName("Should bootstrap institutional authority for person in institution")
  void execute_assignsInstitutionalAuthority() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    Role role = roleWith(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    PersonRoleAssignment assignment = assignmentWith(person, role);

    when(institutionPersonResolver.requirePersonInInstitution(institutionId, personId))
        .thenReturn(person);
    when(institutionalSystemRoleResolver.requireInstitutionalSystemRole(
            institutionId, SystemRoleCode.INSTITUTIONAL_AUTHORITY))
        .thenReturn(role);
    when(personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId))
        .thenReturn(Optional.of(assignment));

    var response = bootstrapInstitutionalAuthorityUseCase.execute(institutionId, personId);

    verify(assignPersonSystemRoleUseCase).execute(person, SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    assertThat(response.technicalCode()).isEqualTo(SystemRoleCode.INSTITUTIONAL_AUTHORITY);
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

  private static PersonRoleAssignment assignmentWith(Person person, Role role) {
    return PersonRoleAssignment.builder()
        .person(person)
        .role(role)
        .institution(person.getInstitution())
        .build();
  }
}
