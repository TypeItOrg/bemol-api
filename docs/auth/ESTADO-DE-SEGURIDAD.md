# ESTADO DE SEGURIDAD, AISLAMIENTO Y CONCURRENCIA

## Propósito

Este documento describe las garantías que están implementadas actualmente. Sirve como contrato técnico para revisar cambios, diseñar endpoints y coordinar trabajo entre backend, frontend y QA.

No describe funcionalidades futuras como si ya existieran. Cuando una garantía dependa de una convención de implementación, se indica explícitamente.

## Estado general

| Área | Estado | Garantía principal |
|---|---|---|
| Autenticación dual | Implementado | Usuarios institucionales y cuentas plataforma tienen credenciales, sesiones y tokens separados |
| Aislamiento institucional | Implementado | Un principal institucional no puede operar sobre un `institutionId` diferente al de su JWT |
| Rotación de refresh tokens | Implementado | Rotación por familia, lock pesimista y detección de reutilización |
| Revocación por institución inactiva | Implementado | Se cierran sesiones y login, refresh y access token vuelven a comprobar el estado |
| Autorización dinámica | Implementado | Roles y permisos se resuelven fuera del JWT y se cachean con invalidación explícita |
| Última autoridad institucional | Implementado | Revocación y eliminación usan lock institucional y rechazan dejar cero autoridades |
| Integridad entre tenants | Implementado | Entidades críticas validan coherencia de institución y las consultas incluyen el tenant |
| Conflictos concurrentes de unicidad | Implementado | Las restricciones se fuerzan con `flush()` y se convierten a excepciones HTTP 409 |
| Pruebas PostgreSQL de refresh | Implementado | Se prueba commit tras reutilización y rotación concurrente con Testcontainers |
| Migraciones de esquema | Implementado | Flyway versiona las transiciones y Hibernate valida el resultado contra las entidades |
| Rate limiting de autenticación | Capa externa | Nginx; verificar cobertura de rutas de autenticación antes del despliegue |

## Invariantes que no deben romperse

### Identidad institucional

- Un `User` pertenece a una sola `Institution`.
- La `Person` asociada al usuario debe pertenecer a esa misma institución.
- Una `PersonRoleAssignment` debe referenciar una persona de la institución indicada.
- Un rol institucional específico solo puede asignarse dentro de su institución; los roles globales de sistema pueden aplicarse a cualquier institución.
- Las búsquedas y mutaciones de personas reciben simultáneamente `personId` e `institutionId` cuando el recurso está bajo un tenant.

### Habilitación de una cuenta

Un usuario institucional es utilizable solamente si:

```text
user.enabled
AND institution.active
AND NOT person.deleted
```

Esta regla se aplica en autenticación, refresh y validación de sesión. No debe reemplazarse por una comprobación aislada de `UserSession.active`.

### Refresh tokens

- El token crudo solo se entrega al cliente.
- La base almacena SHA-256 del token.
- Cada token pertenece a una sesión y familia.
- La fila se bloquea antes de rotarla.
- Presentar un token revocado se considera reutilización, no una expiración normal.
- La reutilización revoca toda la familia y las sesiones vinculadas.
- Las revocaciones deben confirmarse aunque el endpoint termine respondiendo `401`.

### Autorización

- Los permisos no se confían al contenido del JWT.
- Un cambio de rol debe invalidar las entradas correspondientes de caché.
- `PLATFORM_ADMIN` administra recursos globales mediante `/api/v1/admin/**`; las cuentas plataforma no usan las rutas institucionales como acceso implícito.
- Crear una persona con rol privilegiado requiere permiso de asignación o administración de plataforma.
- Nunca debe aceptarse un `institutionId` del body o path sin contrastarlo con el principal o con una guarda equivalente.

### WebAuthn

- Las authentication options son discoverable: se generan con un `Authentication` no-autenticado para que Spring devuelva `allowCredentials` siempre vacío; ningún credential ID sale del servidor antes de autenticar (verificado por test).
- Después del assertion se verifica obligatoriamente que el owner de la credencial sea el usuario del login attempt y que la credencial esté activa y le pertenezca.
- El contrato HTTP es JSON plano explícito construido campo por campo en `WebAuthnOptionsCodec` (inmune a mixins/SPI de Jackson de Spring y a upgrades); los snapshots de Redis conservan la forma completa que el deserializador interno requiere.
- `userVerification=required`, `residentKey=required`, `attestation=none` se fijan en `WebAuthnConfig`.
- Los tests round-trip y de contrato fallan ante cambios del modelo WebAuthn de Spring por diseño (canary de upgrade).
- El write de `lastUsedAt`/contador ocurre una sola vez, dentro del `save` del adapter que Spring invoca en `authenticate()`; el use case nunca escribe la credencial directamente. El `save` del adapter solo actualiza existentes: la creación vive en el verify de registración.

### Recent authentication

- Registrar una passkey exige recent-auth vigente al INICIAR la ceremonia (options); la ceremony ligada a `userId/sessionId/createdAt` autoriza COMPLETARLA mientras siga válida aunque el marker expire en el medio (el `save` ocurre después: nunca quedan passkeys huérfanas).
- Revocar una passkey exige recent-auth vigente al ejecutar.

### Despliegue

- Los defaults `localhost` de WebAuthn viven solo en el profile `dev`; `prod`/`staging` exigen `WEBAUTHN_RP_ID` y `WEBAUTHN_ALLOWED_ORIGINS` explícitos (sin default la aplicación no arranca).
- `WebAuthnDeploymentGuard` rechaza el arranque con `rpId=localhost` u orígenes `http://localhost` en cualquier environment que no sea dev/test.

### Errores

- Solo fallos conocidos de verificación WebAuthn (`VerificationException`, `IllegalArgumentException` de Spring) se mapean a 401; fallos de infraestructura o bugs propagan como 5xx observables. Un contador clonado (`MaliciousCounterValueException`) se loguea como `WARN`.
- Un duplicado de `credential_id` (pre-check o constraint `passkey_credentials_credential_id_key`) es 409 controlado; cualquier otra violación de integridad es 5xx.
- Los estados de cuenta nunca se revelan antes de autenticar: `DisabledException` se mapea a credenciales inválidas genéricas.
- `ExceptionPayload` incluye `code` machine-readable opcional; `RECENT_AUTHENTICATION_REQUIRED` es el único código que abre el diálogo de re-autenticación en el frontend (nunca el status 403 a secas).
- Un usuario que desaparece a mitad de ceremonia/login se mapea a estado inconsistente reintentable (401), nunca a 500 accidental.

### Rate limiting de autenticación

El control de frecuencia corresponde a Nginx, fuera de la API. Antes del despliegue se debe comprobar que sus reglas cubran identificación, contraseña, passkeys, reautenticación y recuperación. La API no mantiene contadores por IP ni por cuenta. El ejemplo versionado de `bemol-infra` todavía enumera el login anterior y activa `limit_req_dry_run`; su revisión no sustituye verificar la configuración efectiva del servidor.

Redis sigue siendo necesario para intentos de login, ceremonias WebAuthn, autenticación reciente y los demás mecanismos de sesión existentes.

## Capas de protección

```text
Request
  → JwtAuthenticationFilter
      → firma, expiración y blacklist
      → sesión utilizable
  → anotación de rol o permiso
  → @RequiresInstitutionAccess
  → controller
  → use case transaccional
  → repositorio filtrado por institución
  → restricciones y claves foráneas
```

Las capas son complementarias. La validación en controller impide una operación ilegítima temprano; el filtro por institución en persistencia evita que un error en una capa superior termine exponiendo otro tenant; las restricciones preservan integridad ante concurrencia o errores de código.

## Concurrencia

### Rotación de refresh

Los repositorios de refresh token usan `PESSIMISTIC_WRITE` al buscar por hash. El comportamiento esperado con dos requests simultáneos es una rotación exitosa seguida de detección de reutilización. La familia y sesión terminan revocadas.

### Última autoridad

Antes de contar autoridades y revocar un rol o eliminar una persona, se bloquea la fila de `Institution`. Todas las operaciones que preservan este invariante deben adquirir el mismo lock antes del conteo.

### Credenciales passkey

La autenticación bloquea la credencial hasta finalizar su transacción. Revocación y renombrado adquieren el mismo bloqueo y validan su estado antes de modificarla, evitando que una actualización obsoleta restaure una credencial revocada.

### Login attempts

- Un `loginAttemptId` produce como máximo una sesión: se reclama atómicamente (`GETDEL`) después de verificar la credencial y antes de emitir la sesión.
- Una password incorrecta no consume el attempt (reintentos permitidos); dos verificaciones concurrentes exitosas producen exactamente una sesión.
- Un attempt `PASSWORD-only` (`hasActivePasskeys=false`) no genera options de passkey.
- Antes de emitir la sesión se revalida `User.enabled + Institution.active + Person no eliminada`; roles y permisos se resuelven frescos en la emisión.

### Seed de permisos y roles

El seed usa `pg_advisory_xact_lock` fuera del perfil `test`. El lock cubre toda la transacción de sincronización y evita carreras entre réplicas durante el arranque.

### Restricciones únicas

Las comprobaciones `exists...` producen mensajes tempranos, pero no reemplazan una restricción de base. Los casos de creación y actualización fuerzan `flush()` dentro del use case para capturar una carrera y traducirla a una excepción de dominio HTTP 409.

## Cachés

| Caché | Se invalida cuando |
|---|---|
| `activeSessions` | logout, reutilización de refresh, eliminación de persona o desactivación institucional |
| `activePlatformSessions` | logout, reutilización de refresh, desactivación o cambio de credenciales de cuenta plataforma |
| `personPermissions` | asignación/revocación de rol institucional y seed |
| `platformAccountPermissions` | asignación/revocación de rol plataforma y seed |
| `platformAccountRoles` | asignación/revocación de rol plataforma y seed |

Redis aplica un TTL de 5 minutos a las entradas de Spring Cache. En staging y producción usa AOF sobre un volumen persistente para conservar la blacklist de access tokens después de un reinicio. Al agregar una mutación nueva de sesiones, roles o permisos, debe agregarse su invalidación en la misma entrega.

## Persistencia e índices relevantes

- Sesiones: índice por propietario y estado activo.
- Refresh tokens: índices por familia y sesión.
- Personas: índice por institución y eliminación lógica.
- Asignaciones de roles: índices por persona/institución y por institución/rol.
- Sesiones y refresh tokens tienen asociaciones de solo lectura para materializar claves foráneas sin reemplazar los IDs escalares usados por los casos de uso.
- `passkey_credentials.credential_id` es `TEXT`: la forma canónica es el Base64URL, sin límite artificial de 255 caracteres.
- Source of truth del user handle WebAuthn: `users.webauthn_user_handle`. La columna `passkey_credentials.user_handle` es un snapshot que ya ninguna lectura utiliza; su eliminación física queda planificada sin urgencia.
- Hard-delete de un `User` elimina en cascada sus `passkey_credentials` (política de privacidad: contienen identificadores asociados a esa cuenta). La auditoría histórica, si se necesita, usa eventos separados y minimizados. La revocación normal (soft-delete) conserva la fila con `revoked_at` mientras el usuario exista.

### Evolución del esquema

Flyway es la única herramienta que crea o modifica el esquema. Hibernate usa `ddl-auto=validate` y detiene el arranque si las entidades no coinciden con la base.

- Cada cambio persistente incluye la modificación del `@Entity` y una migración SQL nueva.
- Los nombres usan timestamps UTC con formato `yyyyMMddHHmmss__description.sql`.
- Una migración aplicada no se modifica ni se renombra; cualquier corrección avanza con una migración posterior.
- Las migraciones comunes viven en `db/migration`; `db/dev` contiene únicamente datos de desarrollo.
- La migración inicial se verifica contra PostgreSQL mediante Testcontainers antes de publicar la imagen.

## Cobertura automatizada

La suite incluye:

- Casos positivos y negativos de login, refresh y logout.
- Autorización MVC para principals institucionales y de plataforma.
- Acceso cruzado entre instituciones.
- Protección de la última autoridad.
- Desactivación institucional y revocación de sesiones.
- Asignación inicial de roles privilegiados.
- Integridad JPA y restricciones de persistencia.
- Conversión de carreras de unicidad a conflictos.
- PostgreSQL real con Testcontainers para reutilización y rotación concurrente de refresh tokens, revocación concurrente de passkeys y traducción de su restricción de unicidad creada por Flyway.

Los tests H2 siguen siendo útiles para slices rápidos, pero las garantías que dependen de locks o semántica transaccional de PostgreSQL deben cubrirse con Testcontainers.

## Checklist para nuevas funcionalidades

Antes de incorporar una operación institucional nueva:

1. ¿El endpoint exige permiso o rol explícito?
2. ¿Se valida que el `institutionId` pertenezca al principal?
3. ¿La consulta de repositorio filtra también por institución?
4. ¿La entidad resultante mantiene coherencia de tenant?
5. ¿La mutación afecta sesiones, roles o permisos cacheados?
6. ¿Existe una carrera entre el chequeo y la escritura?
7. ¿La restricción de base y la excepción HTTP representan el mismo conflicto?
8. ¿Hay pruebas positiva, negativa, de otro tenant y, si corresponde, concurrente?

## Referencias de implementación

- `auth/services/RefreshTokenUseCase`
- `auth/services/PlatformRefreshTokenUseCase`
- `auth/services/IsSessionActiveUseCase`
- `auth/filters/JwtAuthenticationFilter`
- `authorization/services/SessionRevocationService`
- `authorization/services/InitialRoleAssignmentGuard`
- `authorization/services/RevokePersonRoleUseCase`
- `authorization/services/PermissionRoleSeed`
- `authorization/security/InstitutionAccessAspect`
- `institutional/services/UpdateInstitutionStatusUseCase`
- `institutional/services/DeletePersonUseCase`
