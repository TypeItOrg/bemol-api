package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActiveIdentityService {
  private final UserSessionRepository sessions;
  private final UserRepository users;
  private final OperatingContextResolver contexts;

  @Transactional(readOnly = true)
  public JwtAuthenticatedUser resolve(final JwtAuthenticatedUser actor) {
    final var session =
        sessions
            .findByIdAndUserId(actor.sessionId(), actor.actorUserId())
            .filter(value -> value.isActive())
            .orElseThrow(InvalidCredentialsException::new);
    final UUID selected = session.getActivePersonId();
    if (selected == null || selected.equals(actor.actorPersonId())) {
      return actor;
    }

    final Person person = contexts.requireAccessible(actor.institutionId(), selected, actor);
    final var user = users.findByPerson_IdAndInstitution_Id(selected, actor.institutionId());
    if (user.isPresent() && !user.get().isAccessEnabled()) {
      throw new UnauthorizedGuardianshipException();
    }

    return JwtAuthenticatedUser.builder()
        .userId(user.map(value -> value.getId()).orElse(null))
        .personId(selected)
        .documentNumber(person.getDocumentNumber())
        .institutionId(actor.institutionId())
        .sessionId(actor.sessionId())
        .tokenId(actor.tokenId())
        .authenticatedUserId(actor.actorUserId())
        .authenticatedPersonId(actor.actorPersonId())
        .build();
  }

  @Transactional
  public void select(final JwtAuthenticatedUser actor, final @Nullable UUID personId) {
    final UUID selected = personId == null ? actor.actorPersonId() : personId;
    contexts.requireAccessible(actor.institutionId(), selected, actor);
    final var user = users.findByPerson_IdAndInstitution_Id(selected, actor.institutionId());
    if (user.isPresent() && !user.get().isAccessEnabled()) {
      throw new UnauthorizedGuardianshipException();
    }

    final var session =
        sessions
            .findByIdAndUserId(actor.sessionId(), actor.actorUserId())
            .filter(value -> value.isActive())
            .orElseThrow(InvalidCredentialsException::new);
    session.selectIdentity(selected.equals(actor.actorPersonId()) ? null : selected);
  }
}
