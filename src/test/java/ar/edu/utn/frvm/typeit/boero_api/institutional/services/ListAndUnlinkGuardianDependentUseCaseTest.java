package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.DependentApplicationCount;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
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
class ListAndUnlinkGuardianDependentUseCaseTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();
  private static final UUID TUTOR_ID = UUID.randomUUID();

  @Mock private PersonGuardianRepository personGuardianRepository;

  @Test
  @DisplayName("Should list dependents with their active applications count")
  void list_returnsDependentsWithCounts() {
    final PersonGuardian withApplication = link("Mateo", "54123456");
    final PersonGuardian withoutApplication = link("Lucia", "54123457");
    when(personGuardianRepository.findByInstitution_IdAndTutorPerson_IdOrderByCreatedAtAsc(
            INSTITUTION_ID, TUTOR_ID))
        .thenReturn(List.of(withApplication, withoutApplication));
    when(personGuardianRepository.countActiveApplicationsByApplicant(
            INSTITUTION_ID,
            List.of(
                withApplication.getDependentPerson().getId(),
                withoutApplication.getDependentPerson().getId())))
        .thenReturn(
            List.of(
                new DependentApplicationCount(withApplication.getDependentPerson().getId(), 2)));

    final List<GuardianDependentResponse> result =
        new ListGuardianDependentsUseCase(personGuardianRepository)
            .execute(INSTITUTION_ID, TUTOR_ID);

    assertThat(result)
        .extracting(
            GuardianDependentResponse::firstName,
            GuardianDependentResponse::activeApplicationsCount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Mateo", 2L),
            org.assertj.core.groups.Tuple.tuple("Lucia", 0L));
  }

  @Test
  @DisplayName("Should return an empty list without counting when there are no dependents")
  void list_returnsEmptyWithoutCounting() {
    when(personGuardianRepository.findByInstitution_IdAndTutorPerson_IdOrderByCreatedAtAsc(
            INSTITUTION_ID, TUTOR_ID))
        .thenReturn(List.of());

    assertThat(
            new ListGuardianDependentsUseCase(personGuardianRepository)
                .execute(INSTITUTION_ID, TUTOR_ID))
        .isEmpty();
    verify(personGuardianRepository, never())
        .countActiveApplicationsByApplicant(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyCollection());
  }

  @Test
  @DisplayName("Should remove only the guardianship link")
  void unlink_deletesTheLink() {
    final PersonGuardian link = link("Mateo", "54123456");
    final UUID dependentId = link.getDependentPerson().getId();
    when(personGuardianRepository.findByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
            INSTITUTION_ID, TUTOR_ID, dependentId))
        .thenReturn(Optional.of(link));

    new UnlinkGuardianDependentUseCase(personGuardianRepository)
        .execute(INSTITUTION_ID, TUTOR_ID, dependentId);

    verify(personGuardianRepository).delete(link);
  }

  @Test
  @DisplayName("Should fail when the tutor is not linked to that dependent")
  void unlink_failsWhenNotLinked() {
    final UUID dependentId = UUID.randomUUID();
    when(personGuardianRepository.findByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
            INSTITUTION_ID, TUTOR_ID, dependentId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new UnlinkGuardianDependentUseCase(personGuardianRepository)
                    .execute(INSTITUTION_ID, TUTOR_ID, dependentId))
        .isInstanceOfSatisfying(
            DependentNotFoundException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPENDENT_NOT_FOUND"));
  }

  private PersonGuardian link(final String firstName, final String documentNumber) {
    final Institution institution = Institution.builder().id(INSTITUTION_ID).build();
    final Person dependent =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName(firstName)
            .lastName("Gonzalez")
            .documentNumber(documentNumber)
            .birthDate(LocalDate.of(2018, 9, 10))
            .build();

    return PersonGuardian.builder()
        .id(UUID.randomUUID())
        .institution(institution)
        .tutorPerson(Person.builder().id(TUTOR_ID).institution(institution).build())
        .dependentPerson(dependent)
        .relationship(GuardianRelationship.FATHER)
        .primaryContact(true)
        .build();
  }
}
