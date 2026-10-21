package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPersonRolesUseCaseTest {

  @org.mockito.Spy
  private PersonRoleResponseFactory responseFactory =
      new PersonRoleResponseFactory(org.mockito.Mockito.mock(TrainingPathRepository.class));

  @Mock private InstitutionPersonResolver institutionPersonResolver;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @InjectMocks private ListPersonRolesUseCase listPersonRolesUseCase;

  @Test
  @DisplayName("Should list roles for person within institution")
  void execute_returnsPersonRoles() {
    UUID institutionId = UUID.randomUUID();
    UUID personId = UUID.randomUUID();
    Person person = personWith(institutionId, personId);
    PersonRoleAssignment assignment = assignmentWith(person, SystemRoleCode.APPLICANT);

    when(institutionPersonResolver.requirePersonInInstitution(institutionId, personId))
        .thenReturn(person);
    when(personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId))
        .thenReturn(List.of(assignment));

    var response = listPersonRolesUseCase.execute(institutionId, personId);

    verify(institutionPersonResolver).requirePersonInInstitution(institutionId, personId);
    assertThat(response).hasSize(1);
    assertThat(response.getFirst().technicalCode()).isEqualTo(SystemRoleCode.APPLICANT);
    assertThat(response.getFirst().displayName()).isEqualTo("Postulante");
  }

  private static Person personWith(UUID institutionId, UUID personId) {
    return Person.builder()
        .id(personId)
        .institution(Institution.builder().id(institutionId).build())
        .build();
  }

  private static PersonRoleAssignment assignmentWith(Person person, SystemRoleCode roleCode) {
    Role role =
        Role.builder()
            .code(roleCode.name())
            .name(roleCode.getDisplayName())
            .scope(RoleScope.INSTITUTION)
            .system(true)
            .build();
    PersonRoleAssignment assignment =
        PersonRoleAssignment.builder()
            .person(person)
            .role(role)
            .institution(person.getInstitution())
            .build();
    return assignment;
  }
}
