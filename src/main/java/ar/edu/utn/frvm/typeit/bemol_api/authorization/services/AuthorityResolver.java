package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorityResolver {

  private final CachedAuthoritySnapshotResolver snapshotResolver;

  public Set<PermissionCode> resolveForPerson(UUID personId, UUID institutionId) {
    return snapshotResolver.resolveForPerson(personId, institutionId).permissions();
  }

  public Set<PermissionCode> resolveForPlatformAccount(UUID platformAccountId) {
    return snapshotResolver.resolveForPlatformAccount(platformAccountId).permissions();
  }

  public Set<PlatformRoleCode> resolvePlatformRoles(UUID platformAccountId) {
    return snapshotResolver.resolveForPlatformAccount(platformAccountId).roles();
  }

  public InstitutionalAuthoritySnapshot resolvePersonAuthorities(
      UUID personId, UUID institutionId) {
    return snapshotResolver.resolveForPerson(personId, institutionId);
  }
}
