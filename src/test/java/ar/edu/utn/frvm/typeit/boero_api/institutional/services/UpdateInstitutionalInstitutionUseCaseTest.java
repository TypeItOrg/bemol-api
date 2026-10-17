package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.UpdateInstitutionalInstitutionRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateInstitutionalInstitutionUseCaseTest {

  @Mock private AuditEventRecorder auditEventRecorder;
  @Mock private CityRepository cityRepository;
  @Mock private InstitutionRepository institutionRepository;

  @InjectMocks private UpdateInstitutionalInstitutionUseCase updateInstitutionalInstitutionUseCase;

  @Test
  @DisplayName("Should update institution details successfully")
  void execute_updatesInstitutionDetails() {
    final UUID institutionId = UUID.randomUUID();
    final UUID newCityId = UUID.randomUUID();
    final Institution institution = institutionWith(institutionId);
    final City newCity = newCityWith(newCityId);

    when(institutionRepository.findWithLocationById(institutionId))
        .thenReturn(Optional.of(institution));
    when(cityRepository.findById(newCityId)).thenReturn(Optional.of(newCity));

    final var request =
        new UpdateInstitutionalInstitutionRequest(
            "Nuevo Nombre",
            newCityId,
            "Calle Falsa",
            "123",
            "Centro",
            "Piso 1",
            "0353-999999",
            "nuevo@boero.edu.ar");

    final var response = updateInstitutionalInstitutionUseCase.execute(institutionId, request);

    assertThat(response.id()).isEqualTo(institutionId);
    assertThat(response.name()).isEqualTo("Nuevo Nombre");
    assertThat(response.street()).isEqualTo("Calle Falsa");
    assertThat(response.number()).isEqualTo("123");
    assertThat(response.neighborhood()).isEqualTo("Centro");
    assertThat(response.additionalInfo()).isEqualTo("Piso 1");
    assertThat(response.phoneNumber()).isEqualTo("0353-999999");
    assertThat(response.email()).isEqualTo("nuevo@boero.edu.ar");
    assertThat(response.city().name()).isEqualTo("Río Cuarto");
    verify(institutionRepository).save(any(Institution.class));
  }

  @Test
  @DisplayName("Should throw when institution not found")
  void execute_throwsWhenInstitutionNotFound() {
    final UUID institutionId = UUID.randomUUID();
    when(institutionRepository.findWithLocationById(institutionId)).thenReturn(Optional.empty());

    final var request =
        new UpdateInstitutionalInstitutionRequest(
            "Nombre", UUID.randomUUID(), null, null, null, null, null, null);

    assertThatThrownBy(() -> updateInstitutionalInstitutionUseCase.execute(institutionId, request))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  @DisplayName("Should throw when city not found")
  void execute_throwsWhenCityNotFound() {
    final UUID institutionId = UUID.randomUUID();
    final UUID cityId = UUID.randomUUID();
    final Institution institution = institutionWith(institutionId);

    when(institutionRepository.findWithLocationById(institutionId))
        .thenReturn(Optional.of(institution));
    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());

    final var request =
        new UpdateInstitutionalInstitutionRequest(
            "Nombre", cityId, null, null, null, null, null, null);

    assertThatThrownBy(() -> updateInstitutionalInstitutionUseCase.execute(institutionId, request))
        .isInstanceOf(CityNotFoundException.class);
  }

  private static Institution institutionWith(final UUID id) {
    final Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    final Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    final City city =
        City.builder().id(UUID.randomUUID()).name("Villa Maria").province(province).build();

    return Institution.builder()
        .id(id)
        .name("Conservatorio Boero")
        .slug("boero-villa-maria")
        .city(city)
        .active(true)
        .build();
  }

  private static City newCityWith(final UUID cityId) {
    final Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    final Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    return City.builder().id(cityId).name("Río Cuarto").province(province).build();
  }
}
