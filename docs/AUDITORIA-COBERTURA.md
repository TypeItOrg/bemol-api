# Matriz de cobertura de auditoría global

La cobertura complementaria del historial por entidad está en
[Hibernate Envers](ENVERS-COBERTURA.md); no reemplaza los eventos funcionales de
esta matriz ni amplía la captura de datos privados.

## Contrato

La cobertura se mide por **operación de negocio**, en administración, portal institucional y autogestión; no por cada INSERT de una tabla auxiliar. Un evento registra actor, institución, persona relacionada, recurso, fecha, petición y cambios efectivos. Se persiste en la misma transacción que la escritura; un rollback no deja un éxito auditado. Las operaciones sin cambios no generan eventos.

Los snapshots son listas explícitas de campos: no se guardan contraseñas, hashes, tokens, credenciales WebAuthn, documentos personales, domicilios, datos médicos, archivos ni texto libre privado. Para estos últimos se registra qué campo cambió, no su contenido. Los correos mantienen su vista independiente y su persistencia posterior al envío.

## Matriz de cobertura

`Existente` significa que el flujo ya escribe en la auditoría global. `Ampliar` identifica un faltante detectado en el inventario de controladores y casos de uso, no un filtro del frontend. La columna final distingue la instrumentación del código de los escenarios de integración que la comprueban. No significa un test independiente por cada acción.

| Entidad / agregado | Operaciones gestionables | Estado inicial | Cierre / evidencia |
|---|---|---|---|
| Instituciones y dirección/contacto institucional | Crear, editar, activar/desactivar, acceso público, logo | Existente | Conservado; regresión `InstitutionsDocumentsAuditPostgresIntegrationTest` |
| Personas y domicilio personal | Crear, editar por administración/autogestión, eliminar | Parcial: faltan edición y baja de persona | Implementado en edición administrativa/autogestión y baja; C2 (privacidad, identidad, no-op, rollback y filtro de persona) |
| Cuentas institucionales / invitaciones | Crear, acceso, invitar/cancelar/activar, verificar/cambiar correo, cambiar/restablecer contraseña | Parcial: faltan cambios de credenciales | Cambios de contraseña y correo conectados; C2 y regresión de registro, verificación, recuperación y acceso propio |
| Administradores de plataforma | Crear, editar perfil/credenciales, activar/desactivar | Parcial: falta edición | Edición y contraseña conectadas; C2 + regresión de acceso |
| Roles, permisos y asignaciones/alcances por trayecto | Crear, editar, eliminar, asignar, revocar, reemplazar, aprovisionamiento | Existente | Conservado; `GlobalAuditPostgresIntegrationTest` y regresión de autorización/asignaciones |
| Vinculaciones | Solicitar, aprobar, rechazar, desvincular, acceso propio | Existente | Conservado; regresión de autogestión, representación y resolución |
| Adjuntos de vinculación | Subir, eliminar | Ampliar | Implementado; C3 (subida/baja, actor real, representado y referencia de ambas personas) |
| Trayectos formativos | Crear, editar, estado, eliminar, restaurar | Ampliar | Implementado; C1 |
| Ciclos lectivos | Crear, editar, activar/cerrar, eliminar, restaurar | Ampliar; el cierre ya audita los cursos afectados | Implementado; C1 + C3 (cierre y efectos sobre cursadas/selecciones) |
| Espacios académicos (catálogo) | Crear, editar, estado, eliminar, restaurar | Ampliar | Implementado; C1 |
| Instrumentos | Crear, editar, estado, eliminar, restaurar | Ampliar | Implementado; C1 |
| Turnos | Crear, editar, estado, eliminar, restaurar | Ampliar | Implementado; C1 |
| Planes de estudio y versiones | Crear, editar, estado, versionar, eliminar, restaurar | Existente | Conservado; regresión `AcademicModulesAuditPostgresIntegrationTest` |
| Niveles, espacios de plan, instrumentos asociados y correlatividades | Crear, editar, quitar y cambiar asociaciones | Existente dentro del plan | Conservado; regresión `AcademicModulesAuditPostgresIntegrationTest` |
| Cursos, clases, docentes, días y horarios | Crear, reemplazar clases/asignaciones, estado, eliminar, restaurar | Existente dentro del curso | Conservado; regresión `AcademicModulesAuditPostgresIntegrationTest` y C3 |
| Definiciones documentales y requisitos por trayecto/nivel | Crear, editar, transferir, estado y asignaciones | Existente dentro del documento | Conservado; regresión `InstitutionsDocumentsAuditPostgresIntegrationTest` |
| Períodos de inscripción, ofertas y niveles habilitados | Crear, editar, estado, eliminar | Ampliar | Implementado; C3 (CRUD/estado, no-op y precisión de fecha almacenada) |
| Solicitudes y sus datos complementarios/selecciones | Iniciar, editar borrador, enviar, cancelar, aprobar provisoria/definitiva, rechazar | Parcial: faltan resoluciones administrativas | Resoluciones conectadas; borradores sólo por cambios efectivos; C3 + regresión de admisión/rechazo |
| Solicitudes de documentación adicional / requisitos de inscripción | Solicitar y cambiar requisitos | Ampliar | Implementado; C3. Los cambios de requisitos de catálogo se registran en Documentación; sincronización/subida/envío se reflejan en su operación de negocio |
| Adjuntos de inscripción y revisiones | Subir, reemplazar, eliminar, revisar | Historial documental separado, falta proyección global | Proyección global implementada sobre el historial existente; C3 (subir, reemplazar sin doble eliminación, retirar, observar y aceptar) |
| Opciones de curso de una solicitud | Seleccionar, rechazar, asignar cursada, lista de espera | Parcial: faltan resoluciones | Rechazos manuales/por cierre y lista de espera conectados; selección en borrador y asignación en cursada; C3 + regresión de cupos/concurrencia |
| Cursadas, horarios asignados e historial académico | Crear desde solicitud/manual, baja, situación académica, cierre de curso/ciclo | Historial de cursadas separado, falta proyección global | Proyección global implementada sobre historial de cursadas; C3 + regresión de baja y asignación. Motivos privados sólo como nombre de campo cambiado |
| Alumno | Alta derivada de la admisión/cursada; sin CRUD independiente | Cubrir junto al flujo de admisión/cursada | Cubierto por admisión/cursada; C3 + regresión `EnrollmentResolutionPostgresIntegrationTest` (creación/preservación de alumno) |
| Passkeys | Registrar, renombrar, revocar | Ampliar sin material criptográfico | Registro, cambio de nombre y revocación conectados; C4 (identidad, privacidad, no-op y constraint real) |
| Correo | Resultado SMTP aceptado/fallido de los tres flujos | Existente, vista Correos | Conservado en Correos; regresión `MailAuditPostgresIntegrationTest` |

## Entidades sin escritura gestionable independiente

- Países, provincias, ciudades, niveles de educación y permisos del sistema son catálogos de lectura/seed, sin endpoints de mantenimiento en esta plataforma. No se inventa un evento de administración inexistente.
- Tablas de relación (`RolePermission`, `PlatformAccountRole`, `PersonRoleAssignment`, `StudyPlanSpaceInstrument`, ofertas/niveles, horarios/selecciones) se auditan desde el agregado que las modifica, no duplicando un evento por fila.
- Tokens de verificación/recuperación/refresh, familias JWT, sesiones, ceremonias de autenticación, locks, secuencias y slots precalculados son infraestructura. Se audita la operación gestionable que los origina cuando corresponde; nunca sus secretos ni la rotación interna. No se agregan eventos por cada refresh o lectura.
- Los historiales y la propia auditoría son append-only; sus inserciones no generan auditoría recursiva. El mantenimiento técnico de retención no se presenta como una acción funcional del usuario.
- El acceso a contenido documental conserva su historial de seguridad separado; las lecturas normales no se añaden a la auditoría global de cambios.

## Evidencia de verificación

Se agregaron **tres escenarios integrados**, reutilizando infraestructura PostgreSQL, fixtures y regresiones existentes. No se añadieron tests unitarios nuevos; se adaptaron las fixtures afectadas por las nuevas dependencias.

- **C1 — `AcademicCatalogAuditPostgresIntegrationTest`**: los cinco catálogos, CRUD/estado/restauración, operación repetida, descripción privada, ambos tipos de actor, aislamiento institucional, rollback y filtros/paginación HTTP.
- **C2 — `GlobalAuditPostgresIntegrationTest.auditsPersonalChangesAndPasswordsWithoutPrivateSnapshotsOrNoopEvents`**: edición administrativa y propia, contraseñas y perfil de plataforma, eliminación, identidad, privacidad, no-op, rollback y búsqueda de persona.
- **C3 — `CourseEnrollmentFlowPostgresIntegrationTest.auditsPeriodAdmissionDocumentsAndEnrollmentAsOneTransactionalWorkflow`**: período/ofertas, borrador, documentación adicional, archivos/revisión, admisión provisoria/definitiva, vinculación con adjuntos, cursada, cierre y resultado/motivo académico. La regresión existente verifica además lista de espera, baja, rechazo y concurrencia.
- **C4 — `PasskeyConcurrencyPostgresIntegrationTest`**: se ampliaron dos escenarios existentes para comprobar auditoría de registro exitoso tras colisión real, renombrado y revocación, sin material criptográfico ni etiquetas privadas.

Los snapshots de períodos comparan fechas a la precisión de microsegundos de PostgreSQL/JDBC; reenviar el mismo dato no genera cambios falsos. Los borradores comparan respuestas y selecciones en memoria, pero sólo persisten nombres de campos, nunca respuestas médicas ni datos del responsable.

### Resultado local

- Backend: `spotlessCheck`, `codeStructureCheck`, `nullAwayMain`, `nullAwayTest`, `ecjMain`, `ecjTest` y suite completa `test`: **980 tests, 0 fallos, 0 errores, 0 omitidos**.
- Frontend: `pnpm lint` y TypeScript `--noEmit`; catálogo de 106 acciones consistente con la API.
- Ninguna migración ni configuración de despliegue modificada. Los cambios concurrentes de dependientes/vinculaciones permanecen fuera de esta entrega.

### Interfaz y límites

- Los nuevos módulos y acciones están disponibles en los filtros y su agrupación; el detalle reutiliza la vista existente. Los campos privados sólo muestran qué cambió. Las fechas/horas conservan el formato de 24 horas.
- Formato y controles de backend/frontend se verifican por separado de la aceptación visual. El intento de navegador local devolvió `ERR_CONNECTION_REFUSED`; no se acredita una comprobación visual de esta ampliación.
- El aprovisionamiento inicial de roles y sus tablas de relación pertenece al evento de alta de institución/cuenta, no a un evento duplicado por fila seed.
- No se requieren migraciones: acción, módulo derivado y tipo de recurso usan los campos existentes. Se preservan los scripts y la política Flyway actuales.
- No hay backfill: sólo se registran operaciones posteriores a esta instrumentación. No se han publicado commits ni desplegado esta entrega.

Al agregar una entidad u operación gestionable, actualizar esta matriz, su llamada transaccional al recorder y el catálogo de módulos/acciones/etiquetas de la interfaz. Las tablas auxiliares se cubren desde el agregado que coordina la operación.
