package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.exceptions.InvalidRequestParameterException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.UnauthorizedGuardianshipException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.OperatingProfileResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

@Component
@RequiredArgsConstructor
public class OperatingContextResolver {
  private final PersonRepository people;
  private final PersonGuardianRepository links;
  private final PersonRoleAssignmentRepository roles;

  @Transactional(readOnly = true)
  public Person require(
      final UUID institutionId, final UUID selectedPersonId, final JwtAuthenticatedUser actor) {
    if (!actor.institutionId().equals(institutionId)) {
      throw new UnauthorizedGuardianshipException();
    }
    final var person =
        people
            .findByIdAndInstitution_Id(selectedPersonId, institutionId)
            .filter(value -> !value.isDeleted())
            .orElseThrow(UnauthorizedGuardianshipException::new);
    if (!selectedPersonId.equals(actor.personId())
        && !links.existsByInstitution_IdAndTutorPerson_IdAndDependentPerson_IdAndStatusIn(
            institutionId,
            actor.personId(),
            selectedPersonId,
            List.of(GuardianLinkStatus.ACTIVE))) {
      throw new UnauthorizedGuardianshipException();
    }
    return person;
  }

  @Transactional(readOnly = true)
  public OperatingProfileResponse profile(
      final UUID institutionId, final UUID personId, final JwtAuthenticatedUser actor) {
    final var person = require(institutionId, personId, actor);
    final var assignments = roles.findByPerson_IdAndInstitution_Id(personId, institutionId);
    final boolean canEnroll =
        assignments.stream()
            .anyMatch(a -> List.of("APPLICANT", "STUDENT").contains(a.getRole().getCode()));
    return new OperatingProfileResponse(
        actor.personId(),
        personId,
        person.getFirstName(),
        person.getLastName(),
        person.getDocumentNumber(),
        actor.personId().equals(personId),
        canEnroll,
        assignments.stream().map(a -> a.getRole().getName()).toList());
  }

  @Transactional(readOnly = true)
  public List<OperatingProfileResponse> profiles(
      final UUID institutionId, final JwtAuthenticatedUser actor) {
    final var result = new java.util.ArrayList<OperatingProfileResponse>();
    result.add(profile(institutionId, actor.personId(), actor));
    links
        .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
            institutionId, actor.personId(), List.of(GuardianLinkStatus.ACTIVE))
        .stream()
        .filter(link -> !link.getDependentPerson().isDeleted())
        .forEach(
            link -> result.add(profile(institutionId, link.getDependentPerson().getId(), actor)));
    return result;
  }

  /** Only explicit self-service routes select a different subject; the JWT is never changed. */
  public Optional<UUID> currentPersonId() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return Optional.empty();
    }
    final Object value =
        attributes.getRequest().getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(value instanceof Map<?, ?> variables)
        || !(variables.get("selectedPersonId") instanceof String selected)
        || !(variables.get("institutionId") instanceof String institution)) {
      return Optional.empty();
    }
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser actor)) {
      throw new UnauthorizedGuardianshipException();
    }
    final UUID institutionId;
    final UUID personId;
    try {
      institutionId = UUID.fromString(institution);
      personId = UUID.fromString(selected);
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestParameterException();
    }
    return Optional.of(require(institutionId, personId, actor).getId());
  }
}
