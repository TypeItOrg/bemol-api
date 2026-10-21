package ar.edu.utn.frvm.typeit.bemol_api.auth.filters;

import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.AuthMessages;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.AccountType;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.OwnAccessException;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record JwtAuthenticatedUser(
    @Nullable UUID userId,
    UUID personId,
    String documentNumber,
    UUID institutionId,
    UUID sessionId,
    String tokenId,
    @Nullable UUID authenticatedUserId,
    @Nullable UUID authenticatedPersonId)
    implements JwtPrincipal {

  public JwtAuthenticatedUser(
      UUID userId,
      UUID personId,
      String documentNumber,
      UUID institutionId,
      UUID sessionId,
      String tokenId) {
    this(userId, personId, documentNumber, institutionId, sessionId, tokenId, null, null);
  }

  public UUID userId() {
    if (userId == null) {
      throw new OwnAccessException(AuthMessages.ACTIVE_IDENTITY_WITHOUT_ACCOUNT);
    }
    return userId;
  }

  public Optional<UUID> accountUserId() {
    return Optional.ofNullable(userId);
  }

  public UUID actorUserId() {
    return authenticatedUserId == null ? userId() : authenticatedUserId;
  }

  public UUID actorPersonId() {
    return authenticatedPersonId == null ? personId : authenticatedPersonId;
  }

  public boolean delegated() {
    return !actorPersonId().equals(personId);
  }

  @Override
  public AccountType accountType() {
    return AccountType.INSTITUTION;
  }

  @Override
  public String getName() {
    return documentNumber;
  }
}
