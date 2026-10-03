package ar.edu.utn.frvm.typeit.boero_api.institutional.entities;

/** Lifecycle of a guardianship link: only {@code ACTIVE} lets the tutor represent the person. */
public enum GuardianLinkStatus {
  PENDING,
  ACTIVE,
  REJECTED,
  ENDED
}
