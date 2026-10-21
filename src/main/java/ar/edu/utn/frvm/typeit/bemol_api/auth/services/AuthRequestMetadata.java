package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import jakarta.servlet.http.HttpServletRequest;

final class AuthRequestMetadata {

  private AuthRequestMetadata() {}

  static String clientIp(HttpServletRequest request) {
    return request.getRemoteAddr();
  }
}
