# Cobertura actual de Hibernate Envers

## Contrato

Envers complementa la Auditoría funcional, no la reemplaza. Está activado por
defecto con `spring.jpa.properties.hibernate.integration.envers.enabled=true`.
El cambio requiere reiniciar la aplicación y empieza a registrar revisiones nuevas;
no reconstruye valores anteriores a su activación. Puede deshabilitarse explícitamente
sobrescribiendo esa propiedad con `false`.

Los slices de tests existentes mantienen Envers apagado para aislar sus fixtures y
esquemas H2; las integraciones PostgreSQL de historial lo habilitan explícitamente.
El escenario de seguridad comprueba tanto el valor predeterminado activo de la
aplicación como el fallback al deshabilitarlo.
El detalle existente de Auditoría incorpora los cambios de Envers, sin agregar
endpoints ni pantallas independientes ni cambiar la configuración de despliegue.

La cobertura comprende **42 entidades**: las seis del piloto y otras 36. Hay 44
tablas de historial: 42 de entidad, una para los trayectos de asignaciones de rol
y la tabla común de revisiones. Se conserva el prefijo `envers_poc_` para mantener
compatibilidad con el piloto, aunque el alcance sea mayor.

Cada transacción conserva fecha, actor real y petición cuando está disponible.
Un rollback revierte también el historial. Sólo se incluyen campos explícitos;
las relaciones guardan referencias, no copias indiscriminadas del objeto asociado.

## Selección de campos

| Entidad | Valores historizados |
| --- | --- |
| `AcademicLevel` | `studyPlan`, `name`, `displayOrder` |
| `AcademicSpace` | `institution`, `name`, `type`, `format`, `active`, `instrumental`, `deletedAt` heredado |
| `AcademicYear` | `institution`, `year`, `startDate`, `endDate`, `status`, `deletedAt` heredado |
| `ApplicantPreference` | `enrollmentApplication`, `allowsImageUse`, `isReenrolling`, `deletedAt` heredado |
| `CourseEnrollment` | `institution`, `student`, `course`, `courseClass`, `source`, `enrollmentApplication`, `applicationCourse`, `status`, `academicStatus`, `enrolledAt`, `completedAt`, `withdrawnAt`, `actionAuthorityPersonId` |
| `CourseEnrollmentSchedule` | `institution`, `courseEnrollment`, `schedule`, `individualSlot`, `dayOfWeek`, `startTime`, `endTime`, `releasedAt` |
| `DocumentDefinition` | `institution`, `name`, `allowedFormats`, `active` |
| `EnrollmentApplication` | `institution`, `applicantPerson`, `submittedByPerson`, `studyPlan`, `trainingPathId`, `academicYear`, `enrollmentPeriod`, `status`, `resolvedAt`, `resolvedByPersonId`, `deletedAt` heredado |
| `EnrollmentApplicationCourse` | `institution`, `enrollmentApplication`, `course`, `enrollmentPeriod`, `preferredTeacher`, `status`, `requestedAt`, `submittedWithCapacity`, `waitlistNumber`, `waitlistedAt`, `waitlistReason`, `resolvedAt`, `resolvedByPersonId`, `resolutionReasonCode` |
| `EnrollmentApplicationSpace` | `enrollmentApplication`, `studyPlanSpace`, `instrument` |
| `EnrollmentAttachment` | `enrollmentApplication`, `requirement`, `versionStatus`, `reviewStatus`, `uploadedBy`, `uploaderType`, `reviewedBy`, `reviewerType`, `reviewedAt`, `contentType`, `fileSize`, `deletedAt` heredado |
| `EnrollmentDocumentRequest` | `application`, `institutionId`, `createdAt`, `actorId`, `accountType` |
| `EnrollmentDocumentRequirement` | `application`, `sourceRequirementId`, `name`, `level`, `allowedFormats`, `displayOrder`, `document`, `institutionId`, `definitionRevision`, `assignmentRevision`, `active`, `origin`, `request` |
| `EnrollmentPeriod` | `institution`, `academicYear`, `name`, `startDate`, `endDate`, `status`, `scopeConfigured`, `deletedAt` heredado |
| `EnrollmentPeriodOffering` | `period`, `studyPlan` |
| `EnrollmentPeriodOfferingLevel` | `offering`, `academicLevel` |
| `Institution` | `city`, `name`, `slug`, `publicSubdomain`, `logoContentType`, `logoVersion`, `logoSize`, `active` |
| `Instrument` | `institution`, `name`, `active`, `deletedAt` heredado |
| `PasskeyCredential` | `user`, `revokedAt` |
| `Person` | `institution`, `deleted` |
| `PersonAccessInvitation` | `person`, `representation`, `institutionalIssuer`, `issuingInstitutionId`, `expiresAt`, `consumedAt` |
| `PersonGuardian` | `institution`, `tutorPerson`, `dependentPerson`, `relationship`, `ownAccessRequested`, `primaryContact`, `status`, `resolvedBy`, `resolvedAt` |
| `PersonGuardianAttachment` | `institution`, `personGuardian`, `contentType`, `fileSize` |
| `PersonRoleAssignment` | `person`, `role`, `institution`, `accessScope`, `trainingPathIds` |
| `PlatformAccount` | `enabled` |
| `PlatformAccountRole` | `platformAccount`, `role` |
| `Prerequisite` | `studyPlan`, `targetStudyPlanSpace`, `requiredStudyPlanSpace`, `requirementStage`, `requiredCondition` |
| `Role` | `code`, `name`, `scope`, `system`, `institution` |
| `RolePermission` | `role`, `permission` |
| `Student` | `institution`, `person`, `status`, `enrollmentDate` |
| `StudyPlan` | `institution`, `trainingPath`, `previousVersion`, `versionNumber`, `name`, `effectiveFrom`, `effectiveTo`, `status`, `deletedAt` heredado |
| `StudyPlanSpace` | `institution`, `studyPlan`, `academicSpace`, `academicLevel`, `requirementType`, `displayOrder`, `approvalMode` |
| `StudyPlanSpaceInstrument` | `institution`, `studyPlanSpace`, `instrument` |
| `TrainingPath` | `institution`, `name`, `active`, `deletedAt` heredado |
| `TrainingPathDocumentRequirement` | `trainingPath`, `document`, `institutionId`, `level`, `displayOrder`, `active` |
| `User` | `institution`, `person`, `enabled`, `selfAccessEnabled`, `emailVerificationStatus`, `emailVerifiedAt` |

Se conservan además los campos ya seleccionados en `Shift`, `Course`,
`CourseClass`, `CourseClassTeacher`, `CourseClassDay` y `CourseClassSchedule`.
No se auditan automáticamente `createdAt`/`updatedAt` de mantenimiento ni versiones
de bloqueo optimista. La fecha de creación de una solicitud documental sí es un
dato de negocio explícito (`EnrollmentDocumentRequest.createdAt`).

## Exclusiones deliberadas

- Contraseñas, hashes, tokens, material y claves WebAuthn, contadores de login,
  etiquetas privadas de passkeys y fechas de uso frecuente.
- Nombres/documentos/contacto de personas y cuentas, domicilios personales e
  institucionales, respuestas médicas, datos del responsable y escolaridad privada.
- Nombres originales, rutas/keys y contenido de archivos; observaciones, motivos
  y otros textos libres privados. Los adjuntos conservan estado, tipo y tamaño.
- Descripciones e instrucciones privadas de catálogos y documentación.
- Tokens/sesiones, locks, secuencias, slots precalculados y bookkeeping de acceso
  a períodos. Los catálogos de lectura/seed sin mantenimiento tampoco se amplían.
- La propia Auditoría, Correos y los historiales append-only existentes, evitando
  duplicación recursiva. Los resultados SMTP siguen en su registro independiente.

Los campos privados excluidos no aportan su contenido a Envers. La Auditoría
funcional sigue indicando qué campo cambió sin conservar su contenido. Un cambio
de versión de catálogo o reemplazo físico puede generar revisiones de metadatos
seguros aunque el contenido modificado sea privado.
Por ejemplo, una edición de nombre personal conserva su evento funcional, no una
copia histórica del nombre. El nombre de un espacio académico sí tiene historial.

Las referencias permiten destinos no auditados mediante
`RelationTargetAuditMode.NOT_AUDITED`. En Hibernate 7.2, si el destino también
está auditado, Envers resuelve su revisión histórica; la opción no fuerza su
estado actual. Los catálogos no auditados se resuelven al estado actual. Para
datos preexistentes sin baseline, conservar el identificador no garantiza que
se puedan navegar todos los atributos del destino histórico. Una futura lectura
de agregados debe manejar esa ausencia y autorizar por institución, sin
serializar entidades JPA en bruto. Esta entrega no hace backfill.

## Borrados, colecciones y triggers

- Clases: se mantiene el borrado por entidad, en orden de dependencias, con un
  `flush` antes de crear el nuevo árbol. Las bajas y altas se conservan completas.
- Instrumentos de espacios de plan y permisos/asignaciones de rol: los caminos
  existentes ya eliminan entidades mediante JPA; no se sustituyen por consultas
  masivas. Envers conserva también el ID compuesto de `RolePermission` y la
  colección de trayectos de `PersonRoleAssignment`.
- Ofertas/niveles de período y otros hijos con `cascade`/`orphanRemoval`: se
  historizan las entidades hijas; no se duplica todo el agregado en su raíz.
- Fechas de período: creación y edición usan el redondeo a microsegundos de
  PostgreSQL/JDBC. Reenviar fechas con nanosegundos no genera revisiones Envers
  falsas ni eventos funcionales adicionales.
- Reducción de oferta: `EnrollmentPeriodScopeService` elimina mediante Hibernate
  las selecciones DRAFT que quedan fuera de alcance **antes** de eliminar niveles
  u ofertas. Mantiene el bloqueo institucional, las validaciones y la prohibición
  de reducir alcance con solicitudes enviadas. La baja no queda oculta en el
  trigger SQL. Se preserva su efecto sobre `updated_at` con una escritura sólo de
  mantenimiento, no auditada, en la misma transacción.
- El trigger de protección/limpieza se conserva como defensa de base de datos.
  SQL directo, migraciones o cascadas externas a Hibernate no generan revisiones
  Envers; no se presenta esa actividad técnica como cobertura automática.
- El aprovisionamiento que realmente crea o cambia entidades por Hibernate sí
  puede generar revisiones técnicas, aunque no haya un evento funcional por fila.

## Migración y datos existentes

La migración aditiva creada mediante `make migration expand_envers_entity_history`
es `src/main/resources/db/migration/20261018205721__expand_envers_entity_history.sql`.
Agrega 37 tablas y sus índices sin modificar tablas de negocio ni la migración
del piloto. Las claves foráneas del historial apuntan sólo a revisiones, no a
filas vivas, para preservar referencias tras bajas.

Los tipos se contrastaron con el DDL exportado por Hibernate 7.2.12.Final. Se
conserva precisión temporal de microsegundos; los enums históricos no duplican
las restricciones del estado vivo, y cada fila sí valida el tipo de revisión.
Las claves primarias permiten buscar por entidad/revisión; los índices adicionales
cubren institución y padre cuando corresponde.

No hay backfill ni reparación de revisiones anteriores. Activar Envers empieza
a registrar desde ese momento; una entidad preexistente no obtiene mágicamente
su estado anterior a la primera revisión.

## Verificación acotada

En la ampliación de cobertura se agregaron **dos tests integrados**, no tests unitarios:

1. `EnversCoveragePostgresIntegrationTest.coversBusinessEntitiesCatalogsJsonAndRoleScopesWithoutPrivateSnapshots`: inventario exacto de las 42 entidades, 44 tablas y exclusión de columnas privadas; cambios/no-op de catálogo, baja de nivel, JSON documental, permisos con clave compuesta, trayectos de rol, edición privada y baja de persona/cuenta, conservando eventos funcionales.
2. `EnversCoveragePostgresIntegrationTest.recordsOrphanRemovalsAndDraftScopeCleanupAtomicallyWithPrivateAttachmentMetadata`: reducción de niveles y ofertas con limpieza de borradores, rollback incluyendo timestamps, no-op con fechas de precisión nanosegundo, rechazo que preserva datos con solicitud enviada y estados/metadatos de adjuntos sin contenido privado.

Se amplía el test existente `EnversPilotSafetyPostgresIntegrationTest` para validar
el upgrade desde el piloto con datos y revisiones previas preservados, sin backfill.
El arranque normal con Flyway y `ddl-auto=validate` verifica instalación nueva.
Se mantienen los dos escenarios originales de turnos/cursos y las regresiones de
negocio, sin un test independiente por cada campo o entidad.

```sh
./gradlew integrationTest --tests '*Envers*PostgresIntegrationTest'
```

Resultado de esa ampliación: **985 tests, 0 fallos, 0 errores y 0 omitidos**, incluyendo los
cinco escenarios Envers (dos nuevos y tres existentes). También pasaron
`spotlessCheck`, `codeStructureCheck`, `nullAwayMain`, `nullAwayTest`, `ecjMain` y
`ecjTest`. Se mantienen los cuatro límites de estructura excepcionales existentes
sin aumentarlos. Los cambios previos de frontend, infraestructura y backend fuera
de este alcance se preservaron.

## Integración con el detalle de Auditoría

El evento funcional y la revisión comparten un identificador interno de transacción.
No se relacionan por fecha, actor ni identificador de petición. Una sincronización
Spring conserva el identificador durante `REQUIRED` y lo separa al suspender para
`REQUIRES_NEW`. No se fuerza la creación de revisiones: un cambio privado sin
columnas auditadas conserva el evento funcional, sin inventar historial de valores.

La migración aditiva
`20261018212657__link_audit_events_to_envers_transactions.sql` agrega columnas
UUID opcionales a eventos y revisiones, con un índice único en las revisiones.
No actualiza eventos append-only, tablas de negocio ni revisiones antiguas.

Sólo el detalle consulta el historial; el listado devuelve `history: null` y no
realiza comparaciones. La respuesta agrupa altas, modificaciones y bajas por
registro y muestra sólo campos distintos, con sus valores anteriores y nuevos.
Incluye las relaciones del agregado: clases, docentes, días y horarios del curso;
permisos y alcances de roles; ofertas y niveles; selecciones y documentación.
Los cambios representan el resultado neto de la misma transacción sobre ese
agregado, no estados intermedios de cada llamada dentro de ella.

La lectura usa una segunda lista explícita de tablas y columnas permitidas,
independiente de las anotaciones Envers. No serializa entidades ni recorre sus
proxies históricos. Nuevas columnas auditadas no se publican automáticamente.
Credenciales, nombres/documentos/contacto personales, formularios médicos,
observaciones privadas y rutas/nombres/contenidos de archivos siguen excluidos.
Las referencias se muestran como identificadores, sin reconstruir datos privados.
La comparación mantiene los nombres de los campos privados que cambiaron, sin
copiar sus valores.

Si no existe correlación (eventos anteriores a esta migración o Envers apagado),
se conserva el detalle funcional. Si la primera revisión es una modificación de
un registro existente, se muestra el estado disponible y se aclara que el anterior
no está registrado; no se interpreta como un alta ni se inventan valores.
Una baja física conserva el último estado o la fotografía de baja de Envers.
Una raíz sin baseline permite consultar hijos por su identificador estable, pero
no se infieren relaciones históricas que no estén registradas.

El frontend reutiliza las tablas existentes, con columnas Campo / Valor anterior /
Valor nuevo que ajustan sus textos también en móvil. El resumen prioriza al
actor, institución y contexto; no vuelca las referencias internas en la pantalla.
Información técnica, inicialmente plegada, sólo agrega contexto y la referencia
de la petición para soporte. Las fechas mantienen
Córdoba y formato de 24 horas. Los valores TIME se extraen con la misma zona JDBC
que Hibernate, no como texto crudo de PostgreSQL, para evitar desplazar horarios.
No aparecen bloques vacíos de historial.

La comprobación agrega un solo escenario PostgreSQL de correlación (petición
reutilizada, transacciones anidadas, rollback y endpoint), extiende los escenarios
existentes para renombres, primeras revisiones, bajas, relaciones y privacidad,
y agrega una prueba de render integrado del detalle y su fallback.

### Verificación de la integración previa a la activación

La regresión completa pasó con **986 tests, 0 fallos, 0 errores y 0 omitidos**.
Pasaron también `spotlessCheck` normal (sin el hook de formato dirigido),
`codeStructureCheck`, `nullAwayMain`, `nullAwayTest`, `ecjMain` y `ecjTest`.
El frontend pasó `pnpm lint`, `pnpm typecheck` y su única prueba nueva de render
integrado. La interfaz local estaba apagada: no se realizó validación visual en
navegador ni se levantaron servicios o activó Envers para simularla.

### Activación y verificación local

La aplicación ahora arranca con Envers activo por defecto. Se comprobó el arranque
real del servicio de desarrollo con `Envers integration enabled? : true` y readiness `UP`,
sin recrear bases, volúmenes ni migraciones. Los seis escenarios PostgreSQL de
Envers volvieron a pasar junto con `spotlessCheck`; no se agregaron tests nuevos.

Desde el navegador se creó `QA Envers - turno inicial`, se cambió su nombre a
`QA Envers - turno actualizado` y se lo dejó inactivo. Se conservaron las tres
revisiones y sus eventos funcionales correlacionados. La búsqueda del listado
mostró los tres eventos y el detalle mostró Antes / Después del nombre en el
estilo existente, con fechas de 24 horas y retorno que conserva la búsqueda.

Se cambió también la descripción con valores centinela: no quedaron sus valores
en Envers ni en los detalles de Auditoría. Sólo se informa el nombre del campo
modificado. La recarga limpia del detalle no produjo errores o advertencias del
navegador. El único error previo fue la navegación durante el reinicio de la API.
No se alteraron registros existentes; quedó un turno QA inactivo en la base local.

### Presentación tabular del detalle

El resumen muestra acción, fecha y hora, persona que actuó y contexto. No repite
el tipo de cuenta debajo del actor. Una sola sección Cambios combina los valores
de Envers con los campos funcionales adicionales; no muestra dos comparaciones
del mismo campo ni estados que no cambiaron. La coincidencia se limita a la raíz:
los campos de registros relacionados no se confunden con los de esa raíz.

Envers tiene prioridad para los valores historizados; si falta el estado anterior
de la raíz, se aprovecha el snapshot funcional seguro cuando existe. Sin revisión
se usan los snapshots funcionales. Roles y permisos conservan sus snapshots
semánticos completos, con nombres y alcances, en lugar de repetir referencias
técnicas de esas mismas asignaciones. Los datos de alta, acceso y documentación
usan las mismas columnas. Si sólo se conocen los nombres de campos modificados,
los valores se indican como No registrado, sin reconstruir datos privados.

Información técnica se limita al tipo de actor, origen e identificador de la
petición. No lista identificadores internos de entidades, roles, clases o personas
ni vuelca tablas crudas de Envers. Se conserva el historial completo en la base y
en el contrato del detalle; simplificar la presentación no elimina revisiones.

Se verificó el turno QA existente en navegador, en escritorio y a 390 px: una
sola tabla, sin duplicar Nombre ni mostrar Estado sin cambios, textos ajustados
sin desbordamiento horizontal, privacidad y formato de 24 horas. Pasaron formato,
lint, tipos y los dos escenarios existentes de render integrado, ampliados para
probar la unión, el fallback, roles y privacidad. No se agregaron tests unitarios
por campo ni se modificó el backend funcional.

### Comparación funcional de clases de cursos

La vista compara la configuración por número de clase y día de la semana, sin
interpretar un cambio de identificador de base de datos como un cambio funcional.
Reconstruye los estados a partir de las fotografías completas de altas y bajas
ya incluidas en la respuesta de Envers. Cupo, duración y conjunto de franjas
horarias se muestran antes/después; todas las franjas se ordenan y conservan.
Los cambios de docentes se indican sin publicar identificadores en la tabla
principal; las referencias originales permanecen en el historial almacenado.

El emparejamiento representa posiciones de configuración, no la continuidad de
identidad entre entidades borradas y recreadas. Las actualizaciones parciales no
se usan para inventar clases, días ni valores anteriores que no estén registrados.
Las referencias y bajas/altas técnicas se conservan sin volcarse en la interfaz.
No se modifica el evento, las revisiones, el contrato HTTP ni la persistencia.

Datos del evento, Cambios e Información técnica usan SectionHeader, con ícono,
título y descripción, como las demás secciones de detalle de la aplicación.

Se verificó el evento real del curso QA con cupo 20 → 25 y horario
09:00–10:00 → 10:30–11:30: la tabla principal muestra sólo esas dos filas y no
expone las 28 filas técnicas originales ni sus identificadores. Pasaron tres escenarios
integrados de UI y la integración PostgreSQL existente de reemplazo de clases,
rollback e historial completo. No se agregaron tests unitarios por campo.

## Límites de operación

- Sigue pendiente medir latencia, carga concurrente y crecimiento de almacenamiento.
  No se cambió batch size ni se agregó una cola: las revisiones son transaccionales.
- Reemplazos semánticamente iguales de clases/instrumentos aún pueden recrear IDs y
  generar revisiones técnicas; la Auditoría funcional conserva su criterio de no-op.
- No se incorporaron explorador general de revisiones, retención, backfill ni
  reconstrucción de entidades privadas. Los eventos antiguos no se correlacionan
  retroactivamente.
- No se realizaron commits ni despliegues a staging o producción.
