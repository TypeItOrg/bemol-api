package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionPersonResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalAuthorityContinuityGuard;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletePersonUseCase {

  private final InstitutionPersonResolver institutionPersonResolver;
  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final SessionRevocationService sessionRevocationService;
  private final InstitutionRepository institutionRepository;
  private final InstitutionalAuthorityContinuityGuard authorityContinuity;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public void execute(final UUID institutionId, final UUID personId) {
    delete(institutionId, personId, false);
  }

  @Transactional
  public void executeForPlatformAdministration(final UUID institutionId, final UUID personId) {
    delete(institutionId, personId, true);
  }

  private void delete(
      final UUID institutionId, final UUID personId, final boolean allowAuthorityRemoval) {
    institutionRepository
        .findByIdForUpdate(institutionId)
        .orElseThrow(InstitutionNotFoundException::new);
    final Person person =
        institutionPersonResolver.requirePersonInInstitutionForUpdate(institutionId, personId);
    if (person.isDeleted()) {
      return;
    }

    if (!allowAuthorityRemoval) {
      authorityContinuity.requireOtherAuthorityBeforeDeletion(person);
    }
    person.delete();

    personRepository.save(person);
    auditEventRecorder.recordCurrentActor(
        person.getInstitution(),
        personId,
        InstitutionalAuditAction.PERSON_DELETED,
        Person.AUDIT_TYPE,
        personId,
        AuditOrigin.administration(),
        AuditChangeDetails.lifecycle("Persona", true));
    userRepository
        .findByPersonAndInstitutionForUpdate(personId, institutionId)
        .ifPresent(
            user -> {
              final var before = user.auditSnapshot();
              if (user.updateAccess(false)) {
                userRepository.save(user);
                auditEventRecorder.recordCurrentActor(
                    user.getInstitution(),
                    personId,
                    AuthAuditAction.INSTITUTIONAL_ACCESS_CHANGED,
                    User.AUDIT_TYPE,
                    user.getId(),
                    AuditOrigin.administration(),
                    AuditChangeDetails.change(
                        "Cuenta institucional", before, user.auditSnapshot()));
              }
            });

    sessionRevocationService.revokeInstitutionalSessionsForPerson(personId, institutionId);
  }
}
