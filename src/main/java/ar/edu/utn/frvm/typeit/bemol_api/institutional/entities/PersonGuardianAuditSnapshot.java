package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import java.util.Map;

final class PersonGuardianAuditSnapshot {
  private PersonGuardianAuditSnapshot() {}

  static AuditSnapshot.Snapshot of(final PersonGuardian guardian) {
    final String statusLabel =
        switch (guardian.getStatus()) {
          case PENDING -> "Pendiente";
          case ACTIVE -> "Activa";
          case REJECTED -> "Rechazada";
          case ENDED -> "Finalizada";
        };
    final String relationshipLabel =
        switch (guardian.getRelationship()) {
          case MOTHER -> "Madre";
          case FATHER -> "Padre";
          case LEGAL_GUARDIAN -> "Tutor legal";
          case OTHER -> "Otro vínculo";
        };
    return AuditSnapshot.fields()
        .expose(
            "status",
            "Estado de vinculación",
            guardian.getStatus().name(),
            Map.of("code", guardian.getStatus().name(), "label", statusLabel))
        .expose(
            "relationship",
            "Vínculo",
            guardian.getRelationship().name(),
            Map.of("code", guardian.getRelationship().name(), "label", relationshipLabel))
        .expose(
            "tutorPersonId", "Identificador del representante", guardian.getTutorPerson().getId())
        .expose(
            "dependentPersonId",
            "Identificador de la persona representada",
            guardian.getDependentPersonId())
        .expose("ownAccessRequested", "Acceso propio solicitado", guardian.isOwnAccessRequested())
        .expose("primaryContact", "Contacto principal", guardian.isPrimaryContact())
        .build();
  }
}
