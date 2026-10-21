package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads;

import lombok.Builder;

@Builder
public record MonthlyInstitutionRegistrationResponse(int year, int month, long count) {}
