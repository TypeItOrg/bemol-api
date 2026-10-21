package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserSessionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.ActiveSessionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetActiveSessionsUseCase {

  private final UserSessionRepository userSessionRepository;

  public PaginatedResponse<ActiveSessionResponse> execute(
      JwtAuthenticatedUser current, Pageable pageable) {
    var sessions = userSessionRepository.findByUserIdAndActive(current.userId(), true, pageable);
    return PaginatedResponse.from(sessions.map(s -> toResponse(s, current)));
  }

  private static ActiveSessionResponse toResponse(
      UserSession session, JwtAuthenticatedUser current) {
    return ActiveSessionResponse.builder()
        .sessionId(session.getId())
        .ipAddress(session.getIpAddress())
        .userAgent(session.getUserAgent())
        .startedAt(session.getStartedAt())
        .currentSession(session.getId().equals(current.sessionId()))
        .build();
  }
}
