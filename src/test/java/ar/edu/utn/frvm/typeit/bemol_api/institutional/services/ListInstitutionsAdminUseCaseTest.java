package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.InstitutionUserCount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
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

@ExtendWith(MockitoExtension.class)
class ListInstitutionsAdminUseCaseTest {

  @Mock private InstitutionRepository institutionRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private ListInstitutionsAdminUseCase listInstitutionsAdminUseCase;

  @Test
  @DisplayName("Should list institutions with enabled user counts, defaulting to zero for missing")
  void execute_returnsInstitutionsWithUserCounts() {
    Institution withUsers = activeInstitution();
    Institution withNoUsers = activeInstitution();
    when(institutionRepository.findWithLocationByFilters(isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(withUsers, withNoUsers)));
    when(userRepository.countEnabledUsersByInstitutionIdIn(anyCollection()))
        .thenReturn(List.of(countFor(withUsers.getId(), 5L)));

    var response = listInstitutionsAdminUseCase.execute(null, null, PageRequest.of(0, 20));

    assertThat(response.items()).hasSize(2);
    assertThat(response.items().get(0).id()).isEqualTo(withUsers.getId());
    assertThat(response.items().get(0).userCount()).isEqualTo(5L);
    assertThat(response.items().get(1).id()).isEqualTo(withNoUsers.getId());
    assertThat(response.items().get(1).userCount()).isZero();
  }

  @Test
  @DisplayName("Should not call count repository when page is empty")
  void execute_skipsCountQueryWhenPageEmpty() {
    when(institutionRepository.findWithLocationByFilters(eq("Boero"), eq(true), any()))
        .thenReturn(new PageImpl<>(List.of()));

    var response = listInstitutionsAdminUseCase.execute("  Boero  ", true, PageRequest.of(0, 20));

    assertThat(response.items()).isEmpty();
    assertThat(response.totalItems()).isZero();
  }

  private static Institution activeInstitution() {
    Country country = Country.builder().name("Argentina").isoCode("ARG").build();
    Province province = Province.builder().country(country).name("Cordoba").build();
    City city = City.builder().name("Villa Maria").province(province).build();
    return Institution.builder()
        .id(UUID.randomUUID())
        .name("Conservatorio Boero")
        .slug("boero-villa-maria")
        .city(city)
        .active(true)
        .build();
  }

  private static InstitutionUserCount countFor(UUID id, long count) {
    return new InstitutionUserCount() {
      @Override
      public UUID getInstitutionId() {
        return id;
      }

      @Override
      public long getUserCount() {
        return count;
      }
    };
  }
}
