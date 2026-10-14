package ar.edu.utn.frvm.typeit.boero_api.authorization.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.cache.AuthorizationCacheKeys;
import ar.edu.utn.frvm.typeit.boero_api.authorization.cache.AuthorizationCacheNames;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AuthorizationCacheInvalidator {

  private final CacheManager cacheManager;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  public void evictPerson(UUID personId, UUID institutionId) {
    afterCommit(
        () ->
            evict(
                AuthorizationCacheNames.PERSON_AUTHORITIES,
                AuthorizationCacheKeys.person(personId, institutionId)));
  }

  public void evictPlatformAccount(UUID platformAccountId) {
    afterCommit(
        () ->
            evict(
                AuthorizationCacheNames.PLATFORM_AUTHORITIES,
                AuthorizationCacheKeys.platformAccount(platformAccountId)));
  }

  public void evictPeopleForRole(UUID roleId, UUID institutionId) {
    var personIds =
        personRoleAssignmentRepository.findPersonIdsByRoleIdAndInstitutionId(roleId, institutionId);
    afterCommit(
        () ->
            personIds.forEach(
                personId ->
                    evict(
                        AuthorizationCacheNames.PERSON_AUTHORITIES,
                        AuthorizationCacheKeys.person(personId, institutionId))));
  }

  private void evict(String cacheName, Object key) {
    var cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.evict(key);
    }
  }

  private void afterCommit(Runnable eviction) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      eviction.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            eviction.run();
          }
        });
  }
}
