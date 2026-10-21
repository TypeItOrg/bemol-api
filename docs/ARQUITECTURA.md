# ARQUITECTURA DEL SISTEMA

Este documento describe los principios arquitectónicos, la estructura modular y las decisiones técnicas fundamentales de la API de Bemol.

## 1. Stack Tecnológico

- **Lenguaje y Plataforma**: Java 21 LTS con Spring Boot.
- **Construcción y Dependencias**: Gradle con Wrapper versionado.
- **Base de Datos Principal**: PostgreSQL 18 (utiliza funciones nativas como `uuidv7()`).
- **Almacenamiento en Memoria / Caché**: Redis 7 (utilizado para blacklist de access tokens, cache de replay de refresh tokens, invalidación de sesiones y rate limiting).
- **Evolución del Esquema**: Flyway con migraciones versionadas por timestamp UTC.
- **Seguridad**: Spring Security con autenticación stateless (JWT) y resolución dinámica de permisos.
- **Almacenamiento de Archivos**: Abstracción `StorageService` con implementaciones `LocalStorageService` (disco local/volumen Docker) y `S3StorageService` (AWS S3).

## 2. Organización Modular por Features

El código fuente (`ar.edu.utn.frvm.typeit.bemol_api`) se estructura por dominio funcional en lugar de agrupar por capas técnicas horizontales:

| Módulo | Responsabilidad |
|---|---|
| `academic` | Gestión curricular: ciclos lectivos, trayectos formativos, planes de estudio, espacios académicos, niveles, correlatividades, cursos, comisiones, horarios y cursadas. |
| `auth` | Autenticación dual (institucional y de plataforma), sesiones de usuario, rotación y replay de refresh tokens, WebAuthn/Passkeys, verificación de email y recuperación de contraseñas. |
| `authorization` | Catálogo de roles y permisos del sistema, resolución dinámica de permisos con caché, y control de alcance limitado por trayecto formativo (`accessScope`). |
| `enrollment` | Circuito de admisión e inscripciones: postulaciones, asignación de cursos, validación de requisitos documentales, auditoría documental y estados de admisión. |
| `institutional` | Modelo multitenant: instituciones, personas, domicilios, sedes y divisiones territoriales (países, provincias, ciudades). |
| `common` | Componentes transversales: almacenamiento (`StorageService`), auditoría JPA (`Auditable`), logging de requests (`RequestLoggingFilter`), paginación y manejo base de excepciones. |
| `config` | Configuraciones de Spring: seguridad Web, OpenAPI/Swagger, reloj UTC (`TimeConfig`), auditoría JPA y serialización Jackson. |
| `search` | Especificaciones de consulta dinámica mediante JPA Criteria API. |
| `security` | Filtros HTTP de seguridad (`JwtAuthenticationFilter`), extractores de credenciales y contexto de ejecución. |

## 3. Arquitectura de Capas y Responsabilidades

Dentro de cada módulo se aplica una separación estricta de responsabilidades:

### Controladores REST (`controllers`)
- Publican la API HTTP versionada mediante el enum `Version` (por ejemplo, `Version.V1`).
- Definen los contratos de entrada y salida utilizando exclusivamente Java **Records** inmutables.
- Aplican autorización declarativa mediante anotaciones de permisos (`@RequirePermission`, `@RequireAnyPermission`).
- No contienen lógica de negocio ni acceden directamente a repositorios de persistencia.

### Casos de Uso de Aplicación (`services`)
- Cada operación de negocio se implementa en una clase use case específica con sufijo `*UseCase` y un único método público `execute()`.
- Orquestan transacciones explícitas (`@Transactional`), bloqueos de concurrencia, repositorios y notificaciones.
- Los métodos de solo lectura declaran `@Transactional(readOnly = true)`.
- No exponen detalles HTTP ni dependen de `HttpServletRequest` o clases de Spring Web.

### Entidades de Dominio Ricas (`entities`)
- Las entidades son dueñas de sus invariantes intrínsecas, transiciones de estado y cálculos de negocio.
- Exponen métodos de intención claros (`activate()`, `transitionTo()`, `ensureDraft()`, `withdraw()`).
- No utilizan `@Data` ni setters públicos indiscriminados que permitan mutaciones inconsistentes.
- Se instancian a través de fábricas estáticas con validación o constructores controlados.

### Repositorios (`interfaces`)
- Interfaces que extienden `JpaRepository` o `JpaSpecificationExecutor`.
- Centralizan consultas de lectura, proyecciones y bloqueos pesimistas (`PessimisticWrite`).

## 4. Persistencia, Concurrencia e Integridad

1. **Identificadores Estables**: Los identificadores de entidades primarias son UUID versión 7 (`uuidv7()`), ordenables temporalmente y generados por PostgreSQL o la aplicación.
2. **Multi-tenancy y Aislamiento Institucional**: Todas las tablas académicas, de personas y de inscripciones declaran `institution_id`. Las relaciones foráneas compuestas aseguran que no se puedan vincular recursos de distintas instituciones.
3. **Eliminación Lógica e Invariantes**:
   - Los recursos raíz utilizan `deleted_at`.
   - Para permitir la reutilización de identificadores naturales (como slugs, códigos o nombres en registros no eliminados), las restricciones de unicidad utilizan índices parciales activos:
     ```sql
     CREATE UNIQUE INDEX uk_entity_code_active ON entities (institution_id, code) WHERE deleted_at IS NULL;
     ```
4. **Serialización y Locks Pesimistas**: Para prevenir condiciones de carrera en operaciones críticas (como cupos de cursos, inscripciones concurrentes o cambios curriculares), el caso de uso adquiere un bloqueo pesimista sobre la institución o el recurso raíz dentro de la transacción.
5. **Evolución del Esquema**: Flyway gestiona el esquema de base de datos de manera exclusiva. Hibernate se ejecuta con `ddl-auto=validate`.

## 5. Convenciones Temporales y Zonas Horarias

Para evitar inconsistencias entre cliente, servidor y base de datos:

- **Instantes de Tiempo**: Fechas de creación, actualización, expiración, auditoría y sesiones utilizan `java.time.Instant` en Java y `timestamptz` (`timestamp with time zone`) en PostgreSQL. Se serializan en formato ISO-8601 con sufijo UTC (`Z`).
- **Fechas de Calendario**: Fechas de nacimiento, inicio de ciclo lectivo o días de cursada utilizan `java.time.LocalDate` en Java y `date` en PostgreSQL. Se serializan como `YYYY-MM-DD` sin conversión de zona horaria.
- **Reloj Centralizado**: `TimeConfig` expone un `Clock` UTC del sistema (`Clock.systemUTC()`). Los servicios nunca llaman directamente a `Instant.now()` o `LocalDateTime.now()`.
- **Fecha de Negocio**: `BusinessDateProvider` calcula la fecha actual en la zona de Argentina (`America/Argentina/Buenos_Aires`) exclusivamente para reglas de calendario (por ejemplo, cálculo de edad escolar o vigencia de un ciclo lectivo).

## 6. Tratamiento de Excepciones

El sistema maneja errores de forma estructurada y desacoplada de la capa web:

1. **Jerarquía**: Todas las excepciones de negocio extienden `ApplicationException`, la cual define un código de error, mensaje y una categoría `ErrorCategory` (`NOT_FOUND`, `CONFLICT`, `VALIDATION`, `FORBIDDEN`, `UNAUTHORIZED`, etc.).
2. **Mensajes Centralizados**: Los textos de error residen en clases `*Messages` propias de cada dominio (por ejemplo, `AcademicMessages`, `AuthMessages`).
3. **Mapeo HTTP**: `GlobalExceptionHandler` y `ApplicationExceptionHttpMapper` capturan las excepciones y construyen una respuesta uniforme `ExceptionPayload`, asociando el código de estado HTTP correspondiente sin exponer detalles internos de implementación.

## 7. Módulos Técnicos y Referencias

Para profundizar en la implementación de cada subsistema:

- [Autenticación y Autorización](AUTENTICACION.md) y directorio técnico [`docs/auth/`](auth/INDICE.md).
- [Desarrollo Local y Entornos](DESARROLLO-LOCAL.md).
- [Integración Continua y Calidad (CI)](INTEGRACION-CONTINUA.md).
- [Registro y Trazabilidad (Logs)](LOGS.md).
- [Módulo Académico](academic/OPERACIONES-DESTRUCTIVAS.md).
- [Módulo de Inscripciones](enrollment/FLUJO-INSCRIPCION-CURSOS.md).
- [Almacenamiento S3 y Local](storage/ALMACENAMIENTO-S3.md).

## Nombre de la aplicación

La aplicación se llama **Bemol**. Los identificadores operativos anteriores se
conservan cuando son necesarios para acceder a recursos existentes. Consultar
la guía `boero-infra/docs/RENOMBRE-BEMOL.md` antes de renombrar recursos persistentes.
