package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import lombok.Builder;

@Builder
public record TokenResponse(String accessToken, String refreshToken) {}
