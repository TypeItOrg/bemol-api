package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

public enum AuthRealm {
  INSTITUTIONAL("institutional", "activeSessions"),
  PLATFORM("platform", "activePlatformSessions");

  private final String replayScope;
  private final String activeSessionsCache;

  AuthRealm(final String replayScope, final String activeSessionsCache) {
    this.replayScope = replayScope;
    this.activeSessionsCache = activeSessionsCache;
  }

  public String replayScope() {
    return replayScope;
  }

  public String activeSessionsCache() {
    return activeSessionsCache;
  }
}
