package ar.edu.utn.frvm.typeit.boero_api.common.validation;

public final class ValidationMessages {

  public static final String ACTIVE_REQUIRED = "El estado activo es requerido.";
  public static final String APARTMENT_MAX_LENGTH =
      "El departamento debe tener menos de 50 caracteres.";
  public static final String BIRTH_DATE_REQUIRED = "La fecha de nacimiento es requerida.";
  public static final String BIRTH_DATE_PAST = "La fecha de nacimiento debe ser anterior a hoy.";
  public static final String GUARDIAN_RELATIONSHIP_REQUIRED = "El vínculo es requerido.";
  public static final String CEREMONY_REQUIRED = "La ceremonia es requerida.";
  public static final String CITY_REQUIRED = "La ciudad es requerida.";
  public static final String CONFIRMATION_PASSWORD_RANGE =
      "La confirmación de la contraseña debe tener entre 8 y 255 caracteres.";
  public static final String CONFIRMATION_PASSWORD_REQUIRED =
      "La confirmación de la contraseña es requerida.";
  public static final String CURRENT_PASSWORD_MAX_LENGTH =
      "La contraseña actual no puede superar los 255 caracteres.";
  public static final String DOCUMENT_FORMAT =
      "El número de documento debe tener exactamente 8 dígitos numéricos.";
  public static final String DOCUMENT_REQUIRED = "El número de documento es requerido.";
  public static final String EMAIL_FORMAT = "El correo electrónico debe tener un formato válido.";
  public static final String EMAIL_MAX_LENGTH =
      "El correo electrónico debe tener menos de 150 caracteres.";
  public static final String EMAIL_REQUIRED = "El correo electrónico es requerido.";
  public static final String NAME_MAX_LENGTH = "El nombre debe tener menos de 255 caracteres.";
  public static final String NAME_REQUIRED = "El nombre es requerido.";
  public static final String FIRST_NAME_FORMAT = "El nombre solo puede contener letras y espacios.";
  public static final String FIRST_NAME_MAX_LENGTH = NAME_MAX_LENGTH;
  public static final String FIRST_NAME_MIN_LENGTH = "El nombre debe tener al menos 3 caracteres.";
  public static final String FIRST_NAME_RANGE = "El nombre debe tener entre 3 y 255 caracteres.";
  public static final String FIRST_NAME_REQUIRED = NAME_REQUIRED;
  public static final String FLOOR_MAX_LENGTH = "El piso debe tener menos de 50 caracteres.";
  public static final String INSTITUTION_REQUIRED = "La institución es requerida.";
  public static final String LAST_NAME_FORMAT =
      "El apellido solo puede contener letras y espacios.";
  public static final String LAST_NAME_MAX_LENGTH =
      "El apellido debe tener menos de 255 caracteres.";
  public static final String LAST_NAME_MIN_LENGTH = "El apellido debe tener al menos 3 caracteres.";
  public static final String LAST_NAME_RANGE = "El apellido debe tener entre 3 y 255 caracteres.";
  public static final String LAST_NAME_REQUIRED = "El apellido es requerido.";
  public static final String LOGIN_ATTEMPT_REQUIRED =
      "El intento de inicio de sesión es requerido.";
  public static final String MINIMUM_AGE = "La persona debe tener al menos {value} años.";
  public static final String ORDER_POSITIVE = "El orden debe ser positivo.";
  public static final String PASSWORD_MAX_LENGTH =
      "La contraseña debe tener menos de 255 caracteres.";
  public static final String PASSWORD_MIN_LENGTH =
      "La contraseña debe tener al menos 8 caracteres.";
  public static final String PASSWORD_RANGE = "La contraseña debe tener entre 8 y 255 caracteres.";
  public static final String PASSWORD_RECOVERY_TOKEN_REQUIRED =
      "El token de recuperación es requerido.";
  public static final String PASSWORD_REQUIRED = "La contraseña es requerida.";
  public static final String PERMISSION_CATALOG_UNKNOWN =
      "El catálogo contiene un permiso desconocido.";
  public static final String PERSON_EMAIL_FORMAT = "El email debe tener un formato válido.";
  public static final String PERSON_EMAIL_MAX_LENGTH =
      "El email debe tener menos de 150 caracteres.";
  public static final String PERSON_EMAIL_REQUIRED = "El email es requerido.";
  public static final String PHONE_MAX_LENGTH = "El teléfono debe tener menos de 30 caracteres.";
  public static final String REFRESH_TOKEN_REQUIRED = "El token de actualización es requerido.";
  public static final String ROLE_REQUIRED = "El rol es requerido.";
  public static final String ROLE_NAME_MAX_LENGTH =
      "El nombre no puede superar los 100 caracteres.";
  public static final String SLUG_FORMAT =
      "El slug solo puede contener letras minúsculas, números y guiones.";
  public static final String SLUG_MAX_LENGTH = "El slug debe tener menos de 100 caracteres.";
  public static final String SLUG_REQUIRED = "El slug es requerido.";
  public static final String STREET_REQUIRED = "La calle es requerida.";
  public static final String NUMBER_MAX_LENGTH = "El número debe tener menos de 50 caracteres.";
  public static final String YEAR_MINIMUM = "El año debe ser igual o posterior a 2000.";

  private ValidationMessages() {}
}
