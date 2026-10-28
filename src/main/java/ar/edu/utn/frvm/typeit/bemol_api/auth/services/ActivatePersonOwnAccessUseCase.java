package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
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
  private final AccountAccessAuthorization access;

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
        || current.getPerson().isDeleted()
        || !institution.isActive()
        || !java.util.Objects.equals(current.getPerson().getEmail(), current.getExpectedEmail())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    if (current.isInstitutional()) {
      final var issuer = current.getInstitutionalIssuer();
      final var platform = current.getPlatformIssuer();
      if (issuer != null) {
        entityManager.refresh(issuer);
        if (!access.canProvisionInstitutionally(issuer, current.getPerson())) {
          throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
        }
      } else if (platform == null || !access.isPlatformAdministrator(platform.getId())) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
      }
    } else {
      if (!current.hasActiveRepresentation()
          || provisioning.requiresInstitutionalAccountCreation(current.getPerson())
          || !access.canManageInstitutionally(
              java.util.Objects.requireNonNull(representation).getTutorPerson().getId(),
              current.getPerson())) {
        throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
      }
    }
    if (!request.password().equals(request.confirmPassword())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_PASSWORD_MISMATCH);
    }
    final var existing =
        users.findByPerson_IdAndInstitution_Id(current.getPerson().getId(), institution.getId());
    if (existing.isPresent()
        && current.getPurpose()
            != ar.edu.utn.frvm.typeit.bemol_api.auth.enums.AccessInvitationPurpose.AUTONOMY) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_TOKEN_INVALID);
    }
    if (existing.isPresent()
        && passwords.matches(request.password(), existing.get().getPassword())) {
      throw new OwnAccessException(InstitutionMessages.OWN_ACCESS_NEW_PASSWORD_REQUIRED);
    }
    current.getPerson().updateContact(current.getEmail(), current.getPerson().getPhoneNumber());
    final var user =
        existing.orElseGet(
            () -> User.builder().person(current.getPerson()).institution(institution).build());
    user.activateOwnAccess(requireNonNull(passwords.encode(request.password())), clock.instant());
    users.saveAndFlush(user);
    if (existing.isEmpty()) {
      audit.recordCurrentActor(
          institution,
          current.getPerson().getId(),
          AuditAction.INSTITUTIONAL_ACCOUNT_CREATED,
          AuditEntityType.USER,
          user.getId(),
          AuditOrigin.EMAIL_VERIFICATION,
          Map.of("created", true));
    }
    current.consume(clock.instant());
    audit.recordCurrentActor(
        institution,
        current.getPerson().getId(),
        AuditAction.PERSON_OWN_ACCESS_ACTIVATED,
        AuditEntityType.PERSON_ACCESS_INVITATION,
        current.getId(),
        AuditOrigin.SELF_SERVICE,
        Map.of());
  }
}
