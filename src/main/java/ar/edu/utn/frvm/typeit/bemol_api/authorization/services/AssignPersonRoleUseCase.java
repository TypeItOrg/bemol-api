package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignPersonRoleUseCase {
  private final PersonRoleResponseFactory responseFactory;
  private final RoleAdministrationLock administrationLock;

  private final RoleAssignmentScopeValidator scopeValidator;
  private final InstitutionPersonResolver institutionPersonResolver;
  private final RoleRepository roleRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public PersonRoleResponse execute(
      UUID institutionId, UUID personId, AssignRoleRequest request, boolean allowAuthority) {
    administrationLock.lock(institutionId);
    if (!allowAuthority) {
      scopeValidator.requireOperation(PermissionCode.INSTITUTION_ROLE_ASSIGN);
    }
    Person person =
        institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId);
    Role role =
        roleRepository
            .findByIdAndScopeAndInstitution_Id(
                request.roleId(), RoleScope.INSTITUTION, institutionId)
            .orElseThrow(RoleNotAssignableException::new);
    if (!allowAuthority && role.isInstitutionalAuthority()) {
      throw new RoleNotAssignableException();
    }

    var existing =
        personRoleAssignmentRepository.findByPerson_IdAndRole_IdAndInstitution_Id(
            personId, role.getId(), institutionId);
    scopeValidator.validate(
        institutionId, role, request, existing.map(a -> a.getTrainingPathIds()).orElse(Set.of()));
    if (existing.isPresent()
        && (existing.get().getAccessScope() != request.accessScope()
            || !existing.get().getTrainingPathIds().equals(request.selectedTrainingPathIds()))) {
      throw new InvalidAccessScopeException();
    }
    if (!allowAuthority) {
      scopeValidator.requireDelegation(
          role, request.accessScope(), request.selectedTrainingPathIds());
    }
    final var current =
        personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId);
    final var before = RoleAuditDetails.assignments(current);
    if (!allowAuthority) {
      for (var assignment : current) {
        if (role.isApplicant()
            ? !assignment.getRole().isApplicant()
            : assignment.getRole().isApplicant()) {
          scopeValidator.requireOperation(PermissionCode.INSTITUTION_ROLE_REVOKE);
          scopeValidator.requireDelegation(assignment);
        }
      }
    }
    assignPersonSystemRoleUseCase.execute(person, role, true);
    personRoleAssignmentRepository
        .findByPerson_IdAndRole_IdAndInstitution_Id(personId, role.getId(), institutionId)
        .orElseThrow()
        .changeAccessScope(request.accessScope(), request.selectedTrainingPathIds());

    final var finalAssignments =
        personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId);
    final var after = RoleAuditDetails.assignments(finalAssignments);
    if (!before.equals(after)) {
      auditEventRecorder.recordCurrentActor(
          person.getInstitution(),
          personId,
          AuditAction.PERSON_ROLES_CHANGED,
          AuditEntityType.PERSON,
          personId,
          AuditOrigin.administration(),
          Map.of("before", before, "after", after));
    }
    PersonRoleResponse response =
        finalAssignments.stream()
            .filter(assignment -> assignment.getRole().getId().equals(role.getId()))
            .findFirst()
            .map(responseFactory::from)
            .orElseThrow();

    log.info(
        "[Role] Assigned successfully, personId: {}, roleId: {}, institutionId: {}",
        personId,
        role.getId(),
        institutionId);

    return response;
  }
}
