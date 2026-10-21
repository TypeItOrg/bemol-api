package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.LastPersonRoleRevocationException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevokePersonRoleUseCase {
  private final RoleAdministrationLock administrationLock;

  private final RoleAssignmentScopeValidator scopeValidator;
  private final SessionRevocationService sessionRevocationService;
  private final InstitutionPersonResolver institutionPersonResolver;
  private final RoleRepository roleRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final AuthorizationCacheInvalidator authorizationCacheInvalidator;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public void execute(UUID institutionId, UUID personId, UUID roleId, boolean allowAuthority) {
    administrationLock.lock(institutionId);
    if (!allowAuthority) {
      scopeValidator.requireOperation(PermissionCode.INSTITUTION_ROLE_REVOKE);
    }
    final var person =
        institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId);
    Role role =
        roleRepository
            .findByIdAndScopeAndInstitution_Id(roleId, RoleScope.INSTITUTION, institutionId)
            .orElseThrow(RoleNotAssignableException::new);
    boolean authority = role.isInstitutionalAuthority();
    if (authority && !allowAuthority) {
      throw new RoleNotAssignableException();
    }
    List<PersonRoleAssignment> currentAssignments =
        personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId);

    if (!allowAuthority) {
      currentAssignments.stream()
          .filter(a -> a.getRole().getId().equals(roleId))
          .forEach(scopeValidator::requireDelegation);
    }
    preventRevokingOnlyRole(currentAssignments, role.getId());
    personRoleAssignmentRepository
        .findByPerson_IdAndRole_IdAndInstitution_Id(personId, roleId, institutionId)
        .ifPresent(
            assignment -> {
              final var before = RoleAuditDetails.assignments(currentAssignments);
              personRoleAssignmentRepository.delete(assignment);
              final var after =
                  RoleAuditDetails.assignments(
                      currentAssignments.stream()
                          .filter(current -> !current.getRole().getId().equals(roleId))
                          .toList());
              auditEventRecorder.recordCurrentActor(
                  person.getInstitution(),
                  personId,
                  AuditAction.PERSON_ROLES_CHANGED,
                  AuditEntityType.PERSON,
                  personId,
                  AuditOrigin.administration(),
                  Map.of("before", before, "after", after));
            });
    sessionRevocationService.revokeInstitutionalSessionsForPerson(personId, institutionId);
    authorizationCacheInvalidator.evictPerson(personId, institutionId);
  }

  private void preventRevokingOnlyRole(
      final List<PersonRoleAssignment> currentAssignments, final UUID roleId) {
    boolean roleIsAssigned =
        currentAssignments.stream()
            .anyMatch(assignment -> assignment.getRole().getId().equals(roleId));
    if (roleIsAssigned && currentAssignments.size() == 1) {
      throw new LastPersonRoleRevocationException();
    }
  }
}
