package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.ProvinceRepository;
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
class ListProvincesUseCaseTest {

  @Mock private ProvinceRepository provinceRepository;

  @InjectMocks private ListProvincesUseCase listProvincesUseCase;

  @Test
  @DisplayName("Should list provinces when no search provided")
  void execute_returnsProvincesWithoutSearch() {
    Province province = Province.builder().id(UUID.randomUUID()).name("Córdoba").build();
    when(provinceRepository.findAll(PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(province)));

    var response = listProvincesUseCase.execute(null, PageRequest.of(0, 20));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst())
        .satisfies(
            item -> {
              assertThat(item.id()).isEqualTo(province.getId());
              assertThat(item.name()).isEqualTo("Córdoba");
            });
  }

  @Test
  @DisplayName("Should delegate a trimmed search and preserve province details")
  void execute_delegatesTrimmedSearch() {
    Province province = Province.builder().id(UUID.randomUUID()).name("Córdoba").build();
    when(provinceRepository.searchByName("cordoba", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(province)));

    var response = listProvincesUseCase.execute("  cordoba  ", PageRequest.of(0, 20));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().name()).isEqualTo("Córdoba");
  }

  @Test
  @DisplayName("Should return empty when search does not match")
  void execute_returnsEmptyForNoMatches() {
    when(provinceRepository.searchByName("xyzabc", PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of()));

    var response = listProvincesUseCase.execute("xyzabc", PageRequest.of(0, 20));

    assertThat(response.items()).isEmpty();
  }
}
