package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
class ListInstitutionsUseCaseTest {

  @Mock private InstitutionRepository institutionRepository;

  @InjectMocks private ListInstitutionsUseCase listInstitutionsUseCase;

  @Test
  @DisplayName("Should list active and inactive institutions with location and status")
  void execute_returnsFilteredInstitutionsWithLocationAndStatus() {
    Institution institution = activeInstitution();
    when(institutionRepository.findWithLocationByFilters("boero", true, PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(institution)));

    var response = listInstitutionsUseCase.execute(" boero ", true, PageRequest.of(0, 20));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst())
        .satisfies(
            item -> {
              assertThat(item.id()).isEqualTo(institution.getId());
              assertThat(item.name()).isEqualTo("Conservatorio Boero");
              assertThat(item.slug()).isEqualTo("boero-villa-maria");
              assertThat(item.country().name()).isEqualTo("Argentina");
              assertThat(item.country().isoCode()).isEqualTo("ARG");
              assertThat(item.city()).isEqualTo("Villa Maria");
              assertThat(item.province()).isEqualTo("Cordoba");
              assertThat(item.active()).isTrue();
            });
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
        .phoneNumber("0353-123456")
        .email("info@boero.edu.ar")
        .active(true)
        .build();
  }
}
