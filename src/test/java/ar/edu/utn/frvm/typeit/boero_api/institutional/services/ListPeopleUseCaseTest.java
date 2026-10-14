package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.PersonSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListPeopleUseCaseTest {
  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ANA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID LUIS_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final PageRequest PAGE = PageRequest.of(1, 20, Sort.by("lastName"));
  private final Institution institution = Institution.builder().id(INSTITUTION_ID).build();
  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository assignments;
  @Mock private UserRepository users;
  @InjectMocks private ListPeopleUseCase useCase;

  @Test
  void mapsRolesAndAccessByPersonWithoutMixingPeopleAndPreservesPagination() {
    final var ana = person(ANA_ID, "Ana", "11111111");
    final var luis = person(LUIS_ID, "Luis", "22222222");
    final var teacher =
        Role.builder().id(ROLE_ID).institution(institution).code("TEACHER").name("Docente").build();
    final var activeUser =
        User.builder().institution(institution).person(ana).password("hash").build();
    final var disabledUser =
        User.builder().institution(institution).person(luis).password("hash").build();
    disabledUser.updateAccess(false);
    when(personRepository.findByInstitution_IdAndDeletedFalse(INSTITUTION_ID, PAGE))
        .thenReturn(new PageImpl<>(List.of(ana, luis), PAGE, 22));
    when(assignments.findByPerson_IdInAndInstitution_Id(List.of(ANA_ID, LUIS_ID), INSTITUTION_ID))
        .thenReturn(List.of(PersonRoleAssignment.assign(ana, teacher, institution)));
    when(users.findByPerson_IdInAndInstitution_Id(List.of(ANA_ID, LUIS_ID), INSTITUTION_ID))
        .thenReturn(List.of(disabledUser, activeUser));

    assertThat(useCase.execute(INSTITUTION_ID, "   ", null, PAGE))
        .isEqualTo(
            new PaginatedResponse<>(
                List.of(
                    new PersonSummaryResponse(
                        ANA_ID,
                        "Ana",
                        "García",
                        "11111111",
                        "ana@example.com",
                        "353-123",
                        true,
                        List.of(
                            new PersonSummaryResponse.PersonRoleSummaryResponse(
                                "TEACHER", "Docente"))),
                    new PersonSummaryResponse(
                        LUIS_ID,
                        "Luis",
                        "García",
                        "22222222",
                        "luis@example.com",
                        "353-123",
                        false,
                        List.of())),
                1,
                20,
                22,
                2));
  }

  @Test
  void usesTheTrimmedSearchWithoutRequiringARoleFilter() {
    stubFilteredPage("Ana", null);

    assertThat(useCase.execute(INSTITUTION_ID, "  Ana  ", null, PAGE).items())
        .extracting(person -> person.id())
        .containsExactly(ANA_ID);
  }

  @Test
  void usesTheRoleFilterWithoutRequiringASearch() {
    stubFilteredPage(null, ROLE_ID);

    assertThat(useCase.execute(INSTITUTION_ID, null, ROLE_ID, PAGE).items())
        .extracting(person -> person.id())
        .containsExactly(ANA_ID);
  }

  @Test
  void preservesAnEmptyPageBeyondTheLastPageAndSkipsRelatedQueries() {
    final var outOfRange = PageRequest.of(2, 20);
    when(personRepository.search(INSTITUTION_ID, "nope", null, outOfRange))
        .thenReturn(new PageImpl<>(List.of(), outOfRange, 35));

    assertThat(useCase.execute(INSTITUTION_ID, "nope", null, outOfRange))
        .isEqualTo(new PaginatedResponse<>(List.of(), 2, 20, 35, 2));
    verifyNoInteractions(assignments, users);
  }

  private void stubFilteredPage(@Nullable String search, @Nullable UUID role) {
    when(personRepository.search(INSTITUTION_ID, search, role, PAGE))
        .thenReturn(new PageImpl<>(List.of(person(ANA_ID, "Ana", "11111111")), PAGE, 21));
    when(assignments.findByPerson_IdInAndInstitution_Id(List.of(ANA_ID), INSTITUTION_ID))
        .thenReturn(List.of());
    when(users.findByPerson_IdInAndInstitution_Id(List.of(ANA_ID), INSTITUTION_ID))
        .thenReturn(List.of());
  }

  private Person person(UUID id, String firstName, String document) {
    return Person.builder()
        .id(id)
        .institution(institution)
        .firstName(firstName)
        .lastName("García")
        .documentNumber(document)
        .email(firstName.toLowerCase(java.util.Locale.ROOT) + "@example.com")
        .phoneNumber("353-123")
        .build();
  }
}
