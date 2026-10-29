package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.InvalidCredentialsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.UserPayload;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.UserResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.InstitutionalCapabilities;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.PersonNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

  private final UserRepository userRepository;
  private final AuthorityResolver authorityResolver;
  private final PersonRepository people;

  @Transactional(readOnly = true)
  public UserResponse execute(JwtAuthenticatedUser principal) {
    if (principal.accountUserId().isEmpty()) {
      return withoutAccount(principal);
    }

    var user =
        userRepository
            .findWithPersonAndInstitutionById(principal.userId())
            .orElseThrow(InvalidCredentialsException::new);
    if (!user.isAccessEnabled() || (!principal.delegated() && !user.isEnabled())) {
      throw new DisabledException(AuthMessages.USER_DISABLED);
    }

    var authorities =
        authorityResolver.resolvePersonAuthorities(principal.personId(), principal.institutionId());

    var payload = UserPayload.from(user, principal.personId(), authorities);
    return withActor(principal, payload);
  }

  private UserResponse withoutAccount(final JwtAuthenticatedUser principal) {
    final var person =
        people
            .findByIdAndInstitution_Id(principal.personId(), principal.institutionId())
            .orElseThrow(PersonNotFoundException::new);
    final var access =
        authorityResolver.resolvePersonAuthorities(person.getId(), principal.institutionId());
    final var payload =
        UserPayload.builder()
            .personId(person.getId())
            .name(person.getFirstName())
            .lastName(person.getLastName())
            .documentNumber(person.getDocumentNumber())
            .institutionId(principal.institutionId())
            .roles(access.roles())
            .systemRoleCodes(access.systemRoleCodes())
            .capabilities(InstitutionalCapabilities.from(access))
            .permissions(
                access.permissions().stream()
                    .map(value -> value.getCode())
                    .collect(Collectors.toUnmodifiableSet()))
            .build();
    return withActor(principal, payload.withScopes(access.permissionScopes()));
  }

  private UserResponse withActor(final JwtAuthenticatedUser principal, final UserPayload payload) {
    final var permissions =
        principal.delegated()
            ? authorityResolver
                .resolvePersonAuthorities(principal.actorPersonId(), principal.institutionId())
                .permissionScopes()
                .entrySet()
                .stream()
                .collect(
                    Collectors.toMap(entry -> entry.getKey().getCode(), entry -> entry.getValue()))
            : payload.permissionScopes();
    final boolean canRequest =
        permissions
            .getOrDefault(
                ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode
                    .GUARDIAN_LINK_REQUEST
                    .getCode(),
                ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess.none())
            .institutional();
    return new UserResponse(
        payload.withActor(
            principal.actorPersonId(), principal.delegated(), canRequest, permissions.keySet()));
  }
}
