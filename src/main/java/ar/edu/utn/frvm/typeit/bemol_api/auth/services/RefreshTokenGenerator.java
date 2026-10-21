package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenGenerator {

  public String newFamilyId() {
    return UUID.randomUUID().toString();
  }

  public GeneratedRefreshToken generate() {
    final String rawToken = UUID.randomUUID().toString();
    return new GeneratedRefreshToken(rawToken, JwtService.hashToken(rawToken));
  }
}
