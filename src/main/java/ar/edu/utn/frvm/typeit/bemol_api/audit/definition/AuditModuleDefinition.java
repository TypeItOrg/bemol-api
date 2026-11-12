package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"code", "label"})
public record AuditModuleDefinition(String code, String label) {}
