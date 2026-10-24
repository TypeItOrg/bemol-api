package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.requests.CreateInstitutionRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateInstitutionUseCaseTest {

  @Mock private AuditEventRecorder auditEventRecorder;
  @Mock private CityRepository cityRepository;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private InstitutionRoleProvisioner institutionRoleProvisioner;

  @InjectMocks private CreateInstitutionUseCase createInstitutionUseCase;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("Should create institution with the requested state")
  void execute_createsInstitution(boolean active) {
    UUID cityId = UUID.randomUUID();
    UUID savedId = UUID.randomUUID();
    City city = cityWith(cityId);
    CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero",
            cityId,
            "San Martin",
            "123",
            "Centro",
            null,
            "0353-123456",
            "info@boero.edu.ar",
            active);

    when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));
    when(institutionRepository.save(any(Institution.class)))
        .thenAnswer(
            invocation -> {
              Institution institution = invocation.getArgument(0);
              return Institution.builder()
                  .id(savedId)
                  .name(institution.getName())
                  .city(institution.getCity())
                  .street(institution.getStreet())
                  .number(institution.getNumber())
                  .neighborhood(institution.getNeighborhood())
                  .phoneNumber(institution.getPhoneNumber())
                  .email(institution.getEmail())
                  .active(institution.isActive())
                  .build();
            });
    when(institutionRepository.findWithLocationById(savedId))
        .thenReturn(
            Optional.of(
                Institution.builder()
                    .id(savedId)
                    .name(request.name())
                    .city(city)
                    .street(request.street())
                    .number(request.number())
                    .neighborhood(request.neighborhood())
                    .phoneNumber(request.phoneNumber())
                    .email(request.email())
                    .active(active)
                    .build()));

    var response = createInstitutionUseCase.execute(request);

    ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
    verify(institutionRepository).save(captor.capture());
    verify(institutionRoleProvisioner).provision(any(Institution.class));
    assertThat(captor.getValue().isActive()).isEqualTo(active);
    assertThat(response.id()).isEqualTo(savedId);
    assertThat(response.name()).isEqualTo(request.name());
    assertThat(response.active()).isEqualTo(active);
    assertThat(response.city().name()).isEqualTo("Villa Maria");
    assertThat(response.country().isoCode()).isEqualTo("ARG");
  }

  @Test
  @DisplayName("Should throw when city does not exist")
  void execute_throwsWhenCityNotFound() {
    UUID cityId = UUID.randomUUID();
    CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero", cityId, null, null, null, null, null, null, true);

    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createInstitutionUseCase.execute(request))
        .isInstanceOf(CityNotFoundException.class);

    verify(institutionRepository, never()).save(any());
  }

  private static City cityWith(UUID cityId) {
    Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    return City.builder().id(cityId).name("Villa Maria").province(province).build();
  }
}
