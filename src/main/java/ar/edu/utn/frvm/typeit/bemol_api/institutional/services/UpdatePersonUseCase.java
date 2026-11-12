package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCurrentPasswordException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Address;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CityNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.CountryNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.AddressRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CityRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.CountryRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePersonUseCase {
  private final AuditEventRecorder auditRecorder;

  private final PersonRepository personRepository;
  private final InstitutionRepository institutionRepository;
  private final OperatingContextResolver operatingContextResolver;
  private final PersonAccessInvitationRepository accessInvitationRepository;
  private final Clock clock;
  private final UserRepository userRepository;
  private final CityRepository cityRepository;
  private final CountryRepository countryRepository;
  private final AddressRepository addressRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;
  private final Validator validator;

  @Transactional
  public PersonResponse execute(JwtAuthenticatedUser principal, UpdatePersonRequest request) {
    // Share the lock with account activation and representation changes.
    if (request.email() != null) {
      institutionRepository
          .findByIdForUpdate(principal.institutionId())
          .orElseThrow(InstitutionNotFoundException::new);
    }

    Person person =
        (request.email() != null
                ? personRepository.findByIdAndInstitutionIdForUpdate(
                    principal.personId(), principal.institutionId())
                : personRepository.findByIdAndInstitution_Id(
                    principal.personId(), principal.institutionId()))
            .orElseThrow(PersonNotFoundException::new);
    final boolean identityChanged =
        changes(request.firstName(), person.getFirstName())
            || changes(request.lastName(), person.getLastName())
            || changes(request.birthDate(), person.getBirthDate());
    if (identityChanged) {
      throw new OwnAccessException(InstitutionMessages.VERIFIED_IDENTITY_ADMIN_ONLY);
    }
    if (changes(request.email(), person.getEmail())) {
      if (!principal.delegated()
          || userRepository
              .findByPerson_IdAndInstitution_Id(principal.personId(), principal.institutionId())
              .isPresent()) {
        throw new OwnAccessException(InstitutionMessages.ACCOUNT_EMAIL_CHANGE_REQUIRED);
      }
      operatingContextResolver.requireAccessible(
          principal.institutionId(), principal.personId(), principal);
      if (request.email() == null || request.email().isBlank()) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_REQUIRED);
      }
      accessInvitationRepository
          .findByPerson_Id(person.getId())
          .forEach(invitation -> invitation.cancel(clock.instant()));
    }
    if (request.password() != null && !request.password().isEmpty() && principal.delegated()) {
      throw new InvalidCurrentPasswordException();
    }
    final var before = AuditSnapshot.of(person);
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
        request.email() != null ? request.email() : person.getEmail(),
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
    final var after = AuditSnapshot.of(person);
    if (!before.values().equals(after.values())) {
      auditRecorder.recordCurrentActor(
          person.getInstitution(),
          person.getId(),
          InstitutionalAuditAction.PERSON_UPDATED,
          Person.AUDIT_TYPE,
          person.getId(),
          AuditOrigin.SELF_SERVICE,
          AuditChangeDetails.change("Persona", before, after));
    }

    passwordUser.ifPresent(
        user -> {
          final var passwordBefore = user.passwordAuditSnapshot();
          user.changePassword(requireNonNull(passwordEncoder.encode(password)));
          userRepository.save(user);
          auditRecorder.recordCurrentActor(
              user.getInstitution(),
              person.getId(),
              AuthAuditAction.PASSWORD_CHANGED,
              User.AUDIT_TYPE,
              user.getId(),
              AuditOrigin.SELF_SERVICE,
              AuditChangeDetails.change(
                  "Cuenta institucional", passwordBefore, user.passwordAuditSnapshot()));
          sessionRevocationService.revokeInstitutionalSessionsForUser(user.getId());
        });

    return personRepository
        .findWithDetailsByIdAndInstitution_Id(principal.personId(), principal.institutionId())
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }

  private static boolean changes(final @Nullable Object requested, final @Nullable Object current) {
    return requested != null && !requested.equals(current);
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
        userRepository
            .findWithLockById(principal.userId())
            .orElseThrow(InvalidCredentialsException::new);
    if (request.currentPassword() == null
        || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new InvalidCurrentPasswordException();
    }

    return Optional.of(user);
  }
}
