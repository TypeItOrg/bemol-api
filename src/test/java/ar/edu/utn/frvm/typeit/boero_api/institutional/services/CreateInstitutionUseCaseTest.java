package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.SlugAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.CreateInstitutionRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreateInstitutionUseCaseTest {

  @Mock private AuditEventRecorder auditEventRecorder;
  @Mock private CityRepository cityRepository;
  @Mock private InstitutionRepository institutionRepository;
  @Mock private InstitutionRoleProvisioner institutionRoleProvisioner;

  @InjectMocks private CreateInstitutionUseCase createInstitutionUseCase;

  @Test
  @DisplayName("Should create active institution")
  void execute_createsInstitution() {
    UUID cityId = UUID.randomUUID();
    UUID savedId = UUID.randomUUID();
    City city = cityWith(cityId);
    CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero",
            "boero-villa-maria",
            cityId,
            "San Martin",
            "123",
            "Centro",
            null,
            "0353-123456",
            "info@boero.edu.ar");

    when(institutionRepository.existsBySlug("boero-villa-maria")).thenReturn(false);
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));
    when(institutionRepository.save(any(Institution.class)))
        .thenAnswer(
            invocation -> {
              Institution institution = invocation.getArgument(0);
              return Institution.builder()
                  .id(savedId)
                  .name(institution.getName())
                  .slug(institution.getSlug())
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
                    .slug(request.slug())
                    .city(city)
                    .street(request.street())
                    .number(request.number())
                    .neighborhood(request.neighborhood())
                    .phoneNumber(request.phoneNumber())
                    .email(request.email())
                    .active(true)
                    .build()));

    var response = createInstitutionUseCase.execute(request);

    ArgumentCaptor<Institution> captor = ArgumentCaptor.forClass(Institution.class);
    verify(institutionRepository).save(captor.capture());
    verify(institutionRoleProvisioner).provision(any(Institution.class));
    assertThat(captor.getValue().isActive()).isTrue();
    assertThat(response.slug()).isEqualTo("boero-villa-maria");
    assertThat(response.city().name()).isEqualTo("Villa Maria");
    assertThat(response.country().isoCode()).isEqualTo("ARG");
  }

  @Test
  @DisplayName("Should throw when slug already exists")
  void execute_throwsWhenSlugAlreadyExists() {
    CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero",
            "boero-villa-maria",
            UUID.randomUUID(),
            null,
            null,
            null,
            null,
            null,
            null);

    when(institutionRepository.existsBySlug("boero-villa-maria")).thenReturn(true);

    assertThatThrownBy(() -> createInstitutionUseCase.execute(request))
        .isInstanceOf(SlugAlreadyExistsException.class);

    verify(institutionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw when city does not exist")
  void execute_throwsWhenCityNotFound() {
    UUID cityId = UUID.randomUUID();
    CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero", "boero-villa-maria", cityId, null, null, null, null, null, null);

    when(institutionRepository.existsBySlug("boero-villa-maria")).thenReturn(false);
    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> createInstitutionUseCase.execute(request))
        .isInstanceOf(CityNotFoundException.class);

    verify(institutionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should map a concurrent slug constraint violation to conflict")
  void execute_mapsConcurrentSlugConflict() {
    final UUID cityId = UUID.randomUUID();
    final CreateInstitutionRequest request =
        new CreateInstitutionRequest(
            "Conservatorio Boero", "boero-race", cityId, null, null, null, null, null, null);
    when(institutionRepository.existsBySlug("boero-race")).thenReturn(false);
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(cityWith(cityId)));
    when(institutionRepository.save(any(Institution.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(new DataIntegrityViolationException("duplicate slug"))
        .when(institutionRepository)
        .flush();

    assertThatThrownBy(() -> createInstitutionUseCase.execute(request))
        .isInstanceOf(SlugAlreadyExistsException.class);
  }

  private static City cityWith(UUID cityId) {
    Country country =
        Country.builder().id(UUID.randomUUID()).name("Argentina").isoCode("ARG").build();
    Province province =
        Province.builder().id(UUID.randomUUID()).country(country).name("Cordoba").build();
    return City.builder().id(cityId).name("Villa Maria").province(province).build();
  }
}
