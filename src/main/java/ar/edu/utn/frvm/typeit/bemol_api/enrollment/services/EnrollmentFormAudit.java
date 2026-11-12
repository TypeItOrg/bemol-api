package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditChangeDetails;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.audit.snapshot.AuditSnapshot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class EnrollmentFormAudit {
  private final AuditEventRecorder recorder;

  public void form(final EnrollmentForm form, final AuditOperation action) {
    recorder.recordCurrentActor(
        form.getInstitution(),
        null,
        action,
        EnrollmentForm.AUDIT_TYPE,
        form.getId(),
        AuditOrigin.administration(),
        AuditChangeDetails.change(form.getName(), AuditSnapshot.empty(), form.auditSnapshot()));
  }

  public void version(final EnrollmentFormVersion version, final AuditOperation action) {
    recorder.recordCurrentActor(
        version.getInstitution(),
        null,
        action,
        EnrollmentFormVersion.AUDIT_TYPE,
        version.getId(),
        AuditOrigin.administration(),
        AuditChangeDetails.change(
            version.getForm().getName(), AuditSnapshot.empty(), version.auditSnapshot()));
  }

  public void assignment(
      final EnrollmentFormAssignment assignment,
      final AuditOperation action,
      final @Nullable UUID previousVersionId) {
    final var details =
        AuditChangeDetails.change(
            assignment.getFormVersion().getForm().getName(),
            AuditSnapshot.empty(),
            assignment.auditSnapshot());
    if (previousVersionId != null) {
      details.put("previousVersionId", previousVersionId);
    }
    recorder.recordCurrentActor(
        assignment.getInstitution(),
        null,
        action,
        EnrollmentFormAssignment.AUDIT_TYPE,
        assignment.getId(),
        AuditOrigin.administration(),
        details);
  }
}
