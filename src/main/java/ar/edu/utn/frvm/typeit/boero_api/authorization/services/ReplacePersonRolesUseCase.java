package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InstitutionalAuthorityRoleImmutableException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.InvalidAccessScopeException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.LastPersonRoleRevocationException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleAssignmentNotAllowedException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleNotAssignableException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.RoleRevocationNotAllowedException;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.AssignRoleRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.PersonRoleResponse;
import ar.edu.utn.frvm.typeit.boero_api.authorization.payloads.ReplacePersonRolesRequest;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplacePersonRolesUseCase {
  private final PersonRoleResponseFactory responseFactory;
  private final RoleAdministrationLock administrationLock;

  private final RoleAssignmentScopeValidator scopeValidator;
  private final InstitutionPersonResolver institutionPersonResolver;
  private final RoleRepository roleRepository;
  private final PersonRoleAssignmentRepository assignmentRepository;
  private final SessionRevocationService sessionRevocationService;
  private final AuthorizationCacheInvalidator authorizationCacheInvalidator;

  @Transactional
  public List<PersonRoleResponse> execute(
      UUID institutionId,
      UUID personId,
      ReplacePersonRolesRequest request,
      boolean allowAuthority,
      Set<PermissionCode> actorPermissions) {
    administrationLock.lock(institutionId);
    Person person =
        institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId);
    List<PersonRoleAssignment> current =
        assignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId);

    final var requested = requestedAssignments(request);
    Map<UUID, Role> desiredRoles = loadRoles(institutionId, requested.keySet());
    ensureInstitutionalAuthorityIsUnchanged(current, desiredRoles, allowAuthority);
    removeApplicantWhenAnotherRoleIsSelected(desiredRoles);
    if (desiredRoles.isEmpty()) {
      throw new LastPersonRoleRevocationException();
    }

    Set<UUID> currentIds =
        current.stream()
            .map(assignment -> assignment.getRole().getId())
            .collect(Collectors.toSet());
    Set<UUID> desiredIds = desiredRoles.keySet();
    Set<UUID> additions = new HashSet<>(desiredIds);
    additions.removeAll(currentIds);
    Set<UUID> removals = new HashSet<>(currentIds);
    removals.removeAll(desiredIds);
    boolean scopeChanged = false;
    for (var entry : desiredRoles.entrySet()) {
      var desired = requireNonNull(requested.get(entry.getKey()));
      var existing =
          current.stream()
              .filter(a -> a.getRole().getId().equals(entry.getKey()))
              .findFirst()
              .orElse(null);
      scopeValidator.validate(
          institutionId,
          entry.getValue(),
          desired,
          existing == null ? Set.of() : existing.getTrainingPathIds());
      PermissionAccess next =
          new PermissionAccess(desired.accessScope(), desired.selectedTrainingPathIds());
      PermissionAccess previous =
          existing == null
              ? PermissionAccess.none()
              : new PermissionAccess(existing.getAccessScope(), existing.getTrainingPathIds());
      if (!next.equals(previous)) {
        scopeChanged = true;
        if (!previous.contains(next)) {
          additions.add(entry.getKey());
        }
        if (!next.contains(previous)) {
          removals.add(entry.getKey());
        }
        if (!allowAuthority) {
          scopeValidator.requireDelegation(
              entry.getValue(), desired.accessScope(), desired.selectedTrainingPathIds());
          if (existing != null) {
            scopeValidator.requireDelegation(existing);
          }
        }
      }
    }
    if (!allowAuthority) {
      current.stream()
          .filter(a -> !desiredIds.contains(a.getRole().getId()))
          .forEach(scopeValidator::requireDelegation);
    }
    requirePermissionForChanges(
        additions,
        removals,
        allowAuthority,
        allowAuthority ? actorPermissions : scopeValidator.freshPermissions());

    replaceAssignments(person, current, desiredRoles, requested, currentIds);

    if (scopeChanged || !additions.isEmpty() || !removals.isEmpty()) {
      sessionRevocationService.revokeInstitutionalSessionsForPerson(personId, institutionId);
      authorizationCacheInvalidator.evictPerson(personId, institutionId);
    }

    return assignmentRepository.findByPerson_IdAndInstitution_Id(personId, institutionId).stream()
        .map(responseFactory::from)
        .toList();
  }

  private Map<UUID, AssignRoleRequest> requestedAssignments(
      final ReplacePersonRolesRequest request) {
    final var requested = new HashMap<UUID, AssignRoleRequest>();
    for (final var assignment : request.assignments()) {
      if (requested.put(assignment.roleId(), assignment) != null) {
        throw new InvalidAccessScopeException();
      }
    }

    return requested;
  }

  private void replaceAssignments(
      final Person person,
      final List<PersonRoleAssignment> current,
      final Map<UUID, Role> desiredRoles,
      final Map<UUID, AssignRoleRequest> requested,
      final Set<UUID> currentIds) {
    final var desiredIds = desiredRoles.keySet();
    current.stream()
        .filter(assignment -> !desiredIds.contains(assignment.getRole().getId()))
        .forEach(assignmentRepository::delete);
    for (final var role : desiredRoles.values()) {
      if (!currentIds.contains(role.getId())) {
        final var assignment = PersonRoleAssignment.assign(person, role, person.getInstitution());
        final var desired = requireNonNull(requested.get(role.getId()));
        assignment.changeAccessScope(desired.accessScope(), desired.selectedTrainingPathIds());
        assignmentRepository.save(assignment);
      }
    }

    for (final var assignment : current) {
      final var desired = requested.get(assignment.getRole().getId());
      if (desired != null && desiredIds.contains(desired.roleId())) {
        assignment.changeAccessScope(desired.accessScope(), desired.selectedTrainingPathIds());
      }
    }
  }

  private Map<UUID, Role> loadRoles(UUID institutionId, Set<UUID> roleIds) {
    Map<UUID, Role> roles = new HashMap<>();
    for (UUID roleId : roleIds) {
      Role role =
          roleRepository
              .findByIdAndScopeAndInstitution_Id(roleId, RoleScope.INSTITUTION, institutionId)
              .orElseThrow(RoleNotAssignableException::new);
      roles.put(role.getId(), role);
    }
    return roles;
  }

  private void ensureInstitutionalAuthorityIsUnchanged(
      List<PersonRoleAssignment> current, Map<UUID, Role> desiredRoles, boolean allowAuthority) {
    if (allowAuthority) {
      return;
    }

    Set<UUID> currentAuthorityIds =
        current.stream()
            .map(mappedPersonRoleAssignment -> mappedPersonRoleAssignment.getRole())
            .filter(mappedRole -> mappedRole.isInstitutionalAuthority())
            .map(mappedRole -> mappedRole.getId())
            .collect(Collectors.toSet());
    Set<UUID> desiredAuthorityIds =
        desiredRoles.values().stream()
            .filter(mappedRole -> mappedRole.isInstitutionalAuthority())
            .map(mappedRole -> mappedRole.getId())
            .collect(Collectors.toSet());

    if (!currentAuthorityIds.equals(desiredAuthorityIds)
        || (!currentAuthorityIds.isEmpty()
            && desiredRoles.values().stream().anyMatch(mappedRole -> mappedRole.isApplicant()))) {
      throw new InstitutionalAuthorityRoleImmutableException();
    }
  }

  private void removeApplicantWhenAnotherRoleIsSelected(Map<UUID, Role> roles) {
    boolean hasNonApplicant = roles.values().stream().anyMatch(role -> !role.isApplicant());
    if (hasNonApplicant) {
      roles.values().removeIf(mappedRole -> mappedRole.isApplicant());
    }
  }

  private void requirePermissionForChanges(
      Set<UUID> additions,
      Set<UUID> removals,
      boolean allowAuthority,
      Set<PermissionCode> actorPermissions) {
    if (allowAuthority) {
      return;
    }
    if (!additions.isEmpty()
        && !actorPermissions.contains(PermissionCode.INSTITUTION_ROLE_ASSIGN)) {
      throw new RoleAssignmentNotAllowedException();
    }
    if (!removals.isEmpty() && !actorPermissions.contains(PermissionCode.INSTITUTION_ROLE_REVOKE)) {
      throw new RoleRevocationNotAllowedException();
    }
  }
}
