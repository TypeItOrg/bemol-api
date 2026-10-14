package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.ProvinceNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.ProvinceRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CityListItemResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ListCitiesUseCaseTest {

  private static final UUID PROVINCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final PageRequest PAGE = PageRequest.of(1, 20);

  @Mock private CityRepository cityRepository;
  @Mock private ProvinceRepository provinceRepository;
  @InjectMocks private ListCitiesUseCase listCitiesUseCase;

  @Test
  void execute_mapsCitiesAndPreservesPaginationWithoutSearch() {
    when(cityRepository.findAll(PAGE)).thenReturn(new PageImpl<>(List.of(city()), PAGE, 21));

    assertThat(listCitiesUseCase.execute(null, PAGE)).isEqualTo(expectedPage());
  }

  @Test
  void execute_delegatesTrimmedSearchAndMapsProvinceDetails() {
    when(cityRepository.searchByNameOrProvince("María", PAGE))
        .thenReturn(new PageImpl<>(List.of(city()), PAGE, 21));

    assertThat(listCitiesUseCase.execute("  María  ", PAGE)).isEqualTo(expectedPage());
    verify(cityRepository).searchByNameOrProvince("María", PAGE);
  }

  @Test
  void execute_preservesEmptySearchResultMetadata() {
    final PageRequest firstPage = PageRequest.of(0, 20);
    when(cityRepository.searchByNameOrProvince("xyzabc", firstPage))
        .thenReturn(new PageImpl<>(List.of(), firstPage, 0));

    assertThat(listCitiesUseCase.execute("xyzabc", firstPage))
        .isEqualTo(new PaginatedResponse<>(List.of(), 0, 20, 0, 0));
  }

  @Test
  void executeByProvince_keepsTheProvinceFilterWithoutSearch() {
    when(provinceRepository.existsById(PROVINCE_ID)).thenReturn(true);
    when(cityRepository.findByProvinceId(PROVINCE_ID, PAGE))
        .thenReturn(new PageImpl<>(List.of(city()), PAGE, 21));

    assertThat(listCitiesUseCase.executeByProvince(PROVINCE_ID, null, PAGE))
        .isEqualTo(expectedPage());
    verify(cityRepository).findByProvinceId(PROVINCE_ID, PAGE);
  }

  @Test
  void executeByProvince_keepsTheProvinceFilterWithTrimmedSearch() {
    when(provinceRepository.existsById(PROVINCE_ID)).thenReturn(true);
    when(cityRepository.searchByProvinceAndName(PROVINCE_ID, "María", PAGE))
        .thenReturn(new PageImpl<>(List.of(city()), PAGE, 21));

    assertThat(listCitiesUseCase.executeByProvince(PROVINCE_ID, "  María  ", PAGE))
        .isEqualTo(expectedPage());
    verify(cityRepository).searchByProvinceAndName(PROVINCE_ID, "María", PAGE);
  }

  @Test
  void executeByProvince_rejectsAnUnknownProvinceBeforeLoadingCities() {
    when(provinceRepository.existsById(PROVINCE_ID)).thenReturn(false);

    assertThatThrownBy(() -> listCitiesUseCase.executeByProvince(PROVINCE_ID, null, PAGE))
        .isInstanceOf(ProvinceNotFoundException.class);
    verifyNoInteractions(cityRepository);
  }

  private static City city() {
    return City.builder()
        .id(CITY_ID)
        .name("Villa María")
        .province(Province.builder().id(PROVINCE_ID).name("Córdoba").build())
        .build();
  }

  private static PaginatedResponse<CityListItemResponse> expectedPage() {
    return new PaginatedResponse<>(
        List.of(new CityListItemResponse(CITY_ID, "Villa María", PROVINCE_ID, "Córdoba")),
        1,
        20,
        21,
        2);
  }
}
