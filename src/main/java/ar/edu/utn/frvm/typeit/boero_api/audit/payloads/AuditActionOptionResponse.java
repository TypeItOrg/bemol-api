package ar.edu.utn.frvm.typeit.boero_api.audit.payloads;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditModule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"action", "label", "module"})
public record AuditActionOptionResponse(AuditAction action, String label, AuditModule module) {}
