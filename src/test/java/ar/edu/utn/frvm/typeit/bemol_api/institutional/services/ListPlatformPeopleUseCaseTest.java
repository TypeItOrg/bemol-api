package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListPlatformPeopleUseCaseTest {

  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @InjectMocks private ListPlatformPeopleUseCase listPlatformPeopleUseCase;

  @Test
  @DisplayName("Should list platform people with institution and roles")
  void execute_listsPeopleWithInstitutionAndRoles() {
    UUID institutionId = UUID.randomUUID();
    Institution institution =
        Institution.builder().id(institutionId).name("Instituto Boero").build();
    Person person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("García")
            .documentNumber("12345678")
            .build();
    Role role =
        Role.builder()
            .code(SystemRoleCode.TEACHER.name())
            .name(SystemRoleCode.TEACHER.getDisplayName())
            .build();
    PersonRoleAssignment assignment =
        PersonRoleAssignment.builder().person(person).institution(institution).role(role).build();
    Pageable pageable = PageRequest.of(0, 10, Sort.by("institutionName"));
    Pageable repositoryPageable = PageRequest.of(0, 10, Sort.by("institution.name"));
    when(personRepository.findPlatformPeople(
            "ana", institutionId, SystemRoleCode.TEACHER.name(), repositoryPageable))
        .thenReturn(new PageImpl<>(List.of(person), repositoryPageable, 1));
    when(personRoleAssignmentRepository.findByPerson_IdIn(List.of(person.getId())))
        .thenReturn(List.of(assignment));

    var response =
        listPlatformPeopleUseCase.execute(
            "  ana  ", institutionId, SystemRoleCode.TEACHER, pageable);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().institutionId()).isEqualTo(institutionId);
    assertThat(response.items().getFirst().institutionName()).isEqualTo("Instituto Boero");
    assertThat(response.items().getFirst().roles())
        .extracting(roleResponse -> roleResponse.roleCode())
        .containsExactly(SystemRoleCode.TEACHER.name());
  }

  @Test
  @DisplayName("Should normalize empty filters and preserve regular sort fields")
  void execute_normalizesEmptyFiltersAndPreservesSort() {
    Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "lastName"));
    when(personRepository.findPlatformPeople(null, null, null, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    var response = listPlatformPeopleUseCase.execute("   ", null, null, pageable);

    assertThat(response.items()).isEmpty();
    verify(personRepository).findPlatformPeople(null, null, null, pageable);
    verify(personRoleAssignmentRepository, org.mockito.Mockito.never())
        .findByPerson_IdIn(org.mockito.ArgumentMatchers.anyList());
  }
}
