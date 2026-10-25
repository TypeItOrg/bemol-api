package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.UpdateInstitutionRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateInstitutionUseCaseTest {

  @Mock private AuditEventRecorder auditEventRecorder;
  @Mock private CityRepository cityRepository;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private SessionRevocationService sessionRevocationService;

  @InjectMocks private UpdateInstitutionUseCase updateInstitutionUseCase;

  @Test
  @DisplayName("Should update institution")
  void execute_updatesInstitution() {
    UUID institutionId = UUID.randomUUID();
    UUID cityId = UUID.randomUUID();
    Institution institution = institutionWith(institutionId);
    City city = cityWith(cityId);
    UpdateInstitutionRequest request =
        new UpdateInstitutionRequest(
            "Boero Actualizado",
            null,
            cityId,
            "Belgrano",
            "456",
            "Norte",
            "Piso 2",
            "0353-999999",
            "nuevo@boero.edu.ar",
            false);

    when(institutionRepository.findWithLocationById(institutionId))
        .thenReturn(Optional.of(institution));
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));

    var response = updateInstitutionUseCase.execute(institutionId, request);

    verify(institutionRepository).save(institution);
    assertThat(response.name()).isEqualTo("Boero Actualizado");
    assertThat(response.id()).isEqualTo(institutionId);
    assertThat(response.active()).isFalse();
    assertThat(response.city().name()).isEqualTo("Villa Maria");
    assertThat(response.country().isoCode()).isEqualTo("ARG");
  }

  @Test
  @DisplayName("Should throw when institution does not exist")
  void execute_throwsWhenInstitutionNotFound() {
    UUID institutionId = UUID.randomUUID();
    UpdateInstitutionRequest request = updateRequest(UUID.randomUUID(), true);

    when(institutionRepository.findWithLocationById(institutionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> updateInstitutionUseCase.execute(institutionId, request))
        .isInstanceOf(InstitutionNotFoundException.class);

    verify(institutionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw when city does not exist")
  void execute_throwsWhenCityNotFound() {
    UUID institutionId = UUID.randomUUID();
    UUID cityId = UUID.randomUUID();
    UpdateInstitutionRequest request = updateRequest(cityId, true);

    when(institutionRepository.findWithLocationById(institutionId))
        .thenReturn(Optional.of(institutionWith(institutionId)));
    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> updateInstitutionUseCase.execute(institutionId, request))
        .isInstanceOf(CityNotFoundException.class);

    verify(institutionRepository, never()).save(any());
  }

  private static UpdateInstitutionRequest updateRequest(UUID cityId, boolean active) {
    return new UpdateInstitutionRequest(
        "Boero Actualizado", null, cityId, null, null, null, null, null, null, active);
  }

  private static Institution institutionWith(UUID id) {
    Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    City city = City.builder().name("Villa Maria").province(province).build();

    return Institution.builder().id(id).name("Conservatorio Boero").city(city).active(true).build();
  }

  private static City cityWith(UUID cityId) {
    Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    return City.builder().id(cityId).name("Villa Maria").province(province).build();
  }
}
