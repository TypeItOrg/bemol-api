package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import static ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.AuthorizationMessages.SYSTEM_ROLE_NOT_SEEDED;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.boero_api.authorization.entities.Role;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignPersonSystemRoleUseCase {

  private final RoleRepository roleRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;
  private final SessionRevocationService sessionRevocationService;
  private final InstitutionRepository institutionRepository;
  private final AuthorizationCacheInvalidator authorizationCacheInvalidator;

  @Transactional
  public void execute(Person person, SystemRoleCode roleCode) {
    execute(person, roleCode, false);
  }

  @Transactional
  public void execute(Person person, SystemRoleCode roleCode, boolean revokeSessions) {
    execute(person, roleCode, revokeSessions, true);
  }

  @Transactional
  public void executePreservingRoles(Person person, SystemRoleCode roleCode) {
    execute(person, roleCode, false, false);
  }

  private void execute(
      Person person, SystemRoleCode roleCode, boolean revokeSessions, boolean applyRolePolicy) {
    Institution institution = person.getInstitution();
    institutionRepository
        .findByIdForUpdate(institution.getId())
        .orElseThrow(InstitutionNotFoundException::new);
    Role role =
        roleRepository
            .findByScopeAndCodeAndInstitution_Id(
                RoleScope.INSTITUTION, roleCode.name(), institution.getId())
            .orElseThrow(
                () -> new IllegalStateException(String.format(SYSTEM_ROLE_NOT_SEEDED, roleCode)));

    assign(person, role, roleCode, revokeSessions, applyRolePolicy);
    authorizationCacheInvalidator.evictPerson(person.getId(), institution.getId());
  }

  @Transactional
  public void execute(Person person, Role role, boolean revokeSessions) {
    institutionRepository
        .findByIdForUpdate(person.getInstitution().getId())
        .orElseThrow(InstitutionNotFoundException::new);
    @Nullable SystemRoleCode technicalCode =
        role.isSystem() ? SystemRoleCode.valueOf(role.getCode()) : null;
    assign(person, role, technicalCode, revokeSessions, true);
    authorizationCacheInvalidator.evictPerson(person.getId(), person.getInstitution().getId());
  }

  private void assign(
      Person person,
      Role role,
      @Nullable SystemRoleCode technicalCode,
      boolean revokeSessions,
      boolean applyRolePolicy) {
    Institution institution = person.getInstitution();
    List<PersonRoleAssignment> currentAssignments =
        personRoleAssignmentRepository.findByPerson_IdAndInstitution_Id(
            person.getId(), institution.getId());
    boolean alreadyAssigned =
        currentAssignments.stream()
            .anyMatch(assignment -> assignment.getRole().getId().equals(role.getId()));

    if (applyRolePolicy) {
      applyApplicantRolePolicy(currentAssignments, technicalCode);
    }
    if (alreadyAssigned) {
      return;
    }

    personRoleAssignmentRepository.save(PersonRoleAssignment.assign(person, role, institution));

    if (revokeSessions) {
      sessionRevocationService.revokeInstitutionalSessionsForPerson(
          person.getId(), institution.getId());
    }
  }

  private void applyApplicantRolePolicy(
      final List<PersonRoleAssignment> currentAssignments,
      final @Nullable SystemRoleCode roleCode) {
    for (final PersonRoleAssignment assignment : currentAssignments) {
      final boolean assignmentIsApplicant =
          assignment.getRole().getCode().equals(SystemRoleCode.APPLICANT.name());
      final boolean shouldRemove =
          roleCode == SystemRoleCode.APPLICANT ? !assignmentIsApplicant : assignmentIsApplicant;
      if (shouldRemove) {
        personRoleAssignmentRepository.delete(assignment);
      }
    }
  }
}
