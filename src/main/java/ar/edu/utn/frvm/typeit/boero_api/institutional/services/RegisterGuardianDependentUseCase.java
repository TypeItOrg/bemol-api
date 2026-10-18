package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.CannotGuardianSelfException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentAlreadyLinkedException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.DependentBirthDateMismatchException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkRequestException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.CreateGuardianDependentRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentRequestMode;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
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
  private final PersonGuardianRepository personGuardianRepository;
  private final Validator validator;
  private final AuditEventRecorder auditEventRecorder;

  /**
   * Requests the link between the tutor and a person, creating the person when the document is not
   * registered yet. The link stays pending until the institution validates it, so knowing a
   * document number is not enough to represent someone.
   */
  @Transactional
  public GuardianDependentResponse execute(
      final UUID institutionId,
      final UUID tutorPersonId,
      final CreateGuardianDependentRequest request) {
    final var requestViolations = validator.validate(request);
    if (!requestViolations.isEmpty()) {
      throw new ConstraintViolationException(requestViolations);
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
            .orElseGet(
                () -> {
                  if (request.requestMode() == GuardianDependentRequestMode.EXISTING) {
                    throw new GuardianLinkRequestException();
                  }
                  return createDependent(institution, request);
                });

    final PersonGuardian link = requestLink(institution, tutor, dependent, request);
    link.requestOwnAccess(request.ownAccessRequested(), request.email());
    if (request.ownAccessRequested() && (request.email() == null || request.email().isBlank())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_EMAIL_REQUIRED);
    }
    auditEventRecorder.record(
        institution,
        tutor.getId(),
        dependent.getId(),
        AuditAction.GUARDIAN_LINK_REQUESTED,
        AuditEntityType.PERSON_GUARDIAN,
        link.getId());

    return GuardianDependentResponse.from(link, 0, List.of());
  }

  /**
   * Existing identity is never overwritten; the institution reviews declared data before approval.
   */
  private Person requireLinkable(
      final UUID institutionId,
      final Person tutor,
      final Person existing,
      final CreateGuardianDependentRequest request) {
    if (existing.getId().equals(tutor.getId())) {
      throw new CannotGuardianSelfException();
    }

    if (personGuardianRepository
        .existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
            institutionId,
            tutor.getId(),
            existing.getId(),
            List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE))) {
      throw new DependentAlreadyLinkedException();
    }

    // Legacy clients still verify birth date; the explicit modes leave identity review to the
    // institution.
    if (request.requestMode() == null && !request.birthDate().equals(existing.getBirthDate())) {
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

    final Person saved;
    try {
      saved = personRepository.save(person);
      personRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new PersonAlreadyExistsException();
    }
    auditEventRecorder.recordCurrentActor(
        institution,
        saved.getId(),
        AuditAction.PERSON_CREATED,
        AuditEntityType.PERSON,
        saved.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of("hasAccount", false));

    return saved;
  }

  private PersonGuardian requestLink(
      final Institution institution,
      final Person tutor,
      final Person dependent,
      final CreateGuardianDependentRequest request) {
    try {
      final PersonGuardian link =
          personGuardianRepository.save(
              PersonGuardian.request(
                  institution,
                  tutor,
                  dependent,
                  request.relationship(),
                  request.isPrimaryContact(),
                  request.requestMode() == GuardianDependentRequestMode.EXISTING
                      ? null
                      : request.firstName(),
                  request.requestMode() == GuardianDependentRequestMode.EXISTING
                      ? null
                      : request.lastName(),
                  request.requestMode() == GuardianDependentRequestMode.EXISTING
                      ? null
                      : request.birthDate()));
      personGuardianRepository.flush();

      return link;
    } catch (DataIntegrityViolationException exception) {
      throw new DependentAlreadyLinkedException();
    }
  }
}
