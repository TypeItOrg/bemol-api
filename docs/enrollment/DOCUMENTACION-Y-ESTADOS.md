# REQUISITOS DOCUMENTALES Y ESTADOS DE ADMISIÓN

Cada trayecto configura nombre, instrucciones, formatos (PDF/JPEG/PNG), orden y
exigencia de sus documentos. La pantalla está en el detalle del trayecto, sección
**Documentación**. Desactivar o editar un requisito afecta solicitudes nuevas: cada
solicitud copia los requisitos activos al crearse.

| Exigencia | Enviar | Admitir provisoriamente | Confirmar definitivamente |
| --- | --- | --- | --- |
| Obligatorio al enviar | Archivo vigente | Aceptado | Aceptado |
| Obligatorio para confirmar | Puede faltar | Puede faltar | Aceptado |
| Opcional | No bloquea | No bloquea | No bloquea |

La validación técnica conserva el límite de 10 MiB, valida contenido y respeta los
formatos configurados. No representa una revisión administrativa ni detección de
malware. La configuración de Next permite 12 MiB por petición para cubrir el
multipart; el archivo individual sigue limitado a 10 MiB. La plantilla Nginx usa
el mismo máximo de petición. Aplicar esa plantilla es una tarea de despliegue
independiente, no realizada por esta entrega.

## Entregas y revisión

Cada requisito tiene como máximo una entrega vigente. Sus estados visibles son
pendiente de entrega, pendiente de revisión, observado y aceptado. Una observación
requiere motivo. Se revisa una versión identificada por UUID: no se puede aceptar
una versión reemplazada ni modificar una entrega aceptada.

Reemplazar conserva el original y crea una entrega pendiente de revisión. Retirar
conserva el archivo y deja el requisito pendiente; no se permite retirar documentos
iniciales después del envío. Los reemplazos requieren carga y eliminación; el
retiro requiere eliminación. El historial paginado incluye versiones, fecha,
autor, revisión, observaciones y responsable. No se reabren documentos aceptados.

En borradores, las cargas requieren un período abierto. En solicitudes enviadas o
admitidas provisoriamente, la documentación puede completarse incluso después del
cierre. Eso no habilita cambios a materias ni al resto de la solicitud. Aprobadas,
rechazadas y canceladas quedan de consulta.

Los archivos históricos mantienen referencias activas de almacenamiento. El
trabajador limpia únicamente cargas abandonadas/revertidas o trabajos de borrado
explícitos cuya referencia ya no exista; reemplazar o retirar no crea un trabajo
de borrado. No hay vencimiento de documentación ni versiones automáticas de S3.

## Admisión y permisos

`SUBMITTED` puede pasar a `PROVISIONALLY_APPROVED` si los requisitos iniciales están
aceptados y falta aceptar alguno obligatorio de entrega posterior. Puede pasar
directamente a `APPROVED` si todos los obligatorios están aceptados. La confirmación
`PROVISIONALLY_APPROVED → APPROVED` siempre es explícita.

La admisión provisoria habilita el circuito existente de asignación a cursos y
listas de espera, con sus cupos y requisitos académicos. Confirmar no repite ese
procesamiento. La condición de estudiante sigue creándose con la incorporación
efectiva al primer curso. No se agregan bajas ni revocaciones automáticas.

La configuración usa permisos de lectura/modificación del trayecto. La revisión
usa `ENROLLMENT_ATTACHMENT_REVIEW`, que depende de lectura documental, con ámbitos
institucionales y por trayecto. Se asigna a administrativo y autoridad institucional;
los roles personalizados necesitan asignación explícita. Ser propietario no concede
permiso de revisión. La admisión usa el permiso actual de aprobación.

Sin permiso documental se omiten archivos, requisitos de la solicitud, revisiones
y metadatos. Los filtros de pendientes solo consideran los trayectos donde el
personal tiene lectura documental. El historial de admisión registra estados,
fechas y actor; las observaciones quedan en las entregas, fuera de logs y auditoría
técnica.

## Contratos HTTP

Todos los endpoints conservan versionado V1 y autenticación:

- `/institutions/{institutionId}/training-paths/{pathId}/document-requirements`:
  GET y POST; PUT `/{id}` modifica también orden y estado activo.
  Plataforma usa el mismo sufijo bajo `/admin/institutions`.
- `/enrollment-applications/{applicationId}/attachments`: POST multipart recibe
  `requirementId` y `file`, reemplazando el antiguo `attachmentType`; GET lista vigentes.
- `/{attachmentId}/content`: descarga tanto vigentes como históricos;
  `DELETE /{attachmentId}` retira la entrega sin borrar su contenido.
- `/requirements`: condiciones copiadas, estado, entrega vigente y acciones autorizadas.
- `/history?requirementId=…&page=0&size=10`: entregas y revisiones paginadas.
- `POST /{attachmentId}/review`: `{ "status": "ACCEPTED" | "OBSERVED", "observation": "…" }`.
- Las rutas institucional y de plataforma de resolución agregan
  `/approve-provisionally`. `/approve` confirma definitivamente o aprueba directamente.
- Listados administrativos admiten `pendingDocuments=true`; el filtro `status`
  admite `PROVISIONALLY_APPROVED`.

La respuesta de solicitud incorpora `documents`, `canApproveProvisionally`,
`canConfirm` y `admissionHistory`. Los adjuntos utilizan `requirementId`, mantienen
`storagePath: null` y las descargas usan `private, no-store` y `nosniff`.

## Migraciones y aceptación

Migraciones generadas con `make migration`:

- `20260922152727__configurable_enrollment_documents.sql`.
- `20260922154916__enforce_document_delivery_integrity.sql`.

### Verificación estática

- Compilación Java y formato Spotless limitado a los archivos modificados.
- TypeScript **completo** (`tsc --noEmit`), incluidos los fixtures de tests, sin errores.
  Los fixtures existentes usan `requirementId` y el estado documental actual.
- Prettier y ESLint de los archivos modificados; `git diff --check`.
- Configuración Compose validada.

### Aceptación manual del 22/09/2026

Comprobada contra una API real con PostgreSQL 18 y Redis temporales, usando el
catálogo de demostración y archivos PNG ficticios. Los usuarios/permisos y el
catálogo se prepararon por SQL; el alta, envío, carga, revisión, admisión y matrícula
se recorrieron mediante HTTP autenticado. La verificación UI de configuración,
revisión, confirmación e historial de la entrega anterior también se conserva.

| Escenario | Resultado observado |
| --- | --- |
| Datos anteriores | Un adjunto del esquema anterior fue descartado; las migraciones posteriores aplicaron correctamente. Repetir el comando sobre el modelo nuevo dejó intactas sus 7 entregas. En la base local el comando encontró 0 adjuntos anteriores. |
| Copias de requisitos | La solicitud creada recibió sus tres requisitos. Editar/desactivar la definición no cambió esa copia; una solicitud nueva recibió el catálogo actualizado. |
| Envío y admisión | Sin archivo inicial: HTTP 400. Con archivo: envío exitoso. Sin revisión inicial: admisión provisoria rechazada. Después de aceptarlo: admisión exitosa. |
| Propietario y revisión | El solicitante pudo cargar/leer sus archivos; revisar su propio archivo sin permiso devolvió 403. |
| Instituciones y trayectos | Una cuenta de otra institución, incluso con el mismo documento personal, no obtuvo solicitudes ni archivos. El rol limitado a un trayecto recibió 403 al consultar otro; el filtro de pendientes devolvió solo el trayecto autorizado. |
| Lectura documental sin modificación | La consulta devolvió 200; carga, retiro y revisión devolvieron 403. |
| Lectura sin permiso documental | La solicitud fue legible, con `documents: []` y `data.attachments: []`; requisitos, historial, contenido y filtro documental devolvieron 403. |
| Incorporación efectiva | Una admisión provisoria con documento diferido pendiente se matriculó en un curso. Se creó un solo estudiante y un solo rol de estudiante. |
| Cupos y espera | Con la clase llena, otra admisión provisoria quedó `WAITLISTED`, posición 1; intentar matricularla devolvió 400 por falta de capacidad. |
| Período cerrado | La carga diferida continuó funcionando; modificar el resto del borrador fue rechazado. |
| Reemplazo contra revisión | Lanzados simultáneamente: reemplazo 201 y revisión de la versión original 400. La versión obsoleta no quedó aceptada. |
| Dos reemplazos | Ambos uploads concurrentes finalizaron, conservaron sus versiones y quedó exactamente una entrega vigente. |
| Revisión contra confirmación | Ambas peticiones concurrentes completaron en orden válido; la confirmación observó la versión vigente aceptada. Un intento anterior sin aceptación había devuelto 400. |
| Opcionales e historial | Retirar conservó el archivo descargable. Se volvió a cargar y observar el opcional; esa observación no bloqueó la confirmación. Históricos inaccesibles para cuentas sin lectura documental. |
| Confirmación sin duplicación | Antes/después: 1 estudiante, 1 matrícula y 1 evento de incorporación. Cada estado de admisión apareció una vez. Repetir la confirmación devolvió 409. |
| Auditoría | Persistieron rechazos de metadatos, contenido y revisión, además del historial de estados. |
| Limpieza tras reinicio | El trabajador eliminó un huérfano vencido y dejó el trabajo `DONE`. Reactivó las referencias históricas pendientes y conservó físicamente los 3 archivos reemplazados y el archivo retirado. |
| Parámetros inválidos | Historial sin `requirementId` o con UUID inválido devuelve 400; se corrigió el 500 detectado durante esta aceptación. |

Estas comprobaciones son escenarios manuales concretos, no una prueba de carga ni
una cobertura exhaustiva de todas las combinaciones. No se agregaron ni ejecutaron
suites automatizadas; se adaptaron los fixtures existentes señalados por TypeScript.

### Dependencias externas restantes

- Operación real en S3 cuando exista bucket y credenciales.
- Verificación de límites de carga y cabeceras en el proxy de un despliegue real;
  la plantilla Nginx queda preparada, sin desplegar.
