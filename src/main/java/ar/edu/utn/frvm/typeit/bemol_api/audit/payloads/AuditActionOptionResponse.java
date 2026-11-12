package ar.edu.utn.frvm.typeit.bemol_api.audit.payloads;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"action", "label", "module", "moduleLabel"})
public record AuditActionOptionResponse(
    String action, String label, String module, String moduleLabel) {}
