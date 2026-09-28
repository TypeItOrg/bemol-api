package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.time.BusinessDateProvider;
import ar.edu.utn.frvm.typeit.boero_api.common.validation.PersonFieldConstraints;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CannotGuardianSelfException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentHasAccountException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentMustBeMinorException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.DependentApplicationCount;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterGuardianDependentUseCase {

  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final PersonGuardianRepository personGuardianRepository;
  private final Validator validator;
  private final BusinessDateProvider businessDateProvider;

  @Transactional
  public GuardianDependentResponse execute(
      final UUID institutionId,
      final UUID tutorPersonId,
      final CreateGuardianDependentRequest request) {
    if (Period.between(request.birthDate(), businessDateProvider.today()).getYears()
        >= PersonFieldConstraints.ADULT_AGE) {
      throw new DependentMustBeMinorException();
    }

    final Institution institution =
        institutionRepository
            .findById(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);
    final Person tutor =
        personRepository
            .findByIdAndInstitution_Id(tutorPersonId, institutionId)
            .orElseThrow(PersonNotFoundException::new);

    final Person dependent =
        personRepository
            .findByDocumentNumberAndInstitution_Id(request.documentNumber(), institutionId)
            .map(existing -> requireLinkable(institutionId, tutor, existing, request))
            .orElseGet(() -> createDependent(institution, request));

    final PersonGuardian link = link(institution, tutor, dependent, request);
    final long activeApplications =
        personGuardianRepository
            .countActiveApplicationsByApplicant(institutionId, List.of(dependent.getId()))
            .stream()
            .mapToLong(DependentApplicationCount::total)
            .sum();

    return GuardianDependentResponse.from(link, activeApplications);
  }

  /**
   * An existing person can only be claimed as a dependent while it has no account of its own and
   * the tutor knows its birth date. Otherwise anyone who guesses a document number could gain
   * access to another person's enrollment applications.
   */
  private Person requireLinkable(
      final UUID institutionId,
      final Person tutor,
      final Person existing,
      final CreateGuardianDependentRequest request) {
    if (existing.getId().equals(tutor.getId())) {
      throw new CannotGuardianSelfException();
    }

    if (personGuardianRepository.existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_Id(
        institutionId, tutor.getId(), existing.getId())) {
      throw new DependentAlreadyLinkedException();
    }

    final boolean hasAccount =
        userRepository
            .findByPerson_IdAndInstitution_Id(existing.getId(), institutionId)
            .isPresent();
    final boolean sameBirthDate = request.birthDate().equals(existing.getBirthDate());

    if (hasAccount) {
      throw new DependentHasAccountException();
    }

    if (!sameBirthDate) {
      throw new DependentBirthDateMismatchException();
    }

    return existing;
  }

  private Person createDependent(
      final Institution institution, final CreateGuardianDependentRequest request) {
    final Person person =
        Person.builder()
            .institution(institution)
            .firstName(request.firstName())
            .lastName(request.lastName())
            .documentNumber(request.documentNumber())
            .birthDate(request.birthDate())
            .build();

    final var violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    try {
      final Person saved = personRepository.save(person);
      personRepository.flush();

      return saved;
    } catch (DataIntegrityViolationException exception) {
      throw new PersonAlreadyExistsException();
    }
  }

  private PersonGuardian link(
      final Institution institution,
      final Person tutor,
      final Person dependent,
      final CreateGuardianDependentRequest request) {
    try {
      final PersonGuardian link =
          personGuardianRepository.save(
              PersonGuardian.builder()
                  .institution(institution)
                  .tutorPerson(tutor)
                  .dependentPerson(dependent)
                  .relationship(request.relationship())
                  .primaryContact(request.isPrimaryContact())
                  .build());
      personGuardianRepository.flush();

      return link;
    } catch (DataIntegrityViolationException exception) {
      throw new DependentAlreadyLinkedException();
    }
  }
}
