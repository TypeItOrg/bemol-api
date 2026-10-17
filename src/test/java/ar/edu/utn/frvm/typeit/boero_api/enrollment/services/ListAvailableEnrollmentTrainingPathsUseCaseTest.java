package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ListAvailableEnrollmentTrainingPathsUseCaseTest {

  @Mock private PersonRepository personRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private TrainingPathRepository trainingPathRepository;

  @Test
  @DisplayName("Should list only training paths available to an applicant")
  void listsAvailableTrainingPaths() {
    final Institution institution =
        Institution.builder().id(UUID.randomUUID()).name("Conservatorio").build();
    final Person person =
        Person.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .firstName("Ana")
            .lastName("Garcia")
            .documentNumber("12345678")
            .email("ana@example.com")
            .build();
    final JwtAuthenticatedUser principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(person.getId())
            .documentNumber(person.getDocumentNumber())
            .institutionId(institution.getId())
            .sessionId(UUID.randomUUID())
            .tokenId("token-id")
            .build();
    final TrainingPath trainingPath = TrainingPath.create(institution, "Guitarra", null);
    final var pageable = PageRequest.of(0, 20);
    final var useCase =
        new ListAvailableEnrollmentTrainingPathsUseCase(
            Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC),
            new ApplicantEnrollmentGuard(
                personRepository,
                org.mockito.Mockito.mock(
                    ar.edu.utn.frvm.typeit.boero_api.institutional.services.OperatingContextResolver
                        .class),
                personRoleAssignmentRepository),
            trainingPathRepository);

    given(
            personRoleAssignmentRepository.existsByPerson_IdAndInstitution_IdAndRole_Code(
                principal.personId(), principal.institutionId(), "APPLICANT"))
        .willReturn(true);
    given(
            personRepository.findByIdAndInstitution_Id(
                principal.personId(), principal.institutionId()))
        .willReturn(Optional.of(person));
    given(
            trainingPathRepository.findOfferedForEnrollment(
                principal.institutionId(), Instant.parse("2026-09-26T12:00:00Z"), null, pageable))
        .willReturn(new PageImpl<>(java.util.List.of(trainingPath), pageable, 1));

    final var response = useCase.execute(principal, null, pageable);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().name()).isEqualTo("Guitarra");
  }
}
