package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PlatformSessionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IsPlatformSessionActiveUseCase {

  private final PlatformSessionRepository platformSessionRepository;

  @org.springframework.cache.annotation.Cacheable(
      value = "activePlatformSessions",
      key = "#sessionId")
  public boolean execute(UUID sessionId) {
    return platformSessionRepository.existsByIdAndActiveTrue(sessionId);
  }
}
