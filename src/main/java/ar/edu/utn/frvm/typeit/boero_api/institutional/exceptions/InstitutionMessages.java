package ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions;

public final class InstitutionMessages {

  public static final String INSTITUTION_NOT_FOUND = "La institución especificada no existe.";
  public static final String INSTITUTION_INACTIVE = "La institución especificada no está activa.";
  public static final String SLUG_ALREADY_EXISTS = "Ya existe una institución con ese slug.";
  public static final String CITY_NOT_FOUND = "La ciudad especificada no existe.";
  public static final String PERSON_ALREADY_EXISTS =
      "Ya existe una persona con ese documento en la institución.";
  public static final String PERSON_NOT_FOUND = "La persona especificada no existe.";
  public static final String PROVINCE_NOT_FOUND = "La provincia especificada no existe.";
  public static final String COUNTRY_NOT_FOUND = "El país especificado no existe.";
  public static final String CANNOT_MODIFY_OWN_ACCESS =
      "No podés modificar el estado de tu propio acceso.";
  public static final String PERSON_ADDRESS_INSTITUTION_MISMATCH =
      "La persona y el domicilio deben pertenecer a la misma institución.";
  public static final String CANNOT_GUARDIAN_SELF =
      "No podés registrarte a vos mismo como persona a cargo.";
  public static final String DEPENDENT_ALREADY_LINKED =
      "Esa persona ya está registrada como persona a cargo tuya.";
  public static final String DEPENDENT_BIRTH_DATE_MISMATCH =
      "La fecha de nacimiento no coincide con la registrada para ese documento.";
  public static final String DEPENDENT_NOT_FOUND =
      "No se encontró la persona a cargo especificada.";
  public static final String GUARDIANSHIP_UNAUTHORIZED =
      "No tenés la tutela de la persona indicada.";
  public static final String GUARDIAN_LINK_NOT_FOUND =
      "No se encontró la solicitud de vinculación especificada.";
  public static final String GUARDIAN_LINK_ATTACHMENT_NOT_FOUND =
      "No se encontró el documento especificado.";
  public static final String GUARDIAN_LINK_ATTACHMENT_LIMIT =
      "La solicitud ya tiene el máximo de documentos adjuntos.";
  public static final String GUARDIAN_LINK_ALREADY_RESOLVED =
      "La solicitud de vinculación ya fue resuelta.";

  public static final String PUBLIC_SUBDOMAIN_INVALID =
      "Usá entre 1 y 63 letras minúsculas, números o guiones, sin guiones en los extremos.";
  public static final String PUBLIC_SUBDOMAIN_RESERVED = "Ese subdominio está reservado.";
  public static final String PUBLIC_SUBDOMAIN_ALREADY_EXISTS =
      "Ya existe una institución con ese subdominio.";
  public static final String PUBLIC_ACCESS_UNAVAILABLE =
      "El acceso institucional no está disponible temporalmente.";
  public static final String LOGO_INVALID = "El formato del logo no está permitido.";
  public static final String LOGO_TOO_LARGE = "El logo supera el tamaño máximo permitido.";
  public static final String LOGO_NOT_FOUND = "La institución no tiene un logo disponible.";

  private InstitutionMessages() {}
}
