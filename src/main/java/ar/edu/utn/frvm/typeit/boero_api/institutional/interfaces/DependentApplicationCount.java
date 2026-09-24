package ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces;

import java.util.UUID;

/** Number of active enrollment applications whose applicant is {@code personId}. */
public record DependentApplicationCount(UUID personId, long total) {}
