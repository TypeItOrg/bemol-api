package ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit;

import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditModuleDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperation;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditOperationsProvider;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseEnrollment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentAttachment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPeriod;
import java.util.List;
import org.springframework.stereotype.Component;

public enum EnrollmentAuditAction implements AuditOperation {
  ENROLLMENT_APPLICATION_STARTED(Modules.ENROLLMENTS, "Inscripción iniciada"),
  ENROLLMENT_APPLICATION_DRAFT_UPDATED(Modules.ENROLLMENTS, "Borrador de inscripción actualizado"),
  ENROLLMENT_APPLICATION_SUBMITTED(Modules.ENROLLMENTS, "Inscripción enviada"),
  ENROLLMENT_APPLICATION_CANCELLED(Modules.ENROLLMENTS, "Inscripción cancelada"),
  ENROLLMENT_FORM_CREATED(Modules.ENROLLMENTS, "Formulario de inscripción creado"),
  ENROLLMENT_FORM_UPDATED(Modules.ENROLLMENTS, "Formulario de inscripción actualizado"),
  ENROLLMENT_FORM_VERSION_CREATED(Modules.ENROLLMENTS, "Versión de formulario creada"),
  ENROLLMENT_FORM_VERSION_UPDATED(Modules.ENROLLMENTS, "Borrador de formulario actualizado"),
  ENROLLMENT_FORM_VERSION_PUBLISHED(Modules.ENROLLMENTS, "Versión de formulario publicada"),
  ENROLLMENT_FORM_VERSION_ARCHIVED(Modules.ENROLLMENTS, "Versión de formulario archivada"),
  ENROLLMENT_FORM_ASSIGNED(Modules.ENROLLMENTS, "Formulario de inscripción asignado"),
  ENROLLMENT_FORM_UNASSIGNED(Modules.ENROLLMENTS, "Asignación de formulario retirada"),
  ENROLLMENT_PERIOD_CREATED(Modules.ENROLLMENT_PERIODS, "Período de inscripción creado"),
  ENROLLMENT_PERIOD_UPDATED(Modules.ENROLLMENT_PERIODS, "Período de inscripción actualizado"),
  ENROLLMENT_PERIOD_STATUS_CHANGED(Modules.ENROLLMENT_PERIODS, "Estado de período modificado"),
  ENROLLMENT_PERIOD_DELETED(Modules.ENROLLMENT_PERIODS, "Período de inscripción eliminado"),
  ENROLLMENT_APPLICATION_APPROVED(Modules.ENROLLMENTS, "Inscripción aprobada"),
  ENROLLMENT_APPLICATION_APPROVED_PROVISIONALLY(
      Modules.ENROLLMENTS, "Inscripción aprobada provisoriamente"),
  ENROLLMENT_APPLICATION_REJECTED(Modules.ENROLLMENTS, "Inscripción rechazada"),
  ENROLLMENT_DOCUMENTS_REQUESTED(Modules.ENROLLMENTS, "Documentación adicional solicitada"),
  ENROLLMENT_APPLICATION_COURSE_WAITLISTED(
      Modules.ENROLLMENTS, "Selección de curso en lista de espera"),
  ENROLLMENT_APPLICATION_COURSE_REJECTED(Modules.ENROLLMENTS, "Opción de curso rechazada"),
  COURSE_ENROLLMENT_CREATED(Modules.COURSE_ENROLLMENTS, "Cursada creada"),
  COURSE_ENROLLMENT_STATUS_CHANGED(Modules.COURSE_ENROLLMENTS, "Estado de cursada modificado"),
  COURSE_ENROLLMENT_ACADEMIC_STATUS_CHANGED(
      Modules.COURSE_ENROLLMENTS, "Situación académica de cursada modificada"),
  ENROLLMENT_ATTACHMENT_UPLOADED(Modules.DOCUMENTATION, "Adjunto de inscripción cargado"),
  ENROLLMENT_ATTACHMENT_REPLACED(Modules.DOCUMENTATION, "Adjunto de inscripción reemplazado"),
  ENROLLMENT_ATTACHMENT_DELETED(Modules.DOCUMENTATION, "Adjunto de inscripción eliminado"),
  ENROLLMENT_ATTACHMENT_REVIEWED(Modules.DOCUMENTATION, "Adjunto de inscripción revisado");

  private final AuditModuleDefinition module;
  private final String label;

  EnrollmentAuditAction(final AuditModuleDefinition module, final String label) {
    this.module = module;
    this.label = label;
  }

  @Override
  public AuditModuleDefinition getModule() {
    return module;
  }

  @Override
  public String getLabel() {
    return label;
  }

  private static final class Modules {
    static final AuditModuleDefinition ENROLLMENTS =
        new AuditModuleDefinition("ENROLLMENTS", "Inscripciones");
    static final AuditModuleDefinition ENROLLMENT_PERIODS =
        new AuditModuleDefinition("ENROLLMENT_PERIODS", "Períodos de inscripción");
    static final AuditModuleDefinition COURSE_ENROLLMENTS =
        new AuditModuleDefinition("COURSE_ENROLLMENTS", "Cursadas");
    static final AuditModuleDefinition DOCUMENTATION =
        new AuditModuleDefinition("DOCUMENTATION", "Documentación");
  }

  @Component
  public static class Provider implements AuditOperationsProvider {
    @Override
    public List<EnrollmentAuditAction> operations() {
      return List.of(values());
    }

    @Override
    public List<AuditResource> resources() {
      return List.of(
          EnrollmentPeriod.AUDIT_TYPE,
          CourseEnrollment.AUDIT_TYPE,
          EnrollmentAttachment.AUDIT_TYPE,
          EnrollmentApplication.AUDIT_TYPE,
          EnrollmentForm.AUDIT_TYPE,
          EnrollmentFormVersion.AUDIT_TYPE,
          EnrollmentFormAssignment.AUDIT_TYPE);
    }
  }
}
