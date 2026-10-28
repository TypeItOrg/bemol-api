package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePersonUseCase {

  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalAccountManagementUseCase
      accounts;
  private final Validator validator;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public PersonResponse execute(final UUID institutionId, final CreatePersonRequest request) {
    Institution institution =
        institutionRepository
            .findByIdForUpdate(institutionId)
            .orElseThrow(InstitutionNotFoundException::new);

    if (personRepository.existsByDocumentNumberAndInstitution_Id(
        request.documentNumber(), institutionId)) {
      throw new PersonAlreadyExistsException();
    }

    Person person =
        Person.builder()
            .institution(institution)
            .firstName(request.firstName())
            .lastName(request.lastName())
            .documentNumber(request.documentNumber())
            .email(request.email())
            .phoneNumber(request.phoneNumber())
            .birthDate(request.birthDate())
            .build();

    final var violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    try {
      person = personRepository.save(person);
      personRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
        if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
            && "people_active_document_number_unique".equals(violation.getConstraintName())) {
          throw new PersonAlreadyExistsException();
        }
      }
      throw exception;
    }

    SystemRoleCode role =
        request.initialRole() != null ? request.initialRole() : SystemRoleCode.APPLICANT;
    assignPersonSystemRoleUseCase.execute(person, role);
    accounts.invite(institutionId, person.getId(), request.email());

    auditEventRecorder.recordCurrentActor(
        institution,
        person.getId(),
        AuditAction.PERSON_CREATED,
        AuditEntityType.PERSON,
        person.getId(),
        AuditOrigin.administration(),
        Map.of(
            "initialRole",
            role.name(),
            "emailVerificationRequired",
            true,
            "emailVerificationStatus",
            "PENDING",
            "personCreated",
            true));

    return PersonResponse.from(person);
  }
}
