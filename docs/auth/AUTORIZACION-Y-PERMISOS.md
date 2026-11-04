# AUTORIZACIÓN Y PERMISOS

## ¿Por qué `getAuthorities()` devuelve vacío?

`User` (`src/main/java/.../auth/entities/User.java`) y `PlatformAccount` (`src/main/java/.../auth/entities/PlatformAccount.java`) devuelven `List.of()` de `getAuthorities()`.

Esto es intencional. No cargamos permisos ni en el JWT ni en las authorities de Spring Security. Cada resolución de autorización consulta los permisos actuales en la base de datos.

## ¿Por qué no usamos `@PreAuthorize`?

En lugar de `@PreAuthorize("hasAuthority('...')")`, el proyecto usa anotaciones propias (`@RequiresPermission`, `@RequiresAnyPermission`, `@RequiresPlatformRole`) manejadas por aspectos. Las razones:

- Centralizar la lógica de autorización en `AuthorizationService`
- Usar enums del proyecto (`PermissionCode`, `PlatformRoleCode`) como valores de anotación, sin necesidad de strings SpEL
- Un solo mecanismo para permisos institucionales y roles plataforma

## Resolución dinámica de permisos

`AuthorizationService` (`src/main/java/.../authorization/services/AuthorizationService.java`) es el punto de entrada:

1. Recibe el `Authentication` del `SecurityContext`
2. Determina el tipo de principal (`JwtAuthenticatedUser` vs `JwtAuthenticatedPlatformAccount`)
3. Delega en `AuthorityResolver` con los IDs correspondientes

### Para usuarios institucionales

`AuthorityResolver.resolveForPerson(personId, institutionId)`:

1. Lee las asignaciones de esa persona y esa institución, sus permisos y los trayectos de cada asignación.
2. `CachedAuthoritySnapshotResolver.resolveFreshForPerson` construye un `InstitutionalAuthoritySnapshot` con permisos efectivos, nombres de roles y un `PermissionAccess` por permiso, sin consultar la caché.
3. La unión de asignaciones se calcula por permiso. Tener lectura global y edición acotada conserva la edición acotada.

### Alcance de una asignación

`INSTITUTION` concede los permisos del rol para toda la institución, incluidos trayectos futuros. `TRAINING_PATHS` exige una selección no vacía y concede únicamente los permisos que `PermissionCode.supportsTrainingPaths()` permite limitar a esos trayectos.

El catálogo tiene 84 permisos: 40 admiten trayectos y 44 requieren alcance institucional. Dos de estos últimos son reservas de calificaciones y no son configurables. Las operaciones sobre usuarios, roles, ciclos lectivos y catálogos compartidos no se pueden limitar artificialmente a un trayecto.

Por ejemplo, un rol con **Ver usuarios** y **Ver cursos**, asignado sólo al Profesorado, concede **Ver cursos** en el Profesorado. **Ver usuarios** no se otorga desde esa asignación. Otro rol de la misma persona puede seguir concediendo **Ver usuarios** con alcance institucional.

La UI explica esta regla en el selector de alcance y permite desplegar los permisos que esa asignación no otorga. La comprobación efectiva pertenece al backend: la anotación verifica el permiso y las guardas y consultas verifican el recurso y su trayecto antes de devolver datos o modificarlo.

**Ver solicitudes de cursada** depende de **Ver solicitudes de inscripción**. El detalle institucional y su ruta compatible sólo incluyen las selecciones de cursada si ese permiso alcanza el trayecto de la solicitud. El acceso del titular mediante autoservicio y el de administración de plataforma se conservan. `EnrollmentApplicationCourseAuthorization` centraliza esa decisión; ocultar la sección en la UI no reemplaza este control.

### Para cuentas plataforma

`AuthorityResolver.resolveForPlatformAccount(platformAccountId)`:

1. `CachedAuthoritySnapshotResolver.resolveFreshForPlatformAccount` consulta los roles y permisos actuales de la cuenta mediante `PlatformAccountRoleRepository`, sin consultar la caché.
2. Convierte los códigos devueltos en permisos y roles de plataforma.
3. `resolvePlatformRoles` usa la misma lectura fresca para `@RequiresPlatformRole` y para la frontera administrativa.

## Anotaciones de autorización

| Anotación | Parámetro | ¿Qué verifica? |
|---|---|---|
| `@RequiresPermission` | `PermissionCode` | Que el principal tenga ese permiso específico |
| `@RequiresAnyPermission` | `PermissionCode[]` | Que el principal tenga al menos uno de los permisos |
| `@RequiresPlatformRole` | `PlatformRoleCode` | Que el principal sea cuenta plataforma con ese rol |

### Aspectos que las interceptan

- `PermissionAuthorizationAspect` — intercepta `@RequiresPermission` y `@RequiresAnyPermission`
- `RoleAuthorizationAspect` — intercepta `@RequiresPlatformRole`

Ambos leen el `Authentication` del `SecurityContext`, llaman a `AuthorizationService` y lanzan `AccessDeniedException` si la verificación falla (`AuthorizationAspectSupport.denyUnless()`).

## Guarda de llamada institucional

`@RequiresInstitutionAccess`, interceptada por `InstitutionAccessAspect`, protege controllers cuyo recurso está identificado por `institutionId`. Para principales institucionales exige que la institución del JWT coincida con la ruta. La implementación también admite cuentas de plataforma con `PLATFORM_ADMIN`; las rutas administrativas conservan sus propias anotaciones y la protección del namespace.

`InstitutionalCallerGuard` aplica la misma separación en operaciones de servicio o autorización que necesitan una comprobación explícita. Estas dos capas evitan que un usuario de la institución A lea o modifique recursos de la institución B cambiando un path variable.

## Frontera administrativa

Todas las operaciones de administración global viven bajo `/api/v1/admin/**`. Salvo `POST /admin/auth/login` y `POST /admin/auth/refresh`, Spring Security exige dinámicamente el rol `PLATFORM_ADMIN` para todo el namespace.

Los controllers administrativos conservan además `@RequiresPlatformRole(PLATFORM_ADMIN)` como defensa en profundidad. La gestión administrativa de personas y roles usa rutas propias bajo `/admin/institutions/{institutionId}/**`; no reutiliza las rutas institucionales ni sus permisos.

## Caché e invalidación

El resolver de snapshots conserva estas interfaces de caché:

| Caché | Clave conceptual | Contenido |
|---|---|---|
| `personAuthoritiesV3` | persona + institución | permisos, roles y alcances institucionales |
| `platformAuthoritiesV2` | cuenta plataforma | permisos y roles de plataforma |

`AuthorityResolver` usa exclusivamente las interfaces frescas para permisos, roles y alcances. Una lectura iniciada antes de un cambio de rol puede terminar después de su invalidación y volver a guardar el snapshot anterior; por eso una entrada cacheada no autoriza una operación posterior.

Las entradas Redis conservan su TTL de 5 minutos y `AuthorizationCacheInvalidator` desaloja las claves afectadas después del commit. Cambiar asignaciones o alcances también revoca las sesiones afectadas. El navegador deberá volver a autenticar esas sesiones y obtendrá los nuevos permisos. El seed limpia ambos cachés después de sincronizar el catálogo.

La delegación de permisos se revalida contra snapshots frescos después de adquirir el bloqueo de administración institucional. No se debe sustituir esa lectura por los permisos cacheados que tenía el actor antes de esperar un cambio concurrente.

## Trade-off

- **Ventaja**: una resolución iniciada después del commit de una reducción de permisos consulta la autoridad actual, sin esperar al TTL ni depender del orden de invalidación y llenado de caché entre instancias.
- **Costo**: cada resolución de permisos, roles o alcances consulta la base; un request puede realizar varias consultas de autorización.
- **Límite**: una operación cuya comprobación ya leyó el permiso antes del cambio sigue siendo una operación concurrente; esta lectura fresca no cancela operaciones en curso.

## Flujo completo

```
Request → JwtAuthenticationFilter (autentica) → Controller
  → @RequiresPermission (anotación) → PermissionAuthorizationAspect
  → AuthorizationService.hasPermission()
  → AuthorityResolver.resolveForPerson() → permisos actuales en BD
  → Si autorizado → ejecuta el método
  → Si falta el permiso → AccessDeniedException → 403
  → Si el recurso queda fuera del trayecto → ScopedResourceNotFoundException → 404
```

Los catálogos de roles de los formularios usan búsqueda y paginación. Los roles ya asignados se resuelven individualmente cuando no están en la primera página, para conservar sus metadatos y alcances. Las opciones institucionales permiten lectura de usuarios o de roles; consultar opciones no concede permisos de administración.

La [revisión de permisos del 22/10/2026](REVISION-PERMISOS-2026-10-22.md) detalla la matriz ejecutada, las correcciones y la evidencia de navegador.
