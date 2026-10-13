package ar.edu.utn.frvm.typeit.boero_api.authorization.enums;

import ar.edu.utn.frvm.typeit.boero_api.authorization.exceptions.AuthorizationMessages;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermissionCode {
  INSTITUTION_PERSON_READ_ANY(
      "institution:person:read-any",
      PermissionScope.INSTITUTION,
      PermissionGroup.PEOPLE,
      "Ver usuarios"),
  INSTITUTION_PERSON_CREATE(
      "institution:person:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.PEOPLE,
      "Crear usuarios"),
  INSTITUTION_PERSON_UPDATE_ANY(
      "institution:person:update-any",
      PermissionScope.INSTITUTION,
      PermissionGroup.PEOPLE,
      "Editar usuarios"),
  INSTITUTION_PERSON_DELETE(
      "institution:person:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.PEOPLE,
      "Eliminar usuarios"),
  INSTITUTION_USER_STATUS_UPDATE(
      "institution:users:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.PEOPLE,
      "Activar o desactivar usuarios"),
  INSTITUTION_ROLE_ASSIGN(
      "institution:roles:assign",
      PermissionScope.INSTITUTION,
      PermissionGroup.ROLES,
      "Asignar roles"),
  INSTITUTION_ROLE_REVOKE(
      "institution:roles:revoke",
      PermissionScope.INSTITUTION,
      PermissionGroup.ROLES,
      "Revocar roles"),
  INSTITUTION_ROLE_READ(
      "institution:roles:read", PermissionScope.INSTITUTION, PermissionGroup.ROLES, "Ver roles"),
  INSTITUTION_ROLE_CREATE(
      "institution:roles:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ROLES,
      "Crear roles"),
  INSTITUTION_ROLE_UPDATE(
      "institution:roles:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ROLES,
      "Actualizar roles"),
  INSTITUTION_ROLE_DELETE(
      "institution:roles:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ROLES,
      "Eliminar roles"),
  INSTITUTION_GRADES_ENTER(
      "institution:grades:enter",
      PermissionScope.INSTITUTION,
      PermissionGroup.GRADES,
      "Cargar calificaciones"),
  INSTITUTION_GRADES_ENTER_FINAL(
      "institution:grades:enter-final",
      PermissionScope.INSTITUTION,
      PermissionGroup.GRADES,
      "Cargar calificaciones finales"),
  INSTITUTION_READ(
      "institution:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.INSTITUTION,
      "Ver detalles de la institución"),
  INSTITUTION_UPDATE(
      "institution:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.INSTITUTION,
      "Editar detalles de la institución"),
  ACADEMIC_YEAR_READ(
      "institution:academic-year:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver ciclos lectivos"),
  ACADEMIC_YEAR_CREATE(
      "institution:academic-year:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear ciclos lectivos"),
  ACADEMIC_YEAR_UPDATE(
      "institution:academic-year:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar ciclos lectivos"),
  ACADEMIC_YEAR_STATUS_UPDATE(
      "institution:academic-year:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Cambiar estado de ciclos lectivos"),
  ACADEMIC_YEAR_DELETE(
      "institution:academic-year:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar ciclos lectivos"),
  ACADEMIC_YEAR_RESTORE(
      "institution:academic-year:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar ciclos lectivos"),
  DOCUMENT_CATALOG_READ(
      "institution:document-catalog:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver catálogo documental"),
  DOCUMENT_CATALOG_MANAGE(
      "institution:document-catalog:manage",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Administrar catálogo documental"),
  TRAINING_PATH_READ(
      "institution:training-path:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver trayectos formativos"),
  TRAINING_PATH_CREATE(
      "institution:training-path:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear trayectos formativos"),
  TRAINING_PATH_UPDATE(
      "institution:training-path:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar trayectos formativos"),
  TRAINING_PATH_STATUS_UPDATE(
      "institution:training-path:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar trayectos formativos"),
  TRAINING_PATH_DELETE(
      "institution:training-path:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar trayectos formativos"),
  TRAINING_PATH_RESTORE(
      "institution:training-path:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar trayectos formativos"),
  STUDY_PLAN_READ(
      "institution:study-plan:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver planes de estudio"),
  STUDY_PLAN_CREATE(
      "institution:study-plan:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear planes de estudio"),
  STUDY_PLAN_UPDATE(
      "institution:study-plan:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar planes de estudio"),
  STUDY_PLAN_STATUS_UPDATE(
      "institution:study-plan:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar planes de estudio"),
  STUDY_PLAN_CURRICULUM_UPDATE(
      "institution:study-plan:curriculum-update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar estructura curricular"),
  STUDY_PLAN_DELETE(
      "institution:study-plan:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar planes de estudio"),
  STUDY_PLAN_RESTORE(
      "institution:study-plan:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar planes de estudio"),
  ACADEMIC_OFFER_READ(
      "institution:academic-offer:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Consultar oferta académica disponible"),
  ACADEMIC_SPACE_READ(
      "institution:academic-space:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver espacios académicos"),
  ACADEMIC_SPACE_CREATE(
      "institution:academic-space:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear espacios académicos"),
  ACADEMIC_SPACE_UPDATE(
      "institution:academic-space:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar espacios académicos"),
  ACADEMIC_SPACE_STATUS_UPDATE(
      "institution:academic-space:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar espacios académicos"),
  ACADEMIC_SPACE_DELETE(
      "institution:academic-space:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar espacios académicos"),
  ACADEMIC_SPACE_RESTORE(
      "institution:academic-space:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar espacios académicos"),
  INSTRUMENT_READ(
      "institution:instrument:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver instrumentos"),
  INSTRUMENT_CREATE(
      "institution:instrument:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear instrumentos"),
  INSTRUMENT_UPDATE(
      "institution:instrument:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar instrumentos"),
  INSTRUMENT_STATUS_UPDATE(
      "institution:instrument:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar instrumentos"),
  INSTRUMENT_DELETE(
      "institution:instrument:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar instrumentos"),
  INSTRUMENT_RESTORE(
      "institution:instrument:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar instrumentos"),
  ENROLLMENT_PERIOD_READ(
      "institution:enrollment-period:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver períodos de inscripción"),
  ENROLLMENT_PERIOD_CREATE(
      "institution:enrollment-period:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear períodos de inscripción"),
  ENROLLMENT_PERIOD_UPDATE(
      "institution:enrollment-period:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar períodos de inscripción"),
  ENROLLMENT_PERIOD_STATUS_UPDATE(
      "institution:enrollment-period:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Cambiar estado de períodos de inscripción"),
  ENROLLMENT_PERIOD_DELETE(
      "institution:enrollment-period:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar períodos de inscripción"),
  SHIFT_READ(
      "institution:shift:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver turnos"),
  SHIFT_CREATE(
      "institution:shift:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear turnos"),
  SHIFT_UPDATE(
      "institution:shift:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar turnos"),
  SHIFT_STATUS_UPDATE(
      "institution:shift:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar turnos"),
  SHIFT_DELETE(
      "institution:shift:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar turnos"),
  SHIFT_RESTORE(
      "institution:shift:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar turnos"),
  COURSE_READ(
      "institution:course:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Ver cursos"),
  COURSE_CREATE(
      "institution:course:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Crear cursos"),
  COURSE_UPDATE(
      "institution:course:update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Editar cursos"),
  COURSE_STATUS_UPDATE(
      "institution:course:update-status",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Activar o desactivar cursos"),
  COURSE_DELETE(
      "institution:course:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Eliminar cursos"),
  COURSE_RESTORE(
      "institution:course:restore",
      PermissionScope.INSTITUTION,
      PermissionGroup.ACADEMIC,
      "Restaurar cursos"),
  ENROLLMENT_APPLICATION_READ(
      "institution:enrollment-application:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Ver solicitudes de inscripción"),
  ENROLLMENT_DOCUMENT_REQUEST_CREATE(
      "institution:enrollment-document-request:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Solicitar documentación adicional"),
  ENROLLMENT_ATTACHMENT_READ(
      "institution:enrollment-attachment:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Consultar documentación de inscripción"),
  ENROLLMENT_ATTACHMENT_UPLOAD(
      "institution:enrollment-attachment:upload",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Cargar documentación de inscripción"),
  ENROLLMENT_ATTACHMENT_REVIEW(
      "institution:enrollment-attachment:review",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Revisar documentación de inscripción"),
  ENROLLMENT_ATTACHMENT_DELETE(
      "institution:enrollment-attachment:delete",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Eliminar documentación de inscripción"),
  ENROLLMENT_APPLICATION_APPROVE(
      "institution:enrollment-application:approve",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Aprobar solicitudes de inscripción"),
  ENROLLMENT_APPLICATION_REJECT(
      "institution:enrollment-application:reject",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Rechazar solicitudes de inscripción"),
  GUARDIAN_DEPENDENT_MANAGE(
      "institution:guardian-dependent:manage",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Gestionar personas a cargo"),
  GUARDIAN_LINK_REVIEW(
      "institution:guardian-link:review",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Validar vinculaciones entre tutores y estudiantes"),
  INSTITUTION_AUDIT_READ(
      "institution:audit:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.INSTITUTION,
      "Ver registro de auditoría"),
  COURSE_ENROLLMENT_READ(
      "institution:course-enrollment:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Ver cursadas institucionales"),
  COURSE_ENROLLMENT_CREATE(
      "institution:course-enrollment:create",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Crear cursadas manuales"),
  COURSE_ENROLLMENT_WITHDRAW(
      "institution:course-enrollment:withdraw",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Registrar bajas de cursadas"),
  COURSE_ENROLLMENT_ACADEMIC_STATUS_UPDATE(
      "institution:course-enrollment:academic-status-update",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Modificar resultados académicos"),
  ENROLLMENT_APPLICATION_COURSE_READ(
      "institution:enrollment-application-course:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Ver solicitudes de cursada"),
  ENROLLMENT_APPLICATION_COURSE_ENROLL(
      "institution:enrollment-application-course:enroll",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Inscribir solicitudes de cursada"),
  ENROLLMENT_APPLICATION_COURSE_REJECT(
      "institution:enrollment-application-course:reject",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Rechazar solicitudes de cursada"),
  COURSE_WAITLIST_READ(
      "institution:course-waitlist:read",
      PermissionScope.INSTITUTION,
      PermissionGroup.ENROLLMENT,
      "Ver listas de espera");

  private final String code;
  private final PermissionScope scope;
  private final PermissionGroup group;
  private final String description;

  public boolean supportsTrainingPaths() {
    return switch (this) {
      case TRAINING_PATH_READ,
          TRAINING_PATH_UPDATE,
          TRAINING_PATH_STATUS_UPDATE,
          TRAINING_PATH_DELETE,
          TRAINING_PATH_RESTORE,
          STUDY_PLAN_READ,
          STUDY_PLAN_CREATE,
          STUDY_PLAN_UPDATE,
          STUDY_PLAN_STATUS_UPDATE,
          STUDY_PLAN_CURRICULUM_UPDATE,
          STUDY_PLAN_DELETE,
          STUDY_PLAN_RESTORE,
          ACADEMIC_OFFER_READ,
          COURSE_READ,
          COURSE_CREATE,
          COURSE_UPDATE,
          COURSE_STATUS_UPDATE,
          COURSE_DELETE,
          COURSE_RESTORE,
          ENROLLMENT_PERIOD_READ,
          ENROLLMENT_PERIOD_CREATE,
          ENROLLMENT_PERIOD_UPDATE,
          ENROLLMENT_PERIOD_STATUS_UPDATE,
          ENROLLMENT_PERIOD_DELETE,
          ENROLLMENT_APPLICATION_READ,
          ENROLLMENT_DOCUMENT_REQUEST_CREATE,
          ENROLLMENT_ATTACHMENT_READ,
          ENROLLMENT_ATTACHMENT_UPLOAD,
          ENROLLMENT_ATTACHMENT_DELETE,
          ENROLLMENT_ATTACHMENT_REVIEW,
          ENROLLMENT_APPLICATION_APPROVE,
          ENROLLMENT_APPLICATION_REJECT,
          COURSE_ENROLLMENT_READ,
          COURSE_ENROLLMENT_CREATE,
          COURSE_ENROLLMENT_WITHDRAW,
          COURSE_ENROLLMENT_ACADEMIC_STATUS_UPDATE,
          ENROLLMENT_APPLICATION_COURSE_READ,
          ENROLLMENT_APPLICATION_COURSE_ENROLL,
          ENROLLMENT_APPLICATION_COURSE_REJECT,
          COURSE_WAITLIST_READ ->
          true;
      default -> false;
    };
  }

  public boolean isConfigurable() {
    return group != PermissionGroup.GRADES;
  }

  public Set<PermissionCode> requiredPermissions() {
    return switch (this) {
      case INSTITUTION_PERSON_CREATE,
          INSTITUTION_PERSON_UPDATE_ANY,
          INSTITUTION_PERSON_DELETE,
          INSTITUTION_USER_STATUS_UPDATE ->
          Set.of(INSTITUTION_PERSON_READ_ANY);
      case INSTITUTION_ROLE_ASSIGN, INSTITUTION_ROLE_REVOKE ->
          Set.of(INSTITUTION_PERSON_READ_ANY, INSTITUTION_ROLE_READ);
      case INSTITUTION_ROLE_CREATE, INSTITUTION_ROLE_UPDATE, INSTITUTION_ROLE_DELETE ->
          Set.of(INSTITUTION_ROLE_READ);
      case INSTITUTION_UPDATE -> Set.of(INSTITUTION_READ);
      case ACADEMIC_YEAR_CREATE,
          ACADEMIC_YEAR_UPDATE,
          ACADEMIC_YEAR_STATUS_UPDATE,
          ACADEMIC_YEAR_DELETE,
          ACADEMIC_YEAR_RESTORE ->
          Set.of(ACADEMIC_YEAR_READ);
      case TRAINING_PATH_CREATE,
          TRAINING_PATH_UPDATE,
          TRAINING_PATH_STATUS_UPDATE,
          TRAINING_PATH_DELETE,
          TRAINING_PATH_RESTORE ->
          Set.of(TRAINING_PATH_READ);
      case STUDY_PLAN_CREATE,
          STUDY_PLAN_UPDATE,
          STUDY_PLAN_STATUS_UPDATE,
          STUDY_PLAN_CURRICULUM_UPDATE,
          STUDY_PLAN_DELETE,
          STUDY_PLAN_RESTORE ->
          Set.of(STUDY_PLAN_READ);
      case ACADEMIC_SPACE_CREATE,
          ACADEMIC_SPACE_UPDATE,
          ACADEMIC_SPACE_STATUS_UPDATE,
          ACADEMIC_SPACE_DELETE,
          ACADEMIC_SPACE_RESTORE ->
          Set.of(ACADEMIC_SPACE_READ);
      case INSTRUMENT_CREATE,
          INSTRUMENT_UPDATE,
          INSTRUMENT_STATUS_UPDATE,
          INSTRUMENT_DELETE,
          INSTRUMENT_RESTORE ->
          Set.of(INSTRUMENT_READ);
      case ENROLLMENT_PERIOD_CREATE,
          ENROLLMENT_PERIOD_UPDATE,
          ENROLLMENT_PERIOD_STATUS_UPDATE,
          ENROLLMENT_PERIOD_DELETE ->
          Set.of(ENROLLMENT_PERIOD_READ);
      case SHIFT_CREATE, SHIFT_UPDATE, SHIFT_STATUS_UPDATE, SHIFT_DELETE, SHIFT_RESTORE ->
          Set.of(SHIFT_READ);
      case COURSE_CREATE, COURSE_UPDATE, COURSE_STATUS_UPDATE, COURSE_DELETE, COURSE_RESTORE ->
          Set.of(COURSE_READ);
      case DOCUMENT_CATALOG_MANAGE -> Set.of(DOCUMENT_CATALOG_READ);
      case ENROLLMENT_ATTACHMENT_READ -> Set.of(ENROLLMENT_APPLICATION_READ);
      case ENROLLMENT_DOCUMENT_REQUEST_CREATE,
          ENROLLMENT_ATTACHMENT_UPLOAD,
          ENROLLMENT_ATTACHMENT_DELETE,
          ENROLLMENT_ATTACHMENT_REVIEW ->
          Set.of(ENROLLMENT_ATTACHMENT_READ);
      case ENROLLMENT_APPLICATION_APPROVE, ENROLLMENT_APPLICATION_REJECT ->
          Set.of(ENROLLMENT_APPLICATION_READ);
      case COURSE_ENROLLMENT_CREATE,
          COURSE_ENROLLMENT_WITHDRAW,
          COURSE_ENROLLMENT_ACADEMIC_STATUS_UPDATE,
          COURSE_WAITLIST_READ ->
          Set.of(COURSE_ENROLLMENT_READ);
      case ENROLLMENT_APPLICATION_COURSE_ENROLL, ENROLLMENT_APPLICATION_COURSE_REJECT ->
          Set.of(ENROLLMENT_APPLICATION_COURSE_READ, ENROLLMENT_APPLICATION_READ);
      default -> Set.of();
    };
  }

  public static Set<PermissionCode> withRequiredPermissions(Set<PermissionCode> permissions) {
    final EnumSet<PermissionCode> expanded = EnumSet.noneOf(PermissionCode.class);
    expanded.addAll(permissions);
    boolean changed;

    do {
      changed =
          expanded.addAll(
              expanded.stream()
                  .flatMap(permission -> permission.requiredPermissions().stream())
                  .toList());
    } while (changed);

    return Set.copyOf(expanded);
  }

  public static PermissionCode fromCode(String code) {
    for (PermissionCode permission : values()) {
      if (permission.code.equals(code)) {
        return permission;
      }
    }

    throw new IllegalArgumentException(
        String.format(AuthorizationMessages.UNKNOWN_PERMISSION_CODE, code));
  }
}
