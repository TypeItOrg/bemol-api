package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"revision", "changes"})
public record AuditHistoryResponse(long revision, List<AuditEntityChangeResponse> changes) {}
