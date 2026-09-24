package ar.edu.utn.frvm.typeit.boero_api.auth.exceptions;

public final class AuthMessages {
  public static final String INVALID_CREDENTIALS = "Las credenciales proporcionadas son inválidas.";
  public static final String INVALID_CURRENT_PASSWORD = "La contraseña actual no es correcta.";
  public static final String USER_DISABLED = "El usuario se encuentra deshabilitado.";
  public static final String USER_ALREADY_EXISTS =
      "Ya existe un usuario con este documento en la institución especificada.";
  public static final String INSTITUTION_NOT_FOUND = "La institución especificada no existe.";
  public static final String TOKEN_EXPIRED = "El token de acceso ha expirado.";
  public static final String TOKEN_INVALID = "El token de acceso es inválido.";
  public static final String TOKEN_MISSING =
      "Se requiere el encabezado Authorization con un token Bearer.";
  public static final String TOKEN_BLACKLISTED = "El token ha sido revocado.";
  public static final String TOKEN_SESSION_INACTIVE = "La sesión asociada al token no está activa.";
  public static final String REFRESH_TOKEN_INVALID =
      "El token de actualización es inválido o ha expirado.";
  public static final String REFRESH_TOKEN_REUSE =
      "Se detectó la reutilización del token de actualización. Todas las sesiones fueron revocadas.";
  public static final String PLATFORM_ACCOUNT_DISABLED =
      "La cuenta de plataforma se encuentra deshabilitada.";
  public static final String PLATFORM_ACCOUNT_NOT_FOUND =
      "La cuenta de plataforma especificada no existe.";
  public static final String PLATFORM_ACCOUNT_EMAIL_ALREADY_EXISTS =
      "Ya existe una cuenta de plataforma con ese correo electrónico.";
  public static final String PLATFORM_ACCOUNT_SELF_DISABLE =
      "No podés deshabilitar tu propia cuenta de plataforma.";
  public static final String LAST_PLATFORM_ADMIN_DISABLE =
      "No podés deshabilitar la última cuenta administradora activa.";
  public static final String PASSWORD_RECOVERY_TOKEN_INVALID =
      "El enlace de recuperación es inválido o ha expirado.";
  public static final String PASSWORD_CONFIRMATION_MISMATCH = "Las contraseñas no coinciden.";
  public static final String GUARDIAN_MUST_BE_ADULT =
      "Para registrarte como tutor debés ser mayor de edad.";
  public static final String INSTITUTIONAL_USERNAME_REQUIRED = "El nombre de usuario es requerido.";
  public static final String INSTITUTIONAL_USERNAME_INVALID =
      "El nombre de usuario institucional no es válido.";
  public static final String PLATFORM_USERNAME_INVALID =
      "El formato del nombre de usuario de plataforma no es válido.";
  public static final String USER_PERSON_INSTITUTION_MISMATCH =
      "El usuario y la persona deben pertenecer a la misma institución.";
  public static final String SHA_256_UNAVAILABLE = "SHA-256 debe estar disponible.";
  public static final String REFRESH_REPLAY_CACHE_INVALID =
      "La caché de reutilización del token de actualización contiene un valor inválido.";
  public static final String REFRESH_REPLAY_VALUE_TOO_SHORT =
      "El valor cifrado de la reutilización es demasiado corto.";
  public static final String REFRESH_REPLAY_VALUE_INVALID_FORMAT =
      "El valor cifrado de la reutilización tiene un formato inválido.";
  public static final String REFRESH_REPLAY_ENCRYPTION_FAILED =
      "No se pudo cifrar la reutilización del token de actualización.";
  public static final String REFRESH_REPLAY_ENCRYPTION_KEY_REQUIRED =
      "La propiedad app.auth.refresh-replay.encryption-key es obligatoria.";
  public static final String REFRESH_REPLAY_TTL_INVALID =
      "La propiedad app.auth.refresh-replay.ttl debe ser positiva.";
  public static final String REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_BASE64 =
      "La propiedad app.auth.refresh-replay.encryption-key debe contener una clave Base64 válida.";
  public static final String REFRESH_REPLAY_ENCRYPTION_KEY_INVALID_SIZE =
      "La propiedad app.auth.refresh-replay.encryption-key debe decodificar a 32 bytes.";
  public static final String LOGIN_ACCOUNT_NOT_FOUND =
      "No encontramos una cuenta asociada a ese DNI en esta institución.";
  public static final String LOGIN_ATTEMPT_INVALID =
      "El intento de inicio de sesión es inválido o ha expirado.";
  public static final String LOGIN_STATE_INCONSISTENT =
      "No pudimos completar el inicio de sesión. Intentá nuevamente.";
  public static final String PASSKEY_NOT_FOUND = "La passkey especificada no existe.";
  public static final String PASSKEY_ALREADY_REGISTERED = "Esta passkey ya está registrada.";
  public static final String PASSKEY_LIMIT_EXCEEDED =
      "Alcanzaste el máximo de passkeys activas permitidas.";
  public static final String PASSKEY_LABEL_REQUIRED = "El nombre de la passkey es requerido.";
  public static final String PASSKEY_LABEL_LENGTH =
      "El nombre debe tener entre 1 y 100 caracteres.";
  public static final String RECENT_AUTH_REQUIRED =
      "Necesitás una autenticación reciente para realizar esta acción.";
  public static final String WEBAUTHN_CEREMONY_INVALID =
      "La ceremonia WebAuthn es inválida o ha expirado.";
  public static final String WEBAUTHN_VERIFICATION_FAILED =
      "No pudimos usar una passkey. Podés reintentar o ingresar con tu contraseña.";
  public static final String PASSKEY_EMAIL_VERIFICATION_REQUIRED =
      "Confirmá tu correo electrónico antes de ingresar. Para continuar, ingresá tu documento.";

  public static final String EMAIL_VERIFICATION_DURATION_INVALID =
      "Los intervalos de verificación de email deben ser positivos.";
  public static final String EMAIL_VERIFICATION_TOKEN_INVALID =
      "El enlace de verificación es inválido o venció. Solicitá uno nuevo.";
  public static final String EMAIL_VERIFICATION_COOLDOWN =
      "Esperá un minuto entre envíos antes de corregir el email.";
  public static final String EMAIL_VERIFICATION_CONFLICT =
      "No se pudo generar el enlace. Intentá nuevamente.";
  public static final String EMAIL_VERIFICATION_TOKEN_REQUIRED =
      "El enlace de verificación es obligatorio.";
  public static final String EMAIL_VERIFICATION_TOKEN_FORMAT =
      "El enlace de verificación es inválido.";

  public static final String INSTITUTIONAL_HOST_INVALID =
      "El acceso institucional solicitado no es válido.";
  public static final String INSTITUTIONAL_CONTEXT_MISMATCH =
      "Este acceso pertenece a otra institución.";

  private AuthMessages() {}
}
