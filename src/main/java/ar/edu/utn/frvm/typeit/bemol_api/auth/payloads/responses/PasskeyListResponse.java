package ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public record PasskeyListResponse(List<PasskeyResponse> passkeys, int maxActivePasskeys) {}
