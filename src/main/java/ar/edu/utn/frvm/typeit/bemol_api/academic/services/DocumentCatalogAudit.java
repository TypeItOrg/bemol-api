package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;

final class DocumentCatalogAudit {
  private DocumentCatalogAudit() {}

  static AcademicAuditAction action(
      final boolean created,
      final boolean transferring,
      final AuditSnapshot.Snapshot before,
      final AuditSnapshot.Snapshot after) {
    if (created) {
      return AcademicAuditAction.DOCUMENT_DEFINITION_CREATED;
    }
    if (transferring) {
      return AcademicAuditAction.DOCUMENT_DEFINITION_TRANSFERRED;
    }
    if (DocumentDefinition.definitionChanged(before, after)) {
      return AcademicAuditAction.DOCUMENT_DEFINITION_UPDATED;
    }
    return AcademicAuditAction.DOCUMENT_REQUIREMENTS_CHANGED;
  }

  static void record(
      final AuditEventRecorder recorder,
      final DocumentDefinition document,
      final AcademicAuditAction action,
      final AuditSnapshot.Snapshot before,
      final AuditSnapshot.Snapshot after) {
    recorder.recordSnapshotChange(
        document.getInstitution(),
        null,
        action,
        DocumentDefinition.AUDIT_TYPE,
        document.getId(),
        document.getName(),
        before,
        after);
  }
}
