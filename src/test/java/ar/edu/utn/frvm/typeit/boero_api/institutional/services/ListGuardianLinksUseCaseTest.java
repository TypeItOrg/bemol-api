package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianLinkReviewResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListGuardianLinksUseCaseTest {

  private static final UUID INSTITUTION_ID = UUID.randomUUID();

  @Mock private PersonGuardianRepository personGuardianRepository;

  @Test
  @DisplayName("Should list the institution's requests in the given status")
  void execute_listsRequestsOfTheInstitutionByStatus() {
    final Institution institution = Institution.builder().id(INSTITUTION_ID).build();
    final PersonGuardian link =
        PersonGuardian.request(
            institution,
            Person.builder().id(UUID.randomUUID()).documentNumber("35123456").build(),
            Person.builder().id(UUID.randomUUID()).documentNumber("54123456").build(),
            GuardianRelationship.LEGAL_GUARDIAN,
            false);
    when(personGuardianRepository.findByInstitution_IdAndStatusOrderByCreatedAtAsc(
            INSTITUTION_ID, GuardianLinkStatus.PENDING))
        .thenReturn(List.of(link));

    final List<GuardianLinkReviewResponse> result =
        new ListGuardianLinksUseCase(personGuardianRepository)
            .execute(INSTITUTION_ID, GuardianLinkStatus.PENDING);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().relationship()).isEqualTo(GuardianRelationship.LEGAL_GUARDIAN);
    assertThat(result.getFirst().tutor().documentNumber()).isEqualTo("35123456");
    assertThat(result.getFirst().dependent().documentNumber()).isEqualTo("54123456");
  }
}
