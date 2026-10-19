package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionPersonResolver;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.person.UpdatePersonByAdminRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePersonByAdminUseCase {
  private final AuditEventRecorder auditRecorder;

  private final InstitutionPersonResolver institutionPersonResolver;
  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationService sessionRevocationService;
  private final Validator validator;

  @Transactional
  public PersonResponse execute(
      final UUID institutionId, final UUID personId, final UpdatePersonByAdminRequest request) {
    Person person = institutionPersonResolver.requirePersonInInstitution(institutionId, personId);
    final var before = PersonAuditDetails.snapshot(person);
    if (request.isEmpty()) {
      throw new ConstraintViolationException(Set.of());
    }

    person.updateIdentity(
        request.firstName() != null ? request.firstName() : person.getFirstName(),
        request.lastName() != null ? request.lastName() : person.getLastName(),
        person.getBirthDate(),
        person.getBirthCity(),
        person.getNationalityCountry());
    person.updateContact(
        request.email() != null ? request.email() : person.getEmail(),
        request.phoneNumber() != null ? request.phoneNumber() : person.getPhoneNumber());
    final var violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    personRepository.save(person);
    auditRecorder.recordChange(
        person.getInstitution(),
        person.getId(),
        AuditAction.PERSON_UPDATED,
        AuditEntityType.PERSON,
        person.getId(),
        "Persona",
        before,
        PersonAuditDetails.snapshot(person));

    if (request.hasPassword()) {
      User user =
          userRepository
              .findByPerson_IdAndInstitution_Id(personId, institutionId)
              .orElseThrow(PersonNotFoundException::new);
      user.changePassword(requireNonNull(passwordEncoder.encode(request.password())));
      userRepository.save(user);
      auditRecorder.recordCurrentActor(
          user.getInstitution(),
          person.getId(),
          AuditAction.PASSWORD_CHANGED,
          AuditEntityType.USER,
          user.getId(),
          AuditOrigin.administration(),
          Map.of());
      sessionRevocationService.revokeInstitutionalSessionsForUser(user.getId());
    }

    return personRepository
        .findWithDetailsByIdAndInstitution_Id(personId, institutionId)
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }
}
