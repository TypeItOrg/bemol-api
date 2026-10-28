package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonByAdminRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePersonByAdminUseCase {
  private final AuditEventRecorder auditRecorder;

  private final PersonRepository personRepository;
  private final ar.edu.utn.frvm.typeit.bemol_api.auth.services.AccountAccessAuthorization access;
  private final ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository
      institutions;
  private final SessionRevocationService sessionRevocationService;
  private final Validator validator;

  @Transactional
  public PersonResponse execute(
      final UUID institutionId, final UUID personId, final UpdatePersonByAdminRequest request) {
    institutions
        .findByIdForUpdate(institutionId)
        .orElseThrow(
            ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException
                ::new);
    access.requireIdentityCorrection(institutionId);
    Person person =
        personRepository
            .findByIdAndInstitutionIdForUpdate(personId, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(PersonNotFoundException::new);
    final boolean documentChanged =
        request.documentNumber() != null
            && !request.documentNumber().equals(person.getDocumentNumber());
    if (documentChanged
        && personRepository.existsByDocumentNumberAndInstitution_Id(
            java.util.Objects.requireNonNull(request.documentNumber()), institutionId)) {
      throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions
          .PersonAlreadyExistsException();
    }
    final var before = PersonAuditDetails.snapshot(person);
    if (request.isEmpty()) {
      throw new ConstraintViolationException(Set.of());
    }

    person.updateIdentity(
        request.firstName() != null ? request.firstName() : person.getFirstName(),
        request.lastName() != null ? request.lastName() : person.getLastName(),
        request.birthDate() != null ? request.birthDate() : person.getBirthDate(),
        person.getBirthCity(),
        person.getNationalityCountry());
    person.updateContact(
        person.getEmail(),
        request.phoneNumber() != null ? request.phoneNumber() : person.getPhoneNumber());
    if (documentChanged) {
      person.correctDocumentNumber(java.util.Objects.requireNonNull(request.documentNumber()));
    }
    final var violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    try {
      personRepository.saveAndFlush(person);
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
            && "people_active_document_number_unique".equals(violation.getConstraintName())) {
          throw new ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions
              .PersonAlreadyExistsException();
        }
      }
      throw exception;
    }
    if (documentChanged) {
      sessionRevocationService.revokeInstitutionalSessionsForPerson(personId, institutionId);
    }
    auditRecorder.recordChange(
        person.getInstitution(),
        person.getId(),
        AuditAction.PERSON_UPDATED,
        AuditEntityType.PERSON,
        person.getId(),
        "Persona",
        before,
        PersonAuditDetails.snapshot(person));

    return personRepository
        .findWithDetailsByIdAndInstitution_Id(personId, institutionId)
        .map(PersonResponse::from)
        .orElseThrow(PersonNotFoundException::new);
  }
}
