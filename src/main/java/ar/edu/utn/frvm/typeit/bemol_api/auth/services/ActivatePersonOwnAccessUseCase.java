package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.ActivateOwnAccessRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivatePersonOwnAccessUseCase {
  private final InstitutionRepository institutions;
  private final UserRepository users;
  private final PersonAccessInvitationRepository invitations;
  private final InstitutionalHostContext hostContext;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final AuditEventRecorder audit;
  private final EntityManager entityManager;
  private final PersonOwnAccessProvisioning provisioning;

  @Transactional
  public void execute(final ActivateOwnAccessRequest request) {
    final String hash = RequestInstitutionalPasswordRecoveryUseCase.hash(request.token());
    final var invitation =
        invitations
            .findByTokenHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    final var institution = invitation.getPerson().getInstitution();
    hostContext.requireInstitution(institution.getId());
    institutions
        .findByIdForUpdate(institution.getId())
        .orElseThrow(InstitutionNotFoundException::new);
    // Refresh after the shared lock so cancellation, replay and revocation cannot use stale state.
    final var current =
        invitations
            .findByTokenHash(hash)
            .orElseThrow(
                () -> new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID));
    entityManager.refresh(current);
    final var representation = current.getRepresentation();
    if (representation != null) {
      entityManager.refresh(representation);
    }
    entityManager.refresh(current.getPerson(), LockModeType.PESSIMISTIC_WRITE);
    entityManager.refresh(institution);
    if (!current.isUsableAt(clock.instant())
        || (!current.isInstitutional() && !current.hasActiveRepresentation())
        || current.getPerson().isDeleted()
        || !institution.isActive()
        || (current.getPerson().getEmail() != null
            && !current.getPerson().getEmail().equalsIgnoreCase(current.getEmail()))
        || users
            .findByPerson_IdAndInstitution_Id(current.getPerson().getId(), institution.getId())
            .isPresent()) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    final var issuer = current.getInstitutionalIssuer();
    if (issuer != null) {
      entityManager.refresh(issuer);
      if (!provisioning.canProvisionInstitutionally(issuer, current.getPerson())) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
      }
    } else if (provisioning.requiresInstitutionalAccountCreation(current.getPerson())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_INSTITUTION_REQUIRED);
    }
    if (!request.password().equals(request.confirmPassword())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_PASSWORD_MISMATCH);
    }
    if (current.getPerson().getEmail() == null) {
      current.getPerson().updateContact(current.getEmail(), current.getPerson().getPhoneNumber());
    }
    users.save(
        User.builder()
            .person(current.getPerson())
            .institution(institution)
            .password(requireNonNull(passwords.encode(request.password())))
            .emailVerificationStatus(EmailVerificationStatus.VERIFIED)
            .emailVerifiedAt(clock.instant())
            .build());
    current.consume(clock.instant());
    audit.recordCurrentActor(
        institution,
        current.getPerson().getId(),
        AuditAction.PERSON_OWN_ACCESS_ACTIVATED,
        current.isInstitutional()
            ? AuditEntityType.PERSON_ACCESS_INVITATION
            : AuditEntityType.PERSON_GUARDIAN,
        current.isInstitutional()
            ? current.getId()
            : java.util.Objects.requireNonNull(representation).getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
  }
}
