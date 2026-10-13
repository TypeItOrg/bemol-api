package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.GuardianMustBeAdultException;
import ar.edu.utn.frvm.typeit.boero_api.auth.exceptions.UserAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.boero_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionInactiveException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.Period;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {
  private final InstitutionalHostContext hostContext;

  private final InstitutionalEmailVerificationUseCase emailVerification;
  private final UserRepository userRepository;
  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final PasswordEncoder passwordEncoder;
  private final Validator validator;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;
  private final BusinessDateProvider businessDateProvider;

  @Transactional
  public UserRegisteredResponse execute(final RegisterRequest request) {
    if (request.registersAsGuardian() && isUnderage(request.birthDate())) {
      throw new GuardianMustBeAdultException();
    }

    hostContext.requireInstitution(request.institutionId());

    Institution institution =
        institutionRepository
            .findById(request.institutionId())
            .orElseThrow(InstitutionNotFoundException::new);

    if (!institution.isActive()) {
      throw new InstitutionInactiveException();
    }

    Person person =
        personRepository
            .findByDocumentNumberAndInstitutionIdForUpdate(
                request.documentNumber(), request.institutionId())
            .orElse(null);
    final boolean preserveExistingRoles = person != null;
    final SystemRoleCode roleCode;

    if (preserveExistingRoles) {
      if (userRepository
          .findByPerson_IdAndInstitution_Id(requireNonNull(person).getId(), request.institutionId())
          .isPresent()) {
        throw new UserAlreadyExistsException();
      }
      if (!request.birthDate().equals(person.getBirthDate())) {
        throw new DependentBirthDateMismatchException();
      }

      person.updateContact(request.email(), person.getPhoneNumber());
      assertPersonValid(person);
      personRepository.save(person);
      personRepository.flush();
      roleCode = SystemRoleCode.APPLICANT;
    } else {
      person =
          Person.builder()
              .institution(institution)
              .firstName(request.name())
              .lastName(request.lastName())
              .birthDate(request.birthDate())
              .documentNumber(request.documentNumber())
              .email(request.email())
              .build();
      assertPersonValid(person);
      try {
        person = personRepository.save(person);
        personRepository.flush();
      } catch (DataIntegrityViolationException exception) {
        throw new UserAlreadyExistsException();
      }
      roleCode = request.registersAsGuardian() ? SystemRoleCode.GUARDIAN : SystemRoleCode.APPLICANT;
    }

    User user =
        User.builder()
            .institution(institution)
            .person(person)
            .emailVerificationStatus(EmailVerificationStatus.PENDING)
            .password(requireNonNull(passwordEncoder.encode(request.password())))
            .build();
    user = userRepository.save(user);
    if (preserveExistingRoles) {
      assignPersonSystemRoleUseCase.executePreservingRoles(person, roleCode);
    } else {
      assignPersonSystemRoleUseCase.execute(person, roleCode, false);
    }
    emailVerification.sendInitial(user);
    return UserRegisteredResponse.builder()
        .emailVerificationRequired(true)
        .userId(user.getId())
        .documentNumber(user.getDocumentNumber())
        .institutionId(user.getInstitutionId())
        .build();
  }

  private boolean isUnderage(final LocalDate birthDate) {
    return Period.between(birthDate, businessDateProvider.today()).getYears()
        < PersonFieldConstraints.ADULT_AGE;
  }

  private void assertPersonValid(Person person) {
    Set<ConstraintViolation<Person>> violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
