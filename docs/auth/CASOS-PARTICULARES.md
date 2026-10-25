# CASOS PARTICULARES

## ¿Por qué `UserSession` existe si Spring es stateless?

Spring Security está configurado con `SessionCreationPolicy.STATELESS`. Sin embargo, el proyecto mantiene sesiones lógicas en la base de datos (`UserSession`, `PlatformSession`) porque:

- Cada access token está ligado a un `sessionId`
- El filtro JWT verifica que la sesión siga activa en cada request
- Permite revocar todos los tokens de una sesión (logout, incidentes de seguridad)
- Permite listar sesiones activas (`GET /api/v1/auth/sessions`)

## ¿Por qué los permisos no van en el JWT?

Incluir permisos en el JWT haría que:
- Cambiar un rol no tenga efecto hasta que el token expire
- El token sea más grande (especialmente con muchos permisos)
- Revocar permisos requiera blacklistear tokens activos

La decisión fue token pequeño + resolución dinámica con caché. El primer acceso resuelve desde base de datos y los siguientes reutilizan la entrada hasta una invalidación o hasta el TTL.

## Bootstrap del admin de plataforma

`PlatformAdminBootstrap` (`src/main/java/.../authorization/services/PlatformAdminBootstrap.java`) se ejecuta al iniciar la aplicación:

1. Lee `app.platform-admin.email` y `app.platform-admin.password` de la configuración
2. Si no están configurados, salta el bootstrap (útil para desarrollo)
3. Crea un `PlatformAccount` si no existe
4. Le asigna el rol `PLATFORM_ADMIN`

## Seeding de permisos y roles

`PermissionRoleSeed` (`src/main/java/.../authorization/services/PermissionRoleSeed.java`) se ejecuta con prioridad máxima al iniciar:

1. Sincroniza todos los `PermissionCode` del enum con la tabla `permissions`
2. Sincroniza `SystemRoleCode` con la tabla de roles institucionales
3. Sincroniza `PlatformRoleCode` con la tabla de roles de plataforma
4. Asigna permisos a roles según un mapeo fijo
5. En dev/test, asigna el rol `APPLICANT` a personas sin rol

En PostgreSQL, el seed adquiere un advisory lock transaccional antes de sincronizar. Esto serializa el arranque de varias instancias y evita que dos nodos intenten crear simultáneamente los mismos roles globales. Al finalizar limpia los cachés de autorización.

El rol Estudiante recibe `ACADEMIC_OFFER_READ`, sin `STUDY_PLAN_READ` ni
`ACADEMIC_YEAR_READ`: consulta trayectos, planes vigentes, niveles y materias desde
Oferta académica; el ciclo se muestra como contexto de sus inscripciones y cursadas.
La navegación sigue los permisos efectivos; otro rol o una concesión explícita
puede habilitar los catálogos institucionales de planes y ciclos lectivos.

La migración `remove_student_default_academic_year_permission` sustituye los
permisos de catálogo por `ACADEMIC_OFFER_READ` en roles Estudiante institucionales
que conservan exactamente los dos permisos originales (`STUDY_PLAN_READ` y
`ACADEMIC_YEAR_READ`) o sólo `STUDY_PLAN_READ`. No modifica roles personalizados ni
roles Estudiante con otro conjunto de permisos. Los roles institucionales existentes
no se restablecen a los valores predeterminados al iniciar.

## Bootstrap de authority institucional

`BootstrapInstitutionalAuthorityUseCase` (endpoint `POST /api/v1/admin/institutions/{id}/authority/{personId}`) es la única forma de crear el primer `INSTITUTIONAL_AUTHORITY` de una institución. Solo `PLATFORM_ADMIN` puede ejecutarlo.

La primera authority no se puede crear con el endpoint regular de asignación de roles porque ese endpoint requiere permisos institucionales que todavía no existen.

## Protección del último authority

`RevokePersonRoleUseCase` (`src/main/java/.../authorization/services/RevokePersonRoleUseCase.java`) verifica que no se pueda revocar el último `INSTITUTIONAL_AUTHORITY` de una institución. La eliminación lógica de una persona aplica la misma regla. Ambas operaciones bloquean pesimísticamente la institución antes de contar y modificar autoridades, para que dos transacciones concurrentes no puedan eliminar cada una una autoridad distinta dejando a la institución sin ninguna. El conflicto se informa con HTTP 409.

## Roles iniciales al crear personas

Crear una persona con rol inicial `APPLICANT` no requiere privilegios adicionales. Solicitar cualquier rol inicial más poderoso requiere `INSTITUTION_ROLE_ASSIGN` o una cuenta `PLATFORM_ADMIN`. `InitialRoleAssignmentGuard` realiza esta comprobación antes de crear la persona y evita usar el endpoint de alta como escalada de privilegios.

## Exclusividad y permanencia de roles

Toda persona activa debe conservar al menos un rol. `RevokePersonRoleUseCase` rechaza con HTTP 409 la revocación del único rol asignado.

`APPLICANT` es un rol exclusivo y no puede coexistir con ningún otro rol, incluido `ADMINISTRATIVE`. `AssignPersonSystemRoleUseCase` realiza ambos cambios de forma atómica: asignar un rol distinto reemplaza `APPLICANT`, y asignar `APPLICANT` reemplaza los roles actuales. Este último reemplazo se rechaza con HTTP 409 si quitaría la última autoridad institucional. Las asignaciones y revocaciones bloquean pesimísticamente la institución para mantener estas reglas ante operaciones concurrentes.

## Cambio de roles sin revocar sesiones

Cuando se asigna o revoca un rol, la sesión del usuario **no se revoca**. Esto es una decisión deliberada de UX: como los permisos se resuelven dinámicamente desde la base de datos en cada request, el cambio de rol tiene efecto inmediato sin obligar al usuario a volver a loguearse.

`SessionRevocationService` (`src/main/java/.../authorization/services/SessionRevocationService.java`) centraliza el cierre de sesiones y refresh tokens para personas, usuarios, instituciones y cuentas plataforma. Los cambios de roles no lo invocan, pero la eliminación de una persona y la desactivación de una institución sí revocan sus sesiones.

## Edición de cuentas de plataforma

Solo `PLATFORM_ADMIN` puede editar una cuenta mediante `PUT /api/v1/admin/accounts/{id}`. El nombre, apellido y correo son obligatorios; la contraseña es opcional y, si se omite, se conserva la actual.

Cambiar únicamente el nombre o apellido mantiene las sesiones activas. Cambiar el correo o la contraseña revoca todos los refresh tokens y sesiones de la cuenta, y desaloja sus entradas del caché de actividad. Si un administrador modifica sus propias credenciales, debe volver a iniciar sesión con los nuevos datos.

## Desactivación de una institución

Solo `PLATFORM_ADMIN` puede cambiar el estado mediante `PATCH /api/v1/admin/institutions/{id}/status`.

Al desactivar una institución:

1. Se actualiza `Institution.active`.
2. Se buscan todas sus sesiones institucionales activas.
3. Se revocan sus refresh tokens.
4. Se desactivan las sesiones.
5. Se desalojan las entradas de caché de actividad.

Además, login, refresh y validación de access tokens comprueban el estado de la institución. La protección no depende solamente de que la revocación masiva haya finalizado correctamente.

## Usuario de desarrollo

`InstitutionalDevUserBootstrap` (`src/main/java/.../auth/services/InstitutionalDevUserBootstrap.java`) solo se activa en perfiles `dev` y `test`. Crea una persona y usuario de prueba con rol `APPLICANT`. Configurable mediante `app.institutional-dev-user.*`.

## Sesiones activas

El endpoint `GET /api/v1/auth/sessions` lista las sesiones activas del usuario autenticado, indicando cuál es la sesión actual. Útil para que el usuario vea desde qué dispositivos tiene sesión iniciada.
