# Plan de implementación: altas autorizadas, representación y acceso propio

## Objetivo y decisiones cerradas

Implementar en Bemol API y Bemol UI, panel institucional y administración de plataforma:

- Eliminar el autorregistro público y crear cuentas mediante invitaciones, sin contraseñas elegidas por operadores.
- Exigir un permiso explícito para solicitar personas a cargo; no crear personas ni reservar documentos nuevos antes de aprobar.
- Todos los tutores activos administran el acceso propio de perfiles de autoservicio. La última decisión válida prevalece.
- Las cuentas docentes, administrativas o de autoridad permanecen bajo gestión institucional.
- La autonomía es manual, finaliza todos los vínculos y renueva las credenciales sin duplicar persona ni cuenta.

Fuera de alcance: automatizaciones por edad, delegación voluntaria posterior a la autonomía, supervisión especial de cuentas de personal, infraestructura, despliegues y publicación Git.

Los datos actuales son de prueba. No se implementa depuración histórica ni se ejecutan borrados por iniciativa del implementador. Toda recreación exige identificar y confirmar el destino descartable.

## Requisitos

### R01 — Alta interna e invitación de activación

- Retirar el endpoint y consumidores exclusivos del autorregistro. Quitar «Crear cuenta» del login; redirigir la antigua página al login e informar que el acceso se solicita a la institución.
- Reutilizar el alta interna en ambos paneles, sin contraseña ni confirmación. Crear persona y roles autorizados; crear `User` solamente al completar la activación.
- Conservar delegación de roles. Alta e invitación institucional exigen creación, asignación de roles y gestión del estado de usuarios; plataforma mantiene su autorización propia.
- Admitir emisores institucionales y de plataforma sin simular identidades institucionales.
- Enlaces aleatorios, con hash en persistencia, un solo uso y vigencia de 24 horas. Abrir no consume; confirmar correo y contraseña sí.
- Reenviar invalida el enlace anterior; cancelar impide utilizarlo.
- Conservar confirmación y reenvío para cuentas preexistentes que los necesiten, sin segundo paso para nuevas altas.

### R02 — Permiso y solicitudes de personas a cargo

- Incorporar `GUARDIAN_LINK_REQUEST`, `institution:guardian-link:request`, «Solicitar personas a cargo».
- Configurable mediante roles; sin concesión automática a Postulante/Estudiante. El administrador institucional debe poder delegarlo.
- Comprobar en backend. Retirarlo bloquea nuevas solicitudes, no consultas anteriores ni vínculos aprobados.
- Guardar documento, modalidad, identidad declarada, relación y documentación sin crear persona nueva.
- Conservar modalidades existente/nueva. Mientras está pendiente, el solicitante ve lo declarado, no la ficha institucional protegida.
- Impedir duplicados pendientes del mismo tutor/documento. Permitir solicitudes de otros tutores.
- Limitar a cinco solicitudes por minuto por actor e institución mediante Redis, sin nueva dependencia.

### R03 — Aprobación y materialización

- Conservar revisión institucional y de plataforma. Crear una persona exige también autorización de alta.
- En una transacción: bloquear raíz institucional, resolver otra vez el documento, crear/reutilizar persona y activar vínculo.
- Conservar unicidad institucional entre personas no eliminadas. Concurrencia crea como máximo una persona, sin sobrescribir datos institucionales con declaraciones.
- Si una identidad aparecida después difiere de lo declarado, exigir confirmación expresa del revisor antes de reutilizarla.
- Rechazo/cancelación no crean personas, cuentas o roles. Impedir autoaprobación.
- Aprobar puede emitir invitación solicitada, pero no activar cuentas ni cambiar credenciales existentes.
- Aprobar con acceso propio en «No» suspende el ingreso de una cuenta existente o cancela sus invitaciones pendientes; conserva el vínculo y exige la autorización de gestión de cuentas para suspenderla.

### R04 — Representación y acceso propio

- Separar ficha, representación, ingreso personal y bloqueo institucional. Una persona sin cuenta puede ser representada.
- Todos sus tutores activos pueden enviar/cancelar invitaciones y habilitar/suspender ingreso de perfiles de autoservicio.
- Serializar modificaciones: prevalece la última decisión válida confirmada.
- Suspender revoca sesiones/refresh y bloquea contraseña, llaves y renovación; no elimina llaves. La representación continúa.
- Recuperar contraseña no levanta suspensiones ni bloqueos institucionales; ningún tutor puede levantar estos últimos.
- Excluir cuentas con funciones de personal de la gestión de correo, recuperación e ingreso por tutores.
- Comprobar actor autenticado real y permisos propios, no privilegios seleccionados. No encadenar representaciones.

### R05 — Identidad, correo y contraseñas

- Nombre, apellido, documento y nacimiento se corrigen mediante gestión institucional autorizada. Ampliar formulario interno donde falten campos.
- Cambiar documento conserva identificador, respeta unicidad y revoca sesiones personales.
- Conservar edición ordinaria del resto de datos correspondiente, incluidos teléfono/domicilio.
- Titular cambia contraseña con contraseña actual; ocultar/rechazar sin cuenta o bajo representación.
- Retirar contraseñas de edición administrativa institucional y ofrecer recuperación asistida sin elegir/exponer contraseña nueva.
- Titular, tutores activos de autoservicio y administración autorizada pueden iniciar cambios de correo.
- Reautenticar al actor real en cambios sensibles; no exigir al tutor contraseña del representado.
- Nuevo correo permanece pendiente hasta confirmar. Token específico, separado de activación/recuperación, hasheado, temporal y de un solo uso; revalidar autorización del iniciador al confirmar.
- Confirmar cambia correo, notifica al anterior, invalida recuperaciones y revoca sesiones personales.
- Reutilizar intervalo existente de 60 segundos para reenvíos/solicitudes de correo. Una nueva solicitud invalida la anterior.
- Admitir correo alternativo/compartido, sin unicidad por correo. Advertir que recibirá activaciones y recuperaciones.

### R06 — Autonomía manual

- Operación institucional y de plataforma, sin automatización por cumpleaños.
- Exigir revisión de vínculos y salvaguardas de gestión de acceso contra actor real.
- Finalizar todos los vínculos activos y cancelar solicitudes pendientes sobre la persona, conservando historial.
- Invalidar invitaciones, recuperaciones y cambios de correo anteriores; revocar sesiones personales y llaves.
- Suspender ingreso hasta completar invitación institucional de toma de control al correo comprobado por la institución.
- Verificar correo y elegir nueva contraseña conservando `User` existente o creándolo si falta.
- No cambiar roles, inscripciones o identificadores; no levantar bloqueos institucionales.
- Invitación independiente de vínculos finalizados, reenviable aunque venza/falle el correo; nunca restaurar representaciones al reenviar.

### R07 — Seguimiento, auditoría e interfaz

- Diferenciar estado de cuenta e invitación: pendiente, vencida, utilizada o cancelada.
- Mostrar emisor, fechas, vencimiento y resultado de envío; correlacionar auditoría de correo e invitación.
- Enviado no significa entregado/leído. Registrar actor, sujeto, institución y acción, sin contraseñas/tokens/enlaces completos.
- Reutilizar componentes, confirmaciones, paginación, errores y navegación existentes; estados de carga y errores recuperables.
- Actualizar listas, detalles y selector tras cambios; nunca conservar facultades revocadas.
- Contraseñas solamente en activación, recuperación y cambio personal, no en alta/edición administrativa.

## Contratos y persistencia

- Retirar `POST /api/v1/auth/register` sin permitir escrituras por ninguna variante.
- Alta interna sin contraseña, respuesta de persona e invitación. Mantener prefijos institucionales/administrativos.
- Conservar rutas e ID de solicitud; admitir `dependentPersonId` nulo antes de materializar.
- Completar consulta/envío/reenvío/cancelación de invitaciones en ambos paneles.
- Extender acceso propio existente a cuentas creadas; booleano obligatorio, sin coerción de ausentes.
- Añadir recuperación asistida/cambio de correo privados, confirmación pública por token y autonomía por persona con propósito explícito.
- Respuestas públicas no enumeran cuentas. Respuesta de acceso incluye última invitación sin confundir bloqueo institucional.
- Tipos UI manuales, validación runtime y nulabilidad OpenAPI. Conservar autenticación de plataforma por correo e institucional por documento/institución.
- Solicitud con documento/modalidad persistidos y referencia nullable a persona; vínculo activo exige persona. Conservar tenant/unicidad y documentación/auditoría por ID de solicitud.
- Consultas no excluyen solicitudes sin persona. Invitaciones admiten plataforma, alta/autonomía y fechas diferenciadas de emisión/uso/cancelación.
- Persistir tokens de cambio de correo y actualizar Envers.
- Solo migraciones nuevas con `make migration <nombre>`; no reescribir anteriores.
- Adaptar seeder: solicitudes pendientes, vínculos activos, suspensión, múltiples tutores y personal. Sin limpieza histórica automática.

## Verificación autorizada

Pruebas focalizadas, PostgreSQL y navegador; no suites completas salvo autorización posterior.

| ID | Criterio de aceptación |
| --- | --- |
| R01 | Autorregistro sin escrituras; ambos paneles; activación, expiración, cancelación, reenvío, replay y consumo concurrente; correos sin contraseñas. |
| R02 | Permisos efectivos; pendiente no crea persona/cuenta/roles; duplicados, frecuencia y privacidad. |
| R03 | Concurrencia crea una persona, rollback íntegro, identidad existente preservada, rechazo/cancelación sin reserva nueva. |
| R04 | Dos tutores, última operación válida, suspensión de todos los métodos, representación conservada, no eludir bloqueo ni excepción de personal. |
| R05 | Sin edición directa de identidad/correo ni contraseñas administrativas; reautenticación, confirmación, notificación, revocación, caducidad y conflictos; recuperación no habilita. |
| R06 | Todos los vínculos/medios anteriores anulados, mismas identidades/historial, credenciales/llaves anteriores inválidas, activación independiente. |
| R07 | Permisos/estados API-UI consistentes; emisor/actor/sujeto/envío trazables sin secretos; vistas actualizadas. |
| Contratos/datos | Instalación limpia, migraciones, constraints/seeder; consultas, documentación, auditoría y respuestas con persona nullable. |

- API: unitarias de entidades/use-cases, HTTP/autorización, PostgreSQL para constraints/transacciones/concurrencia/revocación.
- UI: Jest focalizado, tipos, ESLint y formato. API: compilación, Spotless y análisis estático.
- Navegador: ambos paneles, tutor/titular/sin permiso, escritorio/móvil; datos ficticios y Mailpit local, sin SMTP externo ni entornos compartidos.
- Regresiones: actor vs identidad seleccionada, aislamiento institucional y prohibición de tomar privilegios del representado.

## Ejecución y matriz de integridad

Orden: persistencia/contratos → autorización/servicios → interfaces → seeder/documentación → verificación.

No marcar verificado por compilar o tener código. Registrar comandos, resultados, navegador, limitaciones y desviaciones aprobadas. No eliminar requisitos silenciosamente.

| ID | Estado | Cambios | Pruebas/comandos y resultado | Navegador | Limitaciones o comprobación pendiente |
| --- | --- | --- | --- | --- | --- |
| R01 | Implementado | Autorregistro y consumidores retirados; altas sin contraseña; invitaciones institucionales/plataforma; `User` diferido; propósito y origen explícitos. | API/HTTP y PostgreSQL: alta, hash, replay, cancelación, reenvío, caducidad y consumo concurrente aprobados. Confirmación/reenvío de cuentas legadas conservados y probados. Mailpit: dos correos sin contraseñas, dos personas y cero `User` antes de activar. | Altas reales ficticias desde ambos paneles; listas actualizadas; formularios sin contraseña objetivo; estado, emisor y vencimiento visibles. | Falta completar activación con elección de contraseña en navegador por intervención humana. La activación sí está comprobada por API/PostgreSQL y componentes Jest. |
| R02 | Verificado | Permiso explícito delegable, sin concesión a Postulante/Estudiante; documento/modalidad/declaraciones sin persona; duplicados por tutor/documento; Redis cinco/minuto; cancelación por ID de solicitud. | PostgreSQL y unitarias: denegación/concesión, documento no reservado, duplicados, privacidad, límite y cancelación sin persona aprobados. Documentación adjunta a solicitud sin persona probada en `GuardianLinkAttachmentServiceTest`. Catálogo/dependencias de permisos y Jest aprobados. | Tutor ve pendientes declaradas; solicitante sin permiso conserva consulta, no ve «Solicitar vinculación» y obtiene «Sin acceso» al abrir la ruta de alta. | El error de límite tiene código `GUARDIAN_REQUEST_LIMIT`; retirar el permiso afecta nuevas solicitudes, no los vínculos ni el historial. |
| R03 | Verificado | Transacción bajo bloqueo institucional; resolver nuevamente el documento; alta/reutilización; confirmación expresa de ficha divergente; sin autoaprobación ni sobrescritura; control de duplicados tras corregir documento. | PostgreSQL: dos aprobaciones concurrentes materializan una persona; colisión requiere confirmación; rollback íntegro; rechazo/cancelación no crean persona. Unitarias y HTTP de revisión aprobados. | Se visualizaron pendientes sin persona y datos declarados; diálogo de confirmación de ficha probado con Jest. | La aprobación desde navegador no se ejecutó; su transacción y autorización se comprobaron por HTTP/servicios/PostgreSQL. |
| R04 | Implementado | Actor real, sin encadenar representaciones; todos los tutores activos; suspensión separada del bloqueo institucional; sesiones/refresh revocados; llaves conservadas pero inutilizables mientras esté suspendido; exclusión de personal. La ruta antigua de acceso delega al mismo servicio que la nueva. | PostgreSQL: dos tutores habilitan/suspenden, sesiones y refresh quedan revocados, llaves conservadas, personal/bloqueo no eludibles. HTTP con JWT/sesiones reales niega tomar privilegios de la identidad seleccionada. Regresiones focalizadas de contraseña/llaves aprobadas. | Perfil sin `User` representable; cuentas suspendidas y de personal diferenciadas; personal no ofrece gestión al tutor; no aparecen contraseña/llaves del representado. Diálogo probado en escritorio/móvil. | Falta ejercicio de autenticación WebAuthn con una llave real en navegador. No se creó ninguna credencial de hardware ni se afirma verificación física. |
| R05 | Implementado | Identidad corregible por institución; documento mantiene ID y revoca sesiones; contacto ordinario conservado; sin claves administrativas objetivo; correo pendiente con token separado, reautenticación del actor, revalidación del iniciador, notificación al anterior y revocación. Retirada corrección pública directa legada. | PostgreSQL: correo no cambia antes de confirmar; reemplazo/replay/caducidad; contraseña del actor incorrecta; pérdida de vínculo; aislamiento institucional; notificación y revocación. Recuperación no habilita. Corrección de documento mantiene `User`/persona y revoca sesiones. HTTP impide la ruta pública antigua; Jest de esquemas/formularios/acciones aprobado. | Identidad y correo de solo lectura en perfil ordinario; teléfono/domicilio editables; corrección institucional incluye documento/nacimiento; formularios administrativos sin nueva contraseña objetivo; reenvío legado sin opción pública de cambiar correo. | Falta confirmar cambio/recuperación de contraseña de extremo a extremo en navegador con intervención humana. Las entradas sensibles administrativas solicitan únicamente la contraseña actual del actor para reautenticarlo. |
| R06 | Implementado | Autonomía manual cierra todos los vínculos/pendientes, invalida invitaciones/recuperaciones/cambios de correo, revoca sesiones/llaves y suspende ingreso; invitación independiente conserva `User` o lo crea al activar; contraseña anterior no reutilizable; sin modificar roles/IDs/inscripciones/bloqueo. | PostgreSQL: mismos identificadores, vínculos finalizados, llave anterior revocada, contraseña anterior rechazada y nueva activación independiente válida. Servicios comparten bloqueo/invalidación y conservan propósito de autonomía al reemitir una invitación pendiente/vencida/cancelada. | Controles y confirmación explícita de correo/efectos implementados; validación de la acción comprobada con Jest. | Falta completar operación y toma de control en navegador con intervención humana. No se permite tramitar la propia autonomía institucional: otra autoridad evita invalidar al emisor. No hay automatización por edad. |
| R07 | Verificado | Cuenta/invitación/bloqueo separados; emisor con nombre, fechas y resultado de envío; auditoría correlacionada por invitación; actor/permisos propios explícitos en `/auth/me`; revalidación de vistas y selector; cargas y errores recuperables. | Auditoría PostgreSQL/Envers/correo y Jest de metadatos/acciones aprobados. Mailpit: dos envíos `SENT`, dos eventos de correo correlacionados, sin contraseñas ni enlaces/tokens en auditoría. Tipos, ESLint y formato aprobados. Detector UI sin hallazgos en 28 objetivos. | Plataformas e institución muestran invitación independiente y «Correo enviado (no confirma entrega ni lectura)»; alta actualiza listado; selector cambia perfil y oculta facultades de credenciales ajenas. | No se comprobó entrega/lectura real ni SMTP externo. Los recorridos manuales pendientes de R01/R04/R05/R06 siguen pendientes, no se dan por verificados por compilar. |
| Contratos/datos | Verificado | Dos migraciones nuevas generadas por `make migration`; persona nullable en pendientes y obligatoria en activos; constraints de tenant/unicidad; tokens de correo, origen/propósito de invitación, Envers y seeder aditivo. | PostgreSQL 18: instalación limpia y upgrade desde historial previo; constraints, transacciones, auditoría y consultas con sujeto nullable aprobados. Seeder ejecutado dos veces en base nueva `auth_qa`, sin sobrescribir registros: pendiente sin persona, dos tutores, autoservicio suspendido y personal. Compilación/estáticos aprobados. | Listados y detalles funcionan con solicitudes sin persona y con personas sin cuenta. | No se ensayó una actualización sobre datos reales ni se desarrolló limpieza histórica. Las pruebas históricas de backfill se aíslan en su esquema anterior. Ninguna migración existente fue reescrita. |

## Resultado de la verificación — 27/10/2026

- **API:** 212 pruebas focalizadas en 35 clases, cero fallos/errores/omitidas. Incluyen entidad/use-case, contratos JSON, HTTP, separación de actor, verificación legada y credenciales.
- **PostgreSQL 18:** 49 pruebas focalizadas en ocho clases, cero fallos/errores/omitidas. Incluyen Redis, constraints, instalación limpia, upgrade, concurrencia, rollback, revocación, auditoría y Envers.
- **UI:** 123 pruebas Jest en 19 archivos, cero fallos. TypeScript `--noEmit`, ESLint y Prettier sobre los archivos cambiados aprobados.
- **Estáticos API:** `compileJava`, comprobación normal `spotlessCheck`, `staticAnalysis` (ECJ/NullAway principal y pruebas) y `codeStructureCheck` aprobados: 1112 archivos Java, cuatro excepciones de estructura existentes, sin ampliar sus umbrales.
- **Diseño mecánico:** detector Impeccable ejecutado una vez sobre 28 objetivos UI, sin hallazgos. No se rediseñó la aplicación ni se cambiaron sus tokens/patrones.
- **Navegador real:** ambos paneles, tutor, titular y actor sin permiso; escritorio y viewport móvil efectivo de 390 × 844 CSS. No hubo desborde horizontal de la página o diálogo en las mediciones realizadas. La comprobación móvil es emulación de viewport, no un dispositivo físico.
- **Correo local:** Mailpit en red interna sin SMTP externo. Los dos correos de alta llevan enlace de activación, no contraseñas. `SENT` no se interpreta como entregado/leído.
- **Pendiente manual:** ingreso de contraseñas nuevas en navegador y uso de una llave WebAuthn real. Por eso R01/R04/R05/R06 permanecen como **Implementado**, no como verificación integral cerrada. No hay desviaciones de alcance aprobadas ni requisitos eliminados silenciosamente.

### Comandos y conjuntos focalizados

Directorio API: `/home/matias/Workspace/Repositories/TypeIt/bemol-api`.

```sh
./gradlew --offline compileJava spotlessCheck staticAnalysis codeStructureCheck
```

El formato se aplicó únicamente a Java cambiado con el soporte existente `-PspotlessIdeHook=<rutas>`; después se ejecutó `spotlessCheck` normal, sin ese parámetro. No se usó el resultado omitido del hook como evidencia de comprobación.

`fastTest` se ejecutó con `--tests '*<clase>'` para cada clase siguiente, no con la suite completa:

- `AuthControllerWebMvcTest`
- `AuthPayloadJsonTest`
- `AuthorizationBoundaryTest`
- `CreatePersonUseCaseTest`
- `EmailVerificationControllerWebMvcTest`
- `GetCurrentUserUseCaseTest`
- `GuardianDependentControllerWebMvcTest`
- `GuardianLinkAttachmentServiceTest`
- `GuardianLinkReviewControllerWebMvcTest`
- `InitialRoleAssignmentGuardTest`
- `InstitutionalEmailVerificationUseCaseTest`
- `ListAndUnlinkGuardianDependentUseCaseTest`
- `PasskeyControllerSecurityWebMvcTest`
- `PasskeyControllerWebMvcTest`
- `PasskeyUserCredentialRepositoryTest`
- `PeopleControllerWebMvcTest`
- `PermissionAccessTest`
- `PermissionCatalogControllerWebMvcTest`
- `PermissionCodeDependencyTest`
- `PermissionRoleSeedTest`
- `PersonGuardianTest`
- `PersonOwnAccessServiceTest`
- `PlatformAuthControllerWebMvcTest`
- `PlatformPeopleControllerWebMvcTest`
- `QaInstitutionalHostIsolationTest`
- `ReAuthenticateUseCaseTest`
- `RegisterGuardianDependentUseCaseTest`
- `RequestInstitutionalPasswordRecoveryUseCaseTest`
- `RequestPasskeyAuthenticationOptionsUseCaseTest`
- `ResolveGuardianLinkUseCaseTest`
- `UpdateInstitutionalUserStatusUseCaseTest`
- `UpdatePersonByAdminUseCaseTest`
- `UpdatePersonUseCaseTest`
- `VerifyPasskeyAuthenticationOptionsTest`
- `VerifyPasskeyRegistrationOptionsTest`

La ronda PostgreSQL ejecutó:

```sh
./gradlew --offline integrationTest \
  --tests '*AuthorizedAccountsPostgresIntegrationTest' \
  --tests '*AuthorizedAccountsMigrationPostgresIntegrationTest' \
  --tests '*PersonGuardianMigrationIntegrationTest' \
  --tests '*GlobalAuditPostgresIntegrationTest' \
  --tests '*MailAuditPostgresIntegrationTest' \
  --tests '*EnversCoveragePostgresIntegrationTest' \
  --tests '*DependentApplicantRoleBackfillMigrationIntegrationTest' \
  --tests '*EmailVerificationPostgresIntegrationTest'
```

Directorio UI: `/home/matias/Workspace/Repositories/TypeIt/bemol-ui`. Se utilizaron las herramientas ya instaladas, sin instalaciones ni cambios de dependencias:

```sh
node node_modules/typescript/bin/tsc --noEmit
node node_modules/eslint/bin/eslint.js <archivos cambiados>
node node_modules/prettier/bin/prettier.cjs --check <archivos cambiados>
node node_modules/jest/bin/jest.js --runInBand --runTestsByPath <archivos focalizados>
```

Archivos Jest focalizados (rutas relativas al directorio UI indicado):

- `src/features/people/schemas/person-form.schema.test.ts`
- `src/features/people/actions/update-person.action.test.ts`
- `src/features/people/actions/manage-account-access.action.test.ts`
- `src/features/people/components/account-invitation-details.test.tsx`
- `src/features/institutional-auth/components/confirm-account-email-form.test.tsx`
- `src/features/institutional-auth/components/institutional-profile-form.test.tsx`
- `src/features/institutional-auth/actions/institutional-host-binding.actions.test.ts`
- `src/features/institutional-auth/components/institutional-branding.test.tsx`
- `src/features/institutional-auth/components/institutional-account-tabs.test.tsx`
- `src/features/guardian-dependents/components/guardian-dependents-list.test.tsx`
- `src/features/guardian-dependents/components/unlink-guardian-dependent-dialog.test.tsx`
- `src/features/guardian-links/components/guardian-link-requests.test.tsx`
- `src/features/guardian-dependents/actions/create-guardian-dependent.action.test.ts`
- `src/features/institutional-auth/utils/institutional-permission.util.test.ts`
- `src/features/institutional-auth/utils/institutional-applicant-role.util.test.ts`
- `src/features/institutional-auth/utils/institutional-navigation.util.test.ts`
- `src/features/institutional-auth/schemas/institutional-user.schema.test.ts`
- `src/features/institutional-auth/components/email-verification-form.test.tsx`
- `src/features/institutional-auth/actions/email-verification.actions.test.ts`

### Evidencia y entorno

Capturas conservadas fuera del código, en `/home/matias/.codex/visualizations/2026/10/27/01a17e73-7e99-7363-b20c-17fdcfe28b24/`:

- `invitacion-plataforma-escritorio.jpg`: emisor, vigencia de 24 horas y resultado de correo, sin afirmar entrega.
- `invitacion-plataforma-movil.jpg`: adaptación del panel de invitación.
- `tutor-acceso-escritorio.jpg` y `tutor-acceso-movil.jpg`: cuenta suspendida gestionable por tutor.
- `sin-permiso-movil.jpg`: consulta sin facultad de solicitar nuevas personas a cargo.
- `reenvio-legado-movil.jpg`: confirmación/reenvío conservados; corrección pública directa retirada.

Logs temporales: `/tmp/bemol-final-focused.log`, `/tmp/bemol-postgres-final.log`, `/tmp/bemol-final-static.log`, `/tmp/bemol-ui-final-focused.log`, `/tmp/bemol-ui-typecheck.log`, `/tmp/bemol-ui-lint.log`, `/tmp/bemol-ui-prettier.log` y `/tmp/bemol-ui-impeccable.json`. Son auxiliares temporales; la evidencia durable reproducible es el código de pruebas y esta matriz.

- API y UI permanecen en `develop`; índices vacíos, sin commits, push ni despliegue. No se modificaron infraestructura, dependencias ni entornos reales.
- No se leyeron archivos de entorno reales con secretos ni se recrearon bases o volúmenes preexistentes.
- QA utilizó una base nueva exclusiva `auth_qa`, PostgreSQL 18/Redis/Mailpit creados para esta tarea y almacenamiento temporal. El seeder se ejecutó dos veces de forma aditiva. Testcontainers usó sus propias bases efímeras.
- Al terminar se detuvieron API/UI de QA y se retiraron únicamente los tres contenedores, sus volúmenes anónimos y su red nuevos, comprobando IDs y etiquetas de propiedad. Se conservaron logs/capturas y se restauró el viewport; no se borraron datos preexistentes.
- El pnpm disponible no coincide con 12.3.4; se ejecutaron CLIs instaladas mediante `node`. DevTools no estaba en la caché offline de API: un init-script temporal lo excluyó del paquete de QA, sin editar el build del repositorio.
- Se conservan confirmación y reenvío de cuentas legadas. Su antigua corrección pública directa de correo fue retirada para que no eluda el nuevo flujo pendiente de confirmación; esas correcciones se solicitan a la institución.
- Los controles institucionales de usuarios y revisión de vínculos usan permisos del actor real, no los del perfil seleccionado. Los permisos de la identidad seleccionada siguen disponibles para las operaciones académicas de representación que ya correspondían.
- El historial finalizado/cancelado permanece consultable sin devolver acceso a la ficha protegida ni facultades de representación.

## Key Learnings:

1. Persona, vínculo de representación, ingreso personal y bloqueo institucional son estados independientes; los permisos de la identidad seleccionada no autorizan la administración del actor.
2. La autonomía manual conserva persona/cuenta y cierra vínculos/credenciales anteriores; su invitación de toma de control debe poder funcionar sin esos vínculos.

## Ampliación aprobada: Representante

Esta ampliación conserva los requisitos y las evidencias anteriores. Sus resultados se registran por separado: no se heredan las verificaciones de R01–R07 como evidencia del nuevo rol.

### Resultado y contratos

- Agregar únicamente `REPRESENTATIVE`, mostrado como **Representante**, al catálogo general y al aprovisionamiento de instituciones actuales y nuevas, sin migraciones de esquema.
- Su único permiso inicial será `GUARDIAN_LINK_REQUEST`, con alcance institucional. Conservar la personalización institucional y la idempotencia; no concederlo automáticamente a Postulante o Estudiante ni asignar Representante a personas existentes por sus vínculos.
- Permitir Postulante + Representante en ambos órdenes. Conservar los demás conflictos de Postulante; al pasar a Estudiante se elimina Postulante y se conserva Representante.
- Aplicar la misma excepción en la eliminación de asignaciones, la autorización de revocaciones implícitas y el reemplazo completo. Mantener autoridad protegida, último rol, delegación, auditoría, bloqueos y revocación de sesiones/caché.
- Conservar `initialRole` singular y las rutas de alta; admitir `REPRESENTATIVE`. Los consumidores anteriores que omiten el campo siguen obteniendo Postulante.
- En ambos paneles, exigir selección explícita de Postulante o Representante al crear. Incorporar el segundo rol desde el editor existente; validar el campo exclusivamente en creación, nunca en identidad o edición.
- Reutilizar permisos del actor real, historial y vínculos aprobados, selector de identidad y guardas académicas. Representante por sí solo no permite inscripción, materias ni horarios propios.
- Aprobar como representada a una persona que ya tiene Representante conserva sus roles: no agrega Postulante automáticamente. Para iniciar actividad académica propia se usa la asignación institucional existente.
- No introducir rol Usuario, permisos individuales, nuevos tipos de cuenta, delegación voluntaria, cambios de autonomía, infraestructura ni publicación Git. Preservar los cambios actuales de perfil, imágenes, correos e invitaciones.

### Matriz de integridad de la ampliación

| ID | Estado | Cambios | Comprobación focalizada | Navegador | Limitaciones |
| --- | --- | --- | --- | --- | --- |
| REP01 — Catálogo | Verificado | Enum, defaults y aprovisionamiento idempotente y personalizado. | Unitarias/PostgreSQL: instituciones nuevas/existentes, repetición, permiso único y personalización conservada. | Representante disponible en el alta de ambos paneles y en el editor de plataforma. | Sin asignación automática por vínculos ni migración de esquema. |
| REP02 — Composición | Verificado | Predicado compartido para la excepción Postulante + Representante, sin revocaciones ficticias; reemplazo completo adaptado. | Unitarias/HTTP/PostgreSQL: ambos órdenes, transición a Estudiante, conflictos previos, autoridad, último rol y delegación; aprobación conserva roles. | Plataforma: par Representante + Postulante conservado. Institución: alta Postulante, agregado de Representante y transición de Postulante + Representante a Estudiante + Representante confirmados visualmente y al guardar. | Pendiente visual anterior cerrado en la revisión de estándares registrada al final. |
| REP03 — Alta | Verificado | Selector explícito solo en creación; serialización/Server Actions y tipos; contrato API singular compatible. | Jest/HTTP/PostgreSQL: valores ausentes/desconocidos, ambos paneles, activación e invitación con `User` diferido. | Alta de Representante desde plataforma e institución; formularios sin preselección y sin contraseñas en escritorio/móvil; dos correos capturados localmente. | Activación comprobada por HTTP/PostgreSQL, sin ingresar credenciales nuevas en navegador. Se repitieron alta Representante en plataforma y Postulante en institución; selector compartido cubierto en ambos modos. Capturas finales sin recorte, detalladas al final. |
| REP04 — Acceso | Verificado | Reutilizar separación entre actor, perfil, solicitudes y actividad académica; sin cambiar guardas académicas ni selector. | Jest/HTTP/PostgreSQL: tres perfiles, retirada del permiso conservando vínculos/historial, actor real, aislamiento y aprobación sin conversión automática. | Los tres perfiles en escritorio/móvil; Representante sin opciones académicas propias; cambio a Estudiante representada y consulta efectiva de sus materias/horarios. | Fallo previo del actor real corregido y regresiones aprobadas. Se conservan abajo el diagnóstico anterior y las evidencias de su cierre. |
| REP05 — Datos y estáticos | Verificado | Tres escenarios aditivos; seeder integrado a su transacción; documento previo conservado. | Seeder repetido y preservación de cambios posteriores en PostgreSQL nuevo; compilación, Spotless, estructura, ECJ/NullAway, TypeScript completo, ESLint y Prettier aprobados. | Solo personas ficticias y Mailpit aislado. | Suites completas, CI, despliegue y dispositivo físico no ejecutados. Ningún dato/volumen preexistente recreado. |

### Verificación prevista

Unitarias/HTTP, integración PostgreSQL y Jest focalizados, compilación y controles estáticos existentes; sin suites completas. Navegador en ambos paneles con los tres perfiles de la tabla funcional, en escritorio y móvil, con datos ficticios y correo capturado localmente. Toda comprobación pendiente o fallo ajeno se declarará expresamente.

### Escenarios aditivos de demostración

`enrollment-demo-representatives.sql`, integrado al seeder manual y a su misma transacción, agrega tres personas ficticias nuevas: Representante, Postulante + Representante y Estudiante + Representante. Cada una tiene un vínculo activo con la estudiante de demostración existente. Si una persona de estos escenarios ya existe por su identificador estable, no se vuelven a insertar sus roles, cuenta ni vínculos: una repetición no revierte sus cambios posteriores. No se reclasifican las personas anteriores del dataset.

### Evidencia de esta ampliación — 27/10/2026

Los resultados siguientes corresponden exclusivamente a Representante, no reemplazan los anteriores de R01–R07.

| Capa | Comando o comprobación | Resultado final |
| --- | --- | --- |
| API unitarias | `./gradlew --offline compileJava fastTest` con filtros de `RoleTest`, `AssignPersonSystemRoleUseCaseTest`, `AssignPersonRoleUseCaseTest`, `ReplacePersonRolesUseCaseTest`, `InitialRoleAssignmentGuardTest`, `CreatePersonUseCaseTest` y `PermissionRoleSeedTest`; repetición final de las siete clases. | 58 casos, cero fallos/errores/omitidos. |
| API HTTP/PostgreSQL | `./gradlew --offline integrationTest --tests '*RepresentativeRolePostgresIntegrationTest' --tests '*AuthorizedAccountsPostgresIntegrationTest'` | 26 casos, cero fallos/errores/omitidos; repetidos tras corregir fixtures y nulabilidad del test nuevo. |
| API estáticos | Spotless dirigido a los 15 Java modificados mediante rutas absolutas; después `./gradlew --offline spotlessCheck codeStructureCheck staticAnalysis` sin hook de formato. | Aprobado; ECJ y NullAway en main/test. No se relajaron límites ni warnings. |
| UI Jest focalizado | CLI Jest instalada con `--runInBand --runTestsByPath`: schemas, serialización, creación, formulario, roles, paginación, acceso institucional y selector de identidad. | 8 suites, 61 casos aprobados. |
| UI regresiones adicionales | Utilidades existentes de postulante, navegación e inicio; role picker y actualización. | 36 casos aprobados y un fallo preexistente de revisión por actor real, separado abajo. |
| UI estáticos | CLI TypeScript instalada: `tsc --noEmit --incremental false` sobre el proyecto completo; ESLint/Prettier sobre los 19 archivos del cambio. | Aprobado, sin modificar dependencias ni lockfile. |
| Datos | `bash -n src/main/resources/db/seed/run.sh`; seeder manual tres veces sobre institución ficticia/base nueva; asserts posteriores. | Tres perfiles nuevos y vínculos activos. La tercera ejecución conservó acceso suspendido y vínculo finalizado de un fixture; también conservó personas/asignaciones de los ocho escenarios anteriores. Se restauró únicamente ese fixture de QA para la demostración. |
| Integridad del workspace | SHA256 de fuentes contra snapshot inicial; comparación de HEAD e índice de ambos repositorios. | Solo 18 archivos API y 19 UI del alcance cambiaron/se añadieron; HEAD e índices intactos. El contenido documental anterior permanece sin sustituciones. |

Se corrigieron dos fixtures HTTP nuevos que omitían `isPrimaryContact`, dos stubs Mockito con varargs genéricos advertidos por ECJ y dos helpers JDBC con nulabilidad detectada por NullAway. Las repeticiones finales anteriores son las aprobadas; los intentos fallidos no cuentan como evidencia positiva.

#### Regresión ajena identificada

`institutional-applicant-role.util.test.ts`, caso `uses the real actor permission when reviewing links`, esperaba denegar revisión cuando solo el perfil seleccionado tiene permiso. El helper existente `canReviewGuardianLinks` consulta `hasInstitutionalPermission`, por lo que ese caso recibe `true`. SHA256 del helper y del test idénticos al snapshot inicial: no fueron modificados por esta ampliación. Se conserva el trabajo previo del usuario; no se debilitó la prueba ni se afirma que toda la regresión UI esté aprobada. La autorización HTTP de las operaciones probadas continúa validando al actor real. Resolver el guard preexistente requiere un cambio separado, no una desviación silenciosa del alcance.

#### Navegador y correo local

- Ambos paneles: selector obligatorio sin preselección, Postulante/Representante, sin contraseñas administrativas. Se creó una persona Representante en cada panel; consultas SQL confirmaron dos invitaciones pendientes y cero `User` para ambas. En plataforma se añadió Postulante desde el editor y se conservaron los dos roles.
- Se iniciaron sesiones ficticias de Representante, Postulante + Representante y Estudiante + Representante. Se comprobaron sus inicios y navegación en escritorio/móvil: sin actividad académica propia para Representante; inscripción para Postulante; materias/horarios para Estudiante.
- Representante seleccionó a la Estudiante aprobada y pudo consultar sus tres materias y horarios, aun estando deshabilitado el acceso personal de la representada. No se otorgaron privilegios administrativos al actor.
- Se aplicaron overrides de viewport de escritorio y móvil. El navegador conserva zoom del usuario: en lecturas DOM se observaron anchos efectivos de 976 y 278 CSS px; no se confunden con emulación de dispositivo físico. No hubo desbordamiento horizontal global en las mediciones de formularios/inicios.
- Al inspeccionar los archivos exportados se detectó recorte lateral en varias capturas, especialmente las de plataforma y los tres inicios. No se puede atribuir ese recorte al CSS ni descartarlo como problema de captura sin repetirlo. Las acciones, estados y mediciones DOM son evidencia funcional; esas imágenes no cierran la validación visual responsive. La captura del alta institucional móvil sí muestra el formulario completo. Queda pendiente repetir las capturas afectadas con viewport/zoom consistentes.
- Mailpit capturó dos mensajes de activación, sin contraseñas de los fixtures. No se usó SMTP externo, cuentas reales ni entrega/lectura como supuesto de éxito.
- Quedan pendientes las comprobaciones visuales específicas indicadas en REP02, repetir las capturas recortadas de REP03/REP04 y la resolución independiente de la regresión anterior. No hay cambios de alcance aprobados implícitamente.

Capturas en `/home/matias/.codex/visualizations/2026/10/27/01a17e73-7e99-7363-b20c-17fdcfe28b24/`, prefijo `representante-`:

- `alta-plataforma-escritorio.jpg`, `alta-plataforma-movil.jpg`, `alta-institucional-escritorio.jpg`, `alta-institucional-movil.jpg`.
- `invitacion-plataforma.jpg`, `lista-perfiles-escritorio.jpg`.
- `solo-escritorio.jpg`/`solo-movil.jpg`, `postulante-escritorio.jpg`/`postulante-movil.jpg`, `estudiante-escritorio.jpg`/`estudiante-movil.jpg`.
- `vinculo-movil.jpg`, `estudiante-representada-escritorio.jpg`, `materias-representada-escritorio.jpg`/`materias-representada-movil.jpg`.

Logs temporales: `/tmp/bemol-representative-api-fast-final.log`, `/tmp/bemol-representative-api-recheck.log`, `/tmp/bemol-representative-api-static-final.log`, `/tmp/bemol-representative-ui-jest.log`, `/tmp/bemol-representative-ui-typecheck-full.log`, `/tmp/bemol-representative-ui-regressions.log`, `/tmp/bemol-representative-ui-preexisting-regression.txt`, `/tmp/bemol-representative-seed-preserve.log` y `/tmp/bemol-representative-implementation/preservation-final.log`.

QA usó procesos API/UI exclusivos en puertos 18080/13000, PostgreSQL 18, Redis y Mailpit nuevos en red interna, base `representative_qa` y almacenamiento temporal. Los recursos preexistentes no se tocaron; la limpieza comprobó IDs/etiquetas de propiedad. El viewport se restauró y las pestañas temporales se cerraron. Sin migraciones nuevas, modificaciones de infraestructura, commits, push ni despliegues.

## Cierre posterior: estándares de UI y pendientes

Esta revisión fue autorizada expresamente después de la entrega anterior. Cierra sus pendientes de permisos y navegador; los diagnósticos y resultados anteriores se conservan como historial, no como fallos actuales. La matriz REP01–REP05 refleja el estado final actualizado.

### Referencias y reutilización

Se inspeccionaron en navegador los formularios existentes de roles (`/admin/roles/new`) e instituciones (`/admin/institutions/new`) antes de confirmar las pantallas de usuarios, representación y acceso. Se mantuvieron sus patrones de navegación, secciones, campos, errores, botones y confirmaciones; no se introdujo un sistema visual nuevo.

- Se reutilizan `SectionHeader`, `Field`/`FieldDescription`/`FieldError`, `TextField` del perfil institucional, `FormSelect`, `DatePicker`, `ActionForm`, `Checkbox`, `AlertDialog`, `Button` y `useActionFormErrorFocus` existentes.
- `SectionHeader` admite omitir una descripción sin dejar un párrafo vacío ni reservar otra fila. Se retiraron los textos redundantes de Datos personales, Institución y Gestión de acceso; la explicación de activación aparece únicamente al crear, no al editar.
- Se conservan las advertencias necesarias: buzón alternativo/compartido, confirmación del nuevo correo, contraseña del actor real, consecuencias de autonomía y comprobación de identidad. La confirmación de aprobación se acortó sin confundir representación con ingreso personal ni cambiar las restricciones de personal.
- El panel de acceso comparte campos entre titular, tutor y administración y reutiliza `TextField`, en lugar de repetir formularios. El catálogo de operaciones y el diálogo de suspensión se separaron por responsabilidad para cumplir el límite existente de ESLint: panel de 397 a 197 líneas no vacías. No se modificaron las reglas ni se añadieron dependencias.
- Las etiquetas de invitación usan `DETAIL_LABEL_CLASS_NAME`, como los otros detalles. Las confirmaciones usan el `Checkbox` compartido manteniendo `confirmed=yes`, ausencia cuando está desmarcado, `required` y estados pendientes.

### Defectos cerrados

| Problema | Corrección y evidencia |
| --- | --- |
| Revisión de vínculos obtenía privilegios del perfil seleccionado. | `canReviewGuardianLinks` usa `hasActorInstitutionalPermission`; el inicio consume la misma guarda que sidebar, páginas y Server Action. Se reprodujo el fallo anterior y pasaron los casos actor/perfil, ausencia del actor y sesión propia. Las guardas académicas del sujeto no se cambiaron. |
| Solicitar quedaba deshabilitado por datos inválidos sin mostrar el motivo. | El formulario reutiliza la validación de su Server Action, previa a HTTP, y su foco de errores. Navegador: nombre «An» muestra «El nombre debe tener entre 3 y 255 letras.», conserva valores y enfoca `dependent-first-name`. Se mantiene la prevención de doble envío, adjuntos y solicitudes ya creadas. |
| Campos de alta podían cambiar mientras se enviaba otra versión. | Los fieldsets se deshabilitan durante el guardado. Una prueba verifica bloqueo, selector y recuperación ante error. |
| Acceso propio no enfocaba los errores de titular/tutor/administración. | Se reutiliza el hook común con el mismo ref utilizado por suspensión. Navegador: correo vacío del tutor y del titular muestra errores y enfoca `email`; las pruebas cubren los tres modos, pendientes y `requestSubmit`. |
| Descripción accesible de correo apuntaba a un ID inexistente. | El diálogo conecta siempre el campo con su `FieldDescription`, incluso sin correo previo; dos casos de accesibilidad aprobados. |
| Revisión de solicitud existente sin nombres declarados mostraba un nombre vacío. | Aprobar/rechazar usa `matchingPerson ?? dependent` y documento como respaldo; seis regresiones y confirmación real de Ana → Julián en navegador. El helper global y las vistas de tutor no fueron modificados, para conservar la privacidad de solicitudes pendientes. |
| Texto de rechazo incorrecto y limitado a inscripciones. | Dice «No se aprobará este vínculo de representación.», con la identidad correcta. |

### Verificación final focalizada

- **Jest:** 22 suites, **152 casos aprobados**, cero fallos. Incluye la regresión previamente fallida, roles/altas/acciones, composición, foco, pendientes, confirmación, serialización, recuperación y navegación. Después de acortar el texto de aprobación y actualizar sus expectativas accesibles se repitió la selección completa, también aprobada. No se ejecutó Jest completo.
- **Estáticos UI:** TypeScript completo con `--noEmit --incremental false`, ESLint y Prettier sobre los 27 archivos de esta revisión, aprobados. `git diff --check` de ambos repositorios aprobado. Detector mecánico Impeccable: sin hallazgos; revisión independiente de contratos y consumidores sin hallazgos accionables.
- **API:** no se modificó código productivo, tests ni migraciones durante esta revisión de UI. Sus resultados previos de 58 unitarias y 26 PostgreSQL se conservan como evidencia anterior, no se presentan como repetidos ahora.
- **Navegador:** altas desde ambos paneles; edición institucional de roles; composición y transición; invitación pendiente; solicitudes, revisión/aprobación/rechazo sin ejecutar decisiones sensibles; acceso del tutor, correo del titular y confirmación de autonomía. Se confirmaron los tres perfiles de demostración y la selección de la Estudiante representada con sus tres materias y horarios.
- **Responsive:** comprobaciones de interacción/DOM con ancho efectivo de 390 CSS px, sin desbordamiento global en los formularios comprobados; capturas finales desde una pestaña nueva a escala 1, escritorio 1280 y encuadres móviles 390 × 844. Las capturas largas de solicitud y autonomía usan 390 × 1500/2100 para mostrar el formulario completo. Sustituyen las capturas recortadas anteriores; no se alteró CSS para ocultar un problema de captura. No son evidencia de dispositivo físico.
- **Resultados reales en QA:** Representante creado desde plataforma y Postulante desde institución, ambos sin `User` y con invitación enviada; el segundo terminó Estudiante + Representante después de la edición institucional. Dos mensajes capturados en Mailpit. La solicitud inválida no creó una persona. La solicitud Ana → Julián permaneció pendiente: no se aprobó, rechazó ni se otorgó autonomía por la revisión visual.

### Evidencias de navegador y preservación

Capturas finales en `/home/matias/.codex/visualizations/2026/10/27/01a17e73-7e99-7363-b20c-17fdcfe28b24/`, prefijo `estandares-`:

- `referencia-roles.jpg`, `referencia-institucion.jpg`: referencias existentes.
- `alta-plataforma-desktop.jpg`/`alta-plataforma-mobile.jpg`, `alta-institucion-desktop.jpg`/`alta-institucion-mobile.jpg`.
- `confirmacion-vinculo-desktop.jpg`/`confirmacion-vinculo-mobile.jpg`, `autonomia-desktop.jpg`/`autonomia-mobile.jpg`.
- `solicitud-desktop.jpg`/`solicitud-mobile.jpg`, `solicitud-error-mobile.jpg`, `tutor-acceso-mobile.jpg`, `titular-correo-desktop.jpg`/`titular-correo-mobile.jpg`.
- `representante-home-desktop.jpg`/`representante-home-mobile.jpg`, `postulante-home-desktop.jpg`/`postulante-home-mobile.jpg`, `estudiante-home-desktop.jpg`/`estudiante-home-mobile.jpg`, `materias-representada-desktop.jpg`/`materias-representada-mobile.jpg`.

Logs reproducibles temporales bajo `/tmp/bemol-ui-standards-review/`: `final-jest.log`, `final-typecheck.log`, `final-eslint.log`, `final-prettier.log`, `impeccable.json`, los logs de regresión antes/después, y `qa/runtime-review-checks.json`. El snapshot inicial y los manifiestos permiten separar estos cambios del trabajo previo del usuario.

QA usó otra base/red/contenedores nuevos, etiquetados con propietario único, únicamente datos ficticios y Mailpit interno. API/UI se ejecutaron en procesos propios y una copia temporal de UI sin archivos `.env`; no se utilizaron SMTP externo ni cuentas reales. Al terminar se cerraron las pestañas y se restauró el viewport; la limpieza validó los IDs/etiquetas antes de retirar solo los recursos temporales. Se preservaron HEAD, índices, fuentes ajenas, bases y volúmenes preexistentes. Sin cambios de infraestructura, commits, push ni despliegues.

No quedan pendientes funcionales o visuales identificados de esta ampliación dentro del alcance comprobado. Permanecen fuera de esta evidencia las suites completas, CI, despliegues y dispositivos físicos.

### Corrección posterior: confirmación de vinculación sin redundancias

- Por indicación del usuario, aprobar muestra únicamente «¿Aceptar que [tutor] represente a [persona]?», con Aceptar y Cancelar. Se retiraron el checkbox y los párrafos explicativos; no se agregaron componentes ni pasos nuevos.
- Pulsar Aceptar confirma la persona coincidente mostrada y envía su `confirmedPersonId`. Se conservan las validaciones del backend, ambos paneles, los errores recuperables y el bloqueo de doble envío. Una coincidencia nueva o distinta no queda confirmada implícitamente.
- R03/R07: Jest focalizado de aprobación y listado consumidor, **2 suites / 12 casos aprobados**; TypeScript, ESLint y Prettier aprobados. No se modificó código API ni se repitieron pruebas PostgreSQL.
- Navegador: vista previa aislada del componente real y sus estilos compartidos, escritorio y móvil, aceptación directa, pendiente y error recuperable con callbacks simulados. Capturas en el directorio de evidencias anterior: `vinculacion-simple-desktop.jpg`, `vinculacion-simple-mobile.jpg` y `vinculacion-simple-error-mobile.jpg`. No se repitió el flujo completo contra API ni se modificaron datos.
- La referencia anterior al checkbox y las capturas `estandares-confirmacion-vinculo-*` se conservan como evidencia histórica, pero quedan sustituidas para este modal. La confirmación de autonomía no cambió.

### Ajustes posteriores: textos, validación del alta e hidratación

- Por indicación expresa del usuario se retiró la aclaración sobre correo alternativo/compartido del alta y la invitación. Esta es una desviación visual aprobada respecto de R05: esos correos siguen admitidos, sin cambiar validaciones de identidad ni acceso.
- R07: Datos personales recupera una descripción breve al crear. Ambos paneles usan la validación existente de React Hook Form/Zod, sin el popup nativo del selector; el envío vacío muestra errores por campo, enfoca el primero y no invoca el alta.
- Etiquetas visibles «Correo electrónico»; validaciones «El correo es requerido.» / «El correo es inválido.»; rol inicial «Seleccioná un rol inicial.». Se actualizaron textos equivalentes de UI/API sin renombrar campos ni modificar los contratos. La validación requerida del correo se detiene antes del formato para no sobrescribir su mensaje.
- El aviso de hidratación provenía del icono PWA dependiente de `resolvedTheme`. Se reutilizó la selección CSS de dos SVG estáticos, sin silenciar errores ni alterar la activación.
- Verificación focalizada: **94 casos UI y 7 API aprobados**; TypeScript, ESLint, Prettier, compilación, formato Java dirigido y `codeStructureCheck` aprobados. Navegador: preview aislado de componentes reales en ambos paneles, claro/oscuro y capturas escritorio/móvil; mensajes requerido/inválido y foco correctos, consola sin avisos de hidratación y cero acciones ficticias invocadas. Capturas con prefijo `alta-validacion-` y `alta-correo-invalido-` en el directorio de evidencias anterior.
- No se utilizó ni consumió el enlace de activación del usuario. No se repitieron suites completas, PostgreSQL ni el flujo de activación real. El detector de diseño confundía el `th:src` de Thymeleaf con una imagen sin fuente; se agregó una excepción de esa regla únicamente para esa plantilla, conservando las pruebas de renderizado de correo.

### Extensión R07: validación propia sin avisos nativos

- `ActionForm` usa `noValidate` por defecto para que las acciones existentes informen los errores; conserva la opción explícita de validación nativa. No se retiraron atributos semánticos ni validaciones de acciones/backend. Se revisaron sus consumidores y las confirmaciones sensibles siguen validadas por la acción.
- Activación informa contraseña requerida, longitud y confirmación en sus campos, con foco y descripción accesible. Una invitación inválida conserva un error general. No se llama a la API con datos inválidos ni se muestra éxito antes de su aceptación.
- La invitación administrativa ahora muestra sus errores de correo, que antes dependían de la validación nativa. Los formularios con React Hook Form de instituciones/administradores, el login de plataforma y la revisión de documentos también delegan la validación a sus controles existentes.
- **98 casos focalizados UI aprobados**; TypeScript, ESLint, Prettier y `git diff --check` aprobados. Navegador: activación vacía con componente y acción reales, en preview aislado, desktop/móvil, dos errores en español, foco en contraseña y consola sin errores. Transporte API bloqueado y contador de intentos igual a cero. Capturas `activacion-validacion-desktop.jpg` y `activacion-validacion-mobile.jpg` en el directorio de evidencias anterior.
- No se usaron credenciales ni enlaces reales ni se activaron cuentas. Sin cambios de código API, migraciones o infraestructura; no se repitieron suites completas ni PostgreSQL. La comprobación visual no sustituye una activación real de extremo a extremo.
