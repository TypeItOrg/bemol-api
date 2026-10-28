package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCurrentPasswordException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Address;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CountryNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.AddressRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CountryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePersonUseCase {
  private final AuditEventRecorder auditRecorder;

  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final CityRepository cityRepository;
  private final CountryRepository countryRepository;
  private final AddressRepository addressRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;
  private final Validator validator;

  @Transactional
  public PersonResponse execute(JwtAuthenticatedUser principal, UpdatePersonRequest request) {
    Person person =
        personRepository
            .findByIdAndInstitution_Id(principal.personId(), principal.institutionId())
            .orElseThrow(PersonNotFoundException::new);
    if ((request.firstName() != null && !request.firstName().equals(person.getFirstName()))
        || (request.lastName() != null && !request.lastName().equals(person.getLastName()))
        || (request.birthDate() != null && !request.birthDate().equals(person.getBirthDate()))) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException(
          ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages
              .VERIFIED_IDENTITY_ADMIN_ONLY);
    }
    if (request.email() != null && !request.email().equals(person.getEmail())) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException(
          ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages
              .ACCOUNT_EMAIL_CHANGE_REQUIRED);
    }
    if (request.password() != null && !request.password().isEmpty() && principal.delegated()) {
      throw new InvalidCurrentPasswordException();
    }
    final var before = PersonAuditDetails.snapshot(person);
    final String password = request.password();
    final Optional<User> passwordUser = verifyCurrentPassword(principal, request);

    var birthCity = person.getBirthCity();
    if (request.birthCityId() != null) {
      birthCity =
          cityRepository.findById(request.birthCityId()).orElseThrow(CityNotFoundException::new);
    }

    var nationalityCountry = person.getNationalityCountry();
    if (request.nationalityCountryId() != null) {
      nationalityCountry =
          countryRepository
              .findById(request.nationalityCountryId())
              .orElseThrow(CountryNotFoundException::new);
    }

    person.updateIdentity(
        person.getFirstName(),
        person.getLastName(),
        person.getBirthDate(),
        birthCity,
        nationalityCountry);
    person.updateContact(
        person.getEmail(),
        request.phoneNumber() != null ? request.phoneNumber() : person.getPhoneNumber());

    if (request.address() != null) {
      var addressRequest = request.address();
      Address address = person.getAddress();
      var city =
          cityRepository.findById(addressRequest.cityId()).orElseThrow(CityNotFoundException::new);

      if (address == null) {
        address =
            Address.create(
                person.getInstitution(),
                city,
                addressRequest.street(),
                addressRequest.number(),
                addressRequest.floor(),
                addressRequest.apartment(),
                addressRequest.neighborhood(),
                addressRequest.additionalInfo());
      } else {
        address.update(
            city,
            addressRequest.street(),
            addressRequest.number(),
            addressRequest.floor(),
            addressRequest.apartment(),
            addressRequest.neighborhood(),
            addressRequest.additionalInfo());
      }

      addressRepository.save(address);
      person.changeAddress(address);
    }

    assertPersonValid(person);
    personRepository.save(person);
    final var after = PersonAuditDetails.snapshot(person);
    if (!before.equals(after)) {
      auditRecorder.recordCurrentActor(
          person.getInstitution(),
          person.getId(),
          AuditAction.PERSON_UPDATED,
          AuditEntityType.PERSON,
          person.getId(),
          AuditOrigin.SELF_SERVICE,
          AuditChangeDetails.change("Persona", before, after));
    }

    passwordUser.ifPresent(
        user -> {
          user.changePassword(requireNonNull(passwordEncoder.encode(password)));
          userRepository.save(user);
          auditRecorder.recordCurrentActor(
              user.getInstitution(),
              person.getId(),
              AuditAction.PASSWORD_CHANGED,
              AuditEntityType.USER,
              user.getId(),
              AuditOrigin.SELF_SERVICE,
              Map.of());
          sessionRevocationService.revokeInstitutionalSessionsForUser(user.getId());
        });

    return personRepository
        .findWithDetailsByIdAndInstitution_Id(principal.personId(), principal.institutionId())
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }

  private void assertPersonValid(final Person person) {
    final var violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private Optional<User> verifyCurrentPassword(
      final JwtAuthenticatedUser principal, final UpdatePersonRequest request) {
    final String password = request.password();
    if (password == null || password.isEmpty()) {
      return Optional.empty();
    }

    final User user =
        userRepository.findById(principal.userId()).orElseThrow(InvalidCredentialsException::new);
    if (request.currentPassword() == null
        || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new InvalidCurrentPasswordException();
    }

    return Optional.of(user);
  }
}
