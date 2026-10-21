# REGISTRO Y TRAZABILIDAD (LOGS)

Este documento define la política de logging, la correlación de solicitudes y las restricciones de seguridad aplicadas en la API de Bemol.

## 1. Filosofía de Logging

El registro de logs en Bemol tiene como objetivo proveer observabilidad operativa y diagnóstica precisa sin comprometer datos sensibles ni degradar el rendimiento:

- **Sin ruido innecesario**: Los errores funcionales esperados no generan stack traces en consola.
- **Trazabilidad de punta a punta**: Cada acción se correlaciona con una petición HTTP mediante un identificador único.
- **Protección estricta de datos (PII y secretos)**: Nunca se persisten ni emiten credenciales, tokens ni datos personales sensibles en texto plano.

## 2. Trazabilidad con `RequestLoggingFilter`

La clase `RequestLoggingFilter` (`ar.edu.utn.frvm.typeit.bemol_api.common.logging.RequestLoggingFilter`) gestiona la correlación de cada petición:

1. **Extracción y Validación de ID**:
   - Inspecciona la cabecera HTTP `X-Request-Id` de la petición.
   - Si contiene un identificador válido (patrón alfanumérico seguro de 8 a 36 caracteres), lo conserva.
   - Si la cabecera no existe o no es válida, genera un `UUID` aleatorio.
2. **Inyección en MDC**:
   - Asigna el identificador al Mapped Diagnostic Context de SLF4J bajo la clave `requestId`:
     ```java
     MDC.put("requestId", requestId);
     ```
   - Gracias a esto, cualquier log emitido durante el procesamiento de la petición (en servicios, filtros o repositorios) incluye automáticamente el `requestId`.
3. **Cabecera de Respuesta**:
   - Envía la cabecera `X-Request-Id` al cliente en la respuesta HTTP, permitiendo al usuario o frontend reportar incidentes con dicho código.
4. **Métricas y Resumen de Solicitud**:
   - Mide la duración de la petición en nanosegundos utilizando `System.nanoTime()` (inmune a cambios en la hora del sistema).
   - Al finalizar en el bloque `finally`, emite una única línea de resumen:
     ```text
     [HTTP] Handled request, method: GET, path: /api/v1/institutions/..., status: 200, durationMs: 14
     ```
   - Limpia de forma garantizada el MDC con `MDC.remove("requestId")` para evitar fugas de memoria o contaminación cruzada entre hilos del servidor.

## 3. Formato Estructurado y Parametrizado

Todas las clases utilizan `@Slf4j` de Lombok y deben apegarse al formato estándar:

```text
[Contexto] Acción realizada, clave1: {}, clave2: {}
```

### Reglas de formato:
- **Logging Parametrizado**: Utilizar siempre los placeholders `{}` de SLF4J. **Nunca concatenar strings con el operador `+`**, ya que genera asignación innecesaria de memoria incluso cuando el nivel de log está desactivado.
- **Mayúsculas y corchetes**: El prefijo entre corchetes identifica el componente o dominio (por ejemplo: `[Auth]`, `[StudyPlan]`, `[CourseEnrollment]`, `[Storage]`).

### Ejemplo:
```java
log.info("[StudyPlan] Curriculum frozen successfully, studyPlanId: {}, spaceCount: {}", studyPlanId, count);
```

## 4. Niveles de Log y Responsabilidades

| Nivel | Cuándo utilizarlo | Comportamiento |
|---|---|---|
| **ERROR** | Errores imprevistos del sistema, excepciones no capturadas o fallos graves de infraestructura. | Se emite **un único ERROR con stack trace completo** centralizado en `GlobalExceptionHandler`. Las capas internas no capturan para re-loguear si van a relanzar la excepción. |
| **WARN** | Respuestas HTTP 4xx atípicas o degradaciones temporales que no impiden el funcionamiento general. | No incluye stack trace por defecto. Los errores de validación habituales no deben generar WARN continuo. |
| **INFO** | Operaciones de negocio exitosas y de alto impacto (inicio de la aplicación, inicio de sesión, publicación de notas, altas curriculares, resumen HTTP de peticiones). | Es el nivel predeterminado visible en entornos productivos. |
| **DEBUG** | Trazas detalladas de diagnóstico para desarrolladores durante el flujo local. | Deshabilitado en staging y producción para los paquetes de la aplicación. |

## 5. Reglas Estrictas de Seguridad y Privacidad

Para garantizar el cumplimiento de normativas de privacidad y prevenir incidentes de seguridad:

### Prohibido registrar:
- **Contraseñas**: Tanto en texto plano como contraseñas hasheadas.
- **Tokens y Secretos**: Access tokens JWT, refresh tokens, claves HMAC, firmas o passkey challenges.
- **Cabeceras de autenticación**: `Authorization`, `Cookie`, `Set-Cookie`.
- **Credenciales en la nube**: Claves de acceso AWS o URLs pre-firmadas privadas.

### Identificadores y Datos Personales (PII):
- **Preferir identificadores internos**: Registrar IDs numéricos o UUIDs (`userId`, `personId`, `institutionId`, `enrollmentId`).
- **No registrar información de contacto directa**: Evitar escribir en los logs números de DNI/documento, correos electrónicos, teléfonos o domicilios particulares salvo que sea estrictamente necesario para auditoría formal persistida en base de datos.
