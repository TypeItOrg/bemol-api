package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.audit.AuthorizationAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapInstitutionalAuthorityUseCase {

  private final InstitutionPersonResolver institutionPersonResolver;
  private final InstitutionalSystemRoleResolver institutionalSystemRoleResolver;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public PersonRoleResponse execute(UUID institutionId, UUID personId) {
    Person person = institutionPersonResolver.requirePersonInInstitution(institutionId, personId);
    Role role =
        institutionalSystemRoleResolver.requireInstitutionalSystemRole(
            institutionId, SystemRoleCode.INSTITUTIONAL_AUTHORITY);

    final var before =
        PersonRoleAssignment.auditAssignments(
            personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
                personId, institutionId));
    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.INSTITUTIONAL_AUTHORITY);
    final var after =
        PersonRoleAssignment.auditAssignments(
            personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
                personId, institutionId));
    if (!before.values().equals(after.values())) {
      auditEventRecorder.recordCurrentActor(
          person.getInstitution(),
          personId,
          AuthorizationAuditAction.PERSON_ROLES_CHANGED,
          Person.AUDIT_TYPE,
          personId,
          AuditOrigin.PLATFORM_ADMINISTRATION,
          AuditChangeDetails.change("Roles y alcances", before, after));
    }

    return personRoleAssignmentRepository
        .findByPerson_IdAndRole_IdAndInstitution_Id(person.getId(), role.getId(), institutionId)
        .map(PersonRoleResponse::from)
        .orElseThrow();
  }
}
