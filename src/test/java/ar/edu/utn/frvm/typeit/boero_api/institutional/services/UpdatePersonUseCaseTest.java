package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.InvalidCurrentPasswordException;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Address;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.City;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Country;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Province;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CountryNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.AddressRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.CountryRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.UpdateAddressRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.UpdatePersonRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UpdatePersonUseCaseTest {

  @Mock private PersonRepository personRepository;
  @Mock private UserRepository userRepository;
  @Mock private CityRepository cityRepository;
  @Mock private CountryRepository countryRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionRevocationService sessionRevocationService;

  private static ValidatorFactory validatorFactory;
  private UpdatePersonUseCase updatePersonUseCase;

  @BeforeAll
  static void createValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  private UUID institutionId;
  private UUID personId;
  private Institution institution;
  private Person person;
  private JwtAuthenticatedUser principal;

  @BeforeEach
  void setUp() {
    institutionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    personId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    institution = Institution.builder().id(institutionId).name("Conservatorio Boero").build();
    person =
        Person.builder()
            .id(personId)
            .firstName("Juan")
            .lastName("Pérez")
            .documentNumber("12345678")
            .email("juan@example.com")
            .institution(institution)
            .build();
    principal =
        JwtAuthenticatedUser.builder()
            .userId(UUID.randomUUID())
            .personId(personId)
            .institutionId(institutionId)
            .build();

    when(personRepository.findByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));
    updatePersonUseCase =
        new UpdatePersonUseCase(
            personRepository,
            userRepository,
            cityRepository,
            countryRepository,
            addressRepository,
            passwordEncoder,
            sessionRevocationService,
            validatorFactory.getValidator());
  }

  private void stubUpdatedDetails() {
    when(personRepository.findWithDetailsByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.of(person));
  }

  @Test
  @DisplayName("Updates the complete identity and contact without changing the tenant or account")
  void execute_updatesIdentityAndContact() {
    stubUpdatedDetails();
    UUID provinceId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UUID cityId = UUID.fromString("00000000-0000-0000-0000-000000000004");
    UUID countryId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    Province province = Province.builder().id(provinceId).name("Córdoba").build();
    City city = City.builder().id(cityId).name("Villa María").province(province).build();
    Country country = Country.builder().id(countryId).name("Argentina").isoCode("ARG").build();
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));
    when(countryRepository.findById(countryId)).thenReturn(Optional.of(country));
    LocalDate birthDate = LocalDate.of(1990, 5, 15);

    var response =
        updatePersonUseCase.execute(
            principal,
            new UpdatePersonRequest(
                "Carlos",
                "García",
                birthDate,
                "carlos@test.com",
                "0353-123456",
                cityId,
                countryId,
                null));

    assertThat(response)
        .isEqualTo(
            new ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.PersonResponse(
                personId,
                "Carlos",
                "García",
                "12345678",
                birthDate,
                "0353-123456",
                "carlos@test.com",
                institutionId,
                "Conservatorio Boero",
                null,
                new ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person
                    .CitySummaryResponse(cityId, "Villa María", provinceId, "Córdoba"),
                new ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.CountrySummaryResponse(
                    countryId, "Argentina", "ARG"),
                false));
    assertThat(person.getBirthCity()).isSameAs(city);
    assertThat(person.getNationalityCountry()).isSameAs(country);
    assertThat(person.getInstitution()).isSameAs(institution);
    verify(personRepository).save(person);
    verifyNoInteractions(
        userRepository, passwordEncoder, sessionRevocationService, addressRepository);
  }

  @Test
  @DisplayName("Should hash the new password and revoke institutional sessions")
  void execute_updatesPassword() {
    stubUpdatedDetails();
    User user =
        User.builder()
            .id(principal.userId())
            .institution(institution)
            .person(person)
            .password("old-hash")
            .build();
    when(userRepository.findById(principal.userId())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    UpdatePersonRequest request =
        new UpdatePersonRequest(
            null, null, null, null, null, null, null, null, "old-password", "new-password");

    updatePersonUseCase.execute(principal, request);

    assertThat(user.getPassword()).isEqualTo("new-hash");
    verify(passwordEncoder).matches("old-password", "old-hash");
    verify(passwordEncoder).encode("new-password");
    verify(userRepository).save(user);
    verify(sessionRevocationService).revokeInstitutionalSessionsForUser(principal.userId());
  }

  @Test
  @DisplayName("Should reject a password change when the current password is invalid")
  void execute_rejectsInvalidCurrentPassword() {
    User user =
        User.builder()
            .id(principal.userId())
            .institution(institution)
            .person(person)
            .password("old-hash")
            .build();
    when(userRepository.findById(principal.userId())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);
    UpdatePersonRequest request =
        new UpdatePersonRequest(
            null, null, null, null, null, null, null, null, "wrong-password", "new-password");

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOf(InvalidCurrentPasswordException.class);

    verify(personRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
    verifyNoInteractions(sessionRevocationService);
    assertThat(user.getPassword()).isEqualTo("old-hash");
  }

  @Test
  @DisplayName("Should create new address when person has none")
  void execute_createsAddress() {
    stubUpdatedDetails();
    UUID cityId = UUID.randomUUID();
    Province province = Province.builder().name("Córdoba").build();
    City city = City.builder().id(cityId).name("Villa María").province(province).build();
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));
    UpdatePersonRequest request =
        new UpdatePersonRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new UpdateAddressRequest(cityId, "San Martín", "123", null, null, null, null));

    var response = updatePersonUseCase.execute(principal, request);

    assertThat(response.address()).isNotNull();
    assertThat(response.address().street()).isEqualTo("San Martín");

    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    verify(addressRepository).save(captor.capture());
    assertThat(captor.getValue().getInstitution()).isEqualTo(institution);
  }

  @Test
  @DisplayName("Should update existing address")
  void execute_updatesExistingAddress() {
    stubUpdatedDetails();
    UUID cityId = UUID.randomUUID();
    Province province = Province.builder().name("Córdoba").build();
    City newCity = City.builder().id(cityId).name("Córdoba").province(province).build();
    City oldCity = City.builder().name("Villa María").province(province).build();
    Address address =
        Address.builder()
            .id(UUID.randomUUID())
            .institution(institution)
            .street("San Martín")
            .number("100")
            .city(oldCity)
            .build();
    person.changeAddress(address);
    when(cityRepository.findById(cityId)).thenReturn(Optional.of(newCity));
    UpdatePersonRequest request =
        new UpdatePersonRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new UpdateAddressRequest(cityId, "Belgrano", "200", null, "3B", null, null));

    var response = updatePersonUseCase.execute(principal, request);

    final var updatedAddress = response.address();
    assertThat(updatedAddress).isNotNull();
    assertThat(updatedAddress.street()).isEqualTo("Belgrano");
    assertThat(updatedAddress.number()).isEqualTo("200");
    assertThat(updatedAddress.apartment()).isEqualTo("3B");
    assertThat(updatedAddress.city().name()).isEqualTo("Córdoba");
  }

  @Test
  @DisplayName("Should only update provided fields (partial update)")
  void execute_partialUpdate() {
    stubUpdatedDetails();
    person.updateContact(person.getEmail(), "0353-123456");
    UpdatePersonRequest request =
        new UpdatePersonRequest(null, "García", null, null, null, null, null, null);

    var response = updatePersonUseCase.execute(principal, request);

    assertThat(response.firstName()).isEqualTo("Juan");
    assertThat(response.lastName()).isEqualTo("García");
    assertThat(response.phoneNumber()).isEqualTo("0353-123456");
    assertThat(response.email()).isEqualTo("juan@example.com");
    assertThat(response.documentNumber()).isEqualTo("12345678");
    verifyNoInteractions(userRepository, passwordEncoder, sessionRevocationService);
  }

  @Test
  @DisplayName("Should throw when person not found")
  void execute_throwsWhenPersonNotFound() {
    when(personRepository.findByIdAndInstitution_Id(personId, institutionId))
        .thenReturn(Optional.empty());
    UpdatePersonRequest request =
        new UpdatePersonRequest(null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("Should throw when birth city not found")
  void execute_throwsWhenBirthCityNotFound() {
    UUID cityId = UUID.randomUUID();
    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());
    UpdatePersonRequest request =
        new UpdatePersonRequest(null, null, null, null, null, cityId, null, null);

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOf(CityNotFoundException.class);
  }

  @Test
  @DisplayName("Should throw when nationality country not found")
  void execute_throwsWhenCountryNotFound() {
    UUID countryId = UUID.randomUUID();
    when(countryRepository.findById(countryId)).thenReturn(Optional.empty());
    UpdatePersonRequest request =
        new UpdatePersonRequest(null, null, null, null, null, null, countryId, null);

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOf(CountryNotFoundException.class);
  }

  @Test
  @DisplayName("Should throw when address city not found")
  void execute_throwsWhenAddressCityNotFound() {
    UUID cityId = UUID.randomUUID();
    when(cityRepository.findById(cityId)).thenReturn(Optional.empty());
    UpdatePersonRequest request =
        new UpdatePersonRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new UpdateAddressRequest(cityId, "San Martín", "123", null, null, null, null));

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOf(CityNotFoundException.class);
  }

  @Test
  @DisplayName("Should validate person after update")
  void execute_validatesPersonAfterUpdate() {
    UpdatePersonRequest request =
        new UpdatePersonRequest("A", null, null, null, null, null, null, null);

    assertThatThrownBy(() -> updatePersonUseCase.execute(principal, request))
        .isInstanceOfSatisfying(
            ConstraintViolationException.class,
            exception ->
                assertThat(exception.getConstraintViolations())
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("firstName"));

    verify(personRepository, never()).save(any());
  }
}
