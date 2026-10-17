package ar.edu.utn.frvm.typeit.boero_api.audit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditAction {
  PERSON_CREATED(AuditModule.USERS, "Persona creada"),
  INSTITUTIONAL_ACCOUNT_CREATED(AuditModule.USERS, "Cuenta institucional creada"),
  PLATFORM_ACCOUNT_CREATED(AuditModule.USERS, "Administrador de plataforma creado"),
  INSTITUTIONAL_EMAIL_VERIFIED(AuditModule.USERS, "Correo institucional verificado"),
  PERSON_ROLES_CHANGED(AuditModule.ROLES, "Roles de persona modificados"),
  ROLE_CREATED(AuditModule.ROLES, "Rol creado"),
  ROLE_UPDATED(AuditModule.ROLES, "Rol actualizado"),
  ROLE_DELETED(AuditModule.ROLES, "Rol eliminado"),
  INSTITUTIONAL_ACCESS_CHANGED(AuditModule.USERS, "Acceso institucional modificado"),
  PLATFORM_ACCESS_CHANGED(AuditModule.USERS, "Acceso de plataforma modificado"),
  PERSON_OWN_ACCESS_INVITED(AuditModule.USERS, "Acceso propio invitado"),
  PERSON_OWN_ACCESS_INVITATION_CANCELLED(AuditModule.USERS, "Invitación de acceso cancelada"),
  PERSON_OWN_ACCESS_CHANGED(AuditModule.USERS, "Acceso propio modificado"),
  PERSON_OWN_ACCESS_ACTIVATED(AuditModule.USERS, "Acceso propio activado"),
  ENROLLMENT_APPLICATION_STARTED(AuditModule.ENROLLMENTS, "Inscripción iniciada"),
  ENROLLMENT_APPLICATION_DRAFT_UPDATED(
      AuditModule.ENROLLMENTS, "Borrador de inscripción actualizado"),
  ENROLLMENT_APPLICATION_SUBMITTED(AuditModule.ENROLLMENTS, "Inscripción enviada"),
  ENROLLMENT_APPLICATION_CANCELLED(AuditModule.ENROLLMENTS, "Inscripción cancelada"),
  // Kept so events recorded before links required validation stay readable.
  GUARDIAN_DEPENDENT_LINKED(AuditModule.GUARDIAN_LINKS, "Vinculación creada (histórico)"),
  GUARDIAN_LINK_REQUESTED(AuditModule.GUARDIAN_LINKS, "Vinculación solicitada"),
  GUARDIAN_LINK_APPROVED(AuditModule.GUARDIAN_LINKS, "Vinculación aprobada"),
  GUARDIAN_LINK_REJECTED(AuditModule.GUARDIAN_LINKS, "Vinculación rechazada"),
  GUARDIAN_DEPENDENT_UNLINKED(AuditModule.GUARDIAN_LINKS, "Vinculación eliminada"),
  INSTITUTION_CREATED(AuditModule.INSTITUTIONS, "Institución creada"),
  INSTITUTION_UPDATED(AuditModule.INSTITUTIONS, "Institución actualizada"),
  INSTITUTION_STATUS_CHANGED(AuditModule.INSTITUTIONS, "Estado de institución modificado"),
  INSTITUTION_PUBLIC_ACCESS_CHANGED(
      AuditModule.INSTITUTIONS, "Acceso público de institución modificado"),
  INSTITUTION_LOGO_CHANGED(AuditModule.INSTITUTIONS, "Logo de institución modificado"),
  COURSE_CREATED(AuditModule.COURSES, "Curso creado"),
  COURSE_CLASSES_CHANGED(AuditModule.COURSES, "Clases de curso actualizadas"),
  COURSE_STATUS_CHANGED(AuditModule.COURSES, "Estado de curso modificado"),
  COURSE_DELETED(AuditModule.COURSES, "Curso eliminado"),
  COURSE_RESTORED(AuditModule.COURSES, "Curso restaurado"),
  STUDY_PLAN_CREATED(AuditModule.STUDY_PLANS, "Plan de estudio creado"),
  STUDY_PLAN_UPDATED(AuditModule.STUDY_PLANS, "Plan de estudio actualizado"),
  STUDY_PLAN_STATUS_CHANGED(AuditModule.STUDY_PLANS, "Estado de plan de estudio modificado"),
  STUDY_PLAN_VERSION_CREATED(AuditModule.STUDY_PLANS, "Versión de plan de estudio creada"),
  STUDY_PLAN_DELETED(AuditModule.STUDY_PLANS, "Plan de estudio eliminado"),
  STUDY_PLAN_RESTORED(AuditModule.STUDY_PLANS, "Plan de estudio restaurado"),
  STUDY_PLAN_SPACE_CREATED(AuditModule.STUDY_PLANS, "Espacio de plan de estudio agregado"),
  STUDY_PLAN_LEVEL_CREATED(AuditModule.STUDY_PLANS, "Nivel de plan de estudio agregado"),
  STUDY_PLAN_LEVEL_UPDATED(AuditModule.STUDY_PLANS, "Nivel de plan de estudio actualizado"),
  STUDY_PLAN_LEVEL_DELETED(AuditModule.STUDY_PLANS, "Nivel de plan de estudio eliminado"),
  STUDY_PLAN_SPACE_UPDATED(AuditModule.STUDY_PLANS, "Espacio de plan de estudio actualizado"),
  STUDY_PLAN_SPACE_DELETED(AuditModule.STUDY_PLANS, "Espacio de plan de estudio eliminado"),
  STUDY_PLAN_PREREQUISITE_CREATED(
      AuditModule.STUDY_PLANS, "Correlatividad de plan de estudio agregada"),
  STUDY_PLAN_PREREQUISITE_UPDATED(
      AuditModule.STUDY_PLANS, "Correlatividad de plan de estudio actualizada"),
  STUDY_PLAN_PREREQUISITE_DELETED(
      AuditModule.STUDY_PLANS, "Correlatividad de plan de estudio eliminada"),
  DOCUMENT_DEFINITION_CREATED(AuditModule.DOCUMENTATION, "Documento del catálogo creado"),
  DOCUMENT_DEFINITION_UPDATED(AuditModule.DOCUMENTATION, "Documento del catálogo actualizado"),
  DOCUMENT_DEFINITION_TRANSFERRED(AuditModule.DOCUMENTATION, "Documento del catálogo transferido"),
  DOCUMENT_REQUIREMENTS_CHANGED(AuditModule.DOCUMENTATION, "Requisitos documentales modificados"),
  EMAIL_SENT(AuditModule.EMAILS, "Correo enviado"),
  EMAIL_FAILED(AuditModule.EMAILS, "Envío de correo fallido");

  private final AuditModule module;
  private final String label;
}
