package ar.edu.utn.frvm.typeit.bemol_api.authorization.exceptions;

public final class AuthorizationMessages {

  public static final String PERSON_NOT_FOUND_IN_INSTITUTION =
      "La persona especificada no pertenece a la institución indicada.";
  public static final String ROLE_NOT_ASSIGNABLE =
      "El rol especificado no puede asignarse a una persona institucional.";
  public static final String LAST_PERSON_ROLE_REVOCATION =
      "No se puede revocar el único rol del usuario. Asigne otro rol antes.";
  public static final String INSTITUTION_INACTIVE = "La institución está inactiva.";
  public static final String SYSTEM_ROLE_NOT_DELETABLE =
      "Los roles del sistema no se pueden eliminar.";
  public static final String INSTITUTIONAL_AUTHORITY_ROLE_IMMUTABLE =
      "El rol de autoridad institucional no se puede modificar.";
  public static final String ROLE_NOT_FOUND = "Rol no encontrado.";
  public static final String DUPLICATE_ROLE_NAME =
      "Ya existe un rol con ese nombre en la institución.";
  public static final String ROLE_ASSIGNMENT_NOT_ALLOWED = "No podés asignar roles.";
  public static final String ROLE_REVOCATION_NOT_ALLOWED = "No podés revocar roles.";
  public static final String PERMISSION_DELEGATION_NOT_ALLOWED =
      "No podés delegar permisos que no poseés.";
  public static final String ROLE_WITH_ASSIGNMENTS =
      "El rol no se puede eliminar mientras tenga usuarios asignados.";
  public static final String ROLE_MANAGEMENT_SELF_LOCKOUT =
      "No podés quitarte los permisos necesarios para administrar roles institucionales.";
  public static final String PERSON_ROLE_INSTITUTION_MISMATCH =
      "La persona, el rol y la asignación deben pertenecer a la misma institución.";
  public static final String PLATFORM_ROLE_REQUIRED =
      "El rol asignado debe pertenecer a la plataforma.";
  public static final String UNKNOWN_PERMISSION_CODE = "Código de permiso desconocido: %s";
  public static final String SYSTEM_ROLE_NOT_SEEDED = "El rol del sistema no está configurado: %s.";
  public static final String PLATFORM_ROLE_NOT_SEEDED =
      "El rol de plataforma no está configurado: %s.";
  public static final String PERMISSION_NOT_SEEDED = "El permiso no está configurado: %s.";
  public static final String INSTITUTION_ACCESS_ANNOTATION_MISCONFIGURED =
      "La anotación RequiresInstitutionAccess se utilizó en un método sin un parámetro UUID "
          + "llamado institutionId.";

  public static final String INVALID_ACCESS_SCOPE =
      "El alcance debe ser institucional o incluir trayectos válidos de la misma institución.";
  public static final String SCOPED_RESOURCE_NOT_FOUND = "Recurso no encontrado.";

  private AuthorizationMessages() {}
}
