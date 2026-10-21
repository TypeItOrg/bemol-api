package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"status"})
public record OwnAccessResponse(String status) {}
