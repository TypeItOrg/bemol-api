package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import ar.edu.utn.frvm.typeit.boero_api.auth.entities.PlatformSession;
import ar.edu.utn.frvm.typeit.boero_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformRefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.PlatformSessionRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.RefreshTokenRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.boero_api.auth.interfaces.UserSessionRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {
  private final Clock clock;

  private final UserRepository userRepository;
  private final UserSessionRepository userSessionRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PlatformSessionRepository platformSessionRepository;
  private final PlatformRefreshTokenRepository platformRefreshTokenRepository;
  private final CacheManager cacheManager;

  @Transactional
  public void revokeInstitutionalSessionsForPerson(final UUID personId, final UUID institutionId) {
    userRepository
        .findByPerson_IdAndInstitution_Id(personId, institutionId)
        .ifPresent(user -> revokeInstitutionalSessionsForUser(user.getId()));
  }

  @Transactional
  public void revokeInstitutionalSessionsForUser(final UUID userId) {
    deactivateInstitutionalSessions(userSessionRepository.findByUserIdAndActive(userId, true));
  }

  @Transactional
  public void revokeInstitutionalSessionsForInstitution(final UUID institutionId) {
    deactivateInstitutionalSessions(userSessionRepository.findActiveByInstitutionId(institutionId));
  }

  @Transactional
  public void revokePlatformAccountSessions(final UUID platformAccountId) {
    deactivatePlatformSessions(
        platformSessionRepository.findByPlatformAccountIdAndActive(platformAccountId, true));
  }

  @Transactional
  public void revokeInstitutionalSessionsByIds(final Collection<UUID> sessionIds) {
    if (sessionIds.isEmpty()) {
      return;
    }

    refreshTokenRepository.revokeBySessionIds(sessionIds);
    userSessionRepository.deactivateByIds(sessionIds, clock.instant());
    evictSessions(AuthRealm.INSTITUTIONAL, sessionIds);
  }

  @Transactional
  public void revokePlatformSessionsByIds(final Collection<UUID> sessionIds) {
    if (sessionIds.isEmpty()) {
      return;
    }

    platformRefreshTokenRepository.revokeByPlatformSessionIds(sessionIds);
    platformSessionRepository.deactivateByIds(sessionIds, clock.instant());
    evictSessions(AuthRealm.PLATFORM, sessionIds);
  }

  private void deactivateInstitutionalSessions(final List<UserSession> sessions) {
    if (sessions.isEmpty()) {
      return;
    }

    revokeInstitutionalSessionsByIds(sessions.stream().map(session -> session.getId()).toList());
  }

  private void deactivatePlatformSessions(final List<PlatformSession> sessions) {
    if (sessions.isEmpty()) {
      return;
    }

    revokePlatformSessionsByIds(sessions.stream().map(session -> session.getId()).toList());
  }

  private void evictSessions(final AuthRealm realm, final Collection<UUID> sessionIds) {
    final var ids = List.copyOf(sessionIds);
    Runnable evict =
        () -> {
          final var cache = cacheManager.getCache(realm.activeSessionsCache());
          if (cache != null) {
            ids.forEach(cache::evict);
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              evict.run();
            }
          });
    } else {
      evict.run();
    }
  }
}
