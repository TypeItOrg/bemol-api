# SEGURIDAD DE DOCUMENTACIÓN DE INSCRIPCIÓN

El circuito configurable, la admisión provisoria y su verificación están en
[Requisitos documentales y estados de admisión](DOCUMENTACION-Y-ESTADOS.md).

## Almacenamiento compartido

El proveedor es global (`STORAGE_PROVIDER=local|s3`). Inscripciones usa
`common.storage.StorageService`, igual que los futuros módulos de imágenes u otros
archivos. Las restricciones de formatos, permisos y auditoría descritas aquí son
propias de documentación de inscripción. El servicio común no las impone a otros
módulos y no mantiene una configuración de proveedor por módulo.

## Alcance y permisos

PDF, JPEG, PNG y WebP estático de hasta 10 MiB. Se validan contenido, tipo declarado y estructura:
ImageIO decodifica las imágenes después de limitar sus dimensiones a 25 millones
de píxeles; PDFBox 3.0.8 usa análisis estricto y rechaza PDFs cifrados, sin páginas,
truncados o ilegibles. Como protección de recursos se permiten hasta 1.000 páginas
y 64 MiB acumulados de contenido de páginas descomprimido. Se conserva el archivo
original, incluidas firmas digitales: no se reconstruyen ni sanitizan documentos.
WebP se decodifica con TwelveMonkeys ImageIO; se comprueba el tamaño del contenedor
RIFF y se rechazan animaciones. Los formatos permitidos siguen dependiendo del
catálogo; habilitar WebP no modifica requisitos ni configuraciones existentes.

**No hay antivirus ni detección de malware.** La validación no certifica que un
archivo sea inocuo ni comprueba autenticidad de documentos o firmas digitales.

| Actor | Consulta | Carga | Eliminación/reemplazo |
| --- | --- | --- | --- |
| Solicitante propietario, en la institución de su sesión | Sí | Según estado documental | Según estado documental |
| Administrador de plataforma | Sí | Según estado documental | Según estado documental |
| Personal institucional | `ENROLLMENT_ATTACHMENT_READ` | `ENROLLMENT_ATTACHMENT_UPLOAD` | `ENROLLMENT_ATTACHMENT_DELETE`; reemplazar también requiere UPLOAD |

Todos los tipos documentales, incluidos certificados médicos, comparten estos
permisos. El personal debe coincidir con institución y ámbito del trayecto.
Administrativo y autoridad institucional reciben los permisos iniciales; los
roles personalizados requieren asignación explícita. READ depende del permiso de
lectura de solicitudes; UPLOAD y DELETE dependen de READ. Los borradores requieren período abierto; las solicitudes enviadas y
admitidas provisoriamente permiten completar documentación después del cierre.
Las entregas aceptadas quedan bloqueadas.

Sin READ, las respuestas de solicitudes devuelven `data.attachments: []` y
`canReadAttachments: false`. La UI oculta la tarjeta documental. Los endpoints de
adjuntos rechazan accesos no autorizados; los adjuntos devueltos nunca incluyen la
ruta física (`storagePath` queda null). Las descargas conservan la previsualización,
con `Cache-Control: private, no-store` y `X-Content-Type-Options: nosniff` en API/UI.
La UI permite carga, retiro, revisión e historial según permisos y estado.

## Auditoría

`enrollment_document_audit` conserva IDs históricos sin claves foráneas a personas,
solicitudes o adjuntos, para no impedir eliminaciones de esos registros. Registra
instantes UTC, actor (personId o platformAccountId), tipo de cuenta, institución,
solicitud, adjunto, acción, resultado y requestId.

Las mutaciones exitosas se auditan dentro de la misma transacción. Consultas,
accesos al contenido y rechazos se registran mediante una transacción independiente,
incluso si la transacción exterior es de solo lectura o termina revertida.
CONTENT_ACCESS/ALLOWED se confirma antes de abrir el stream: describe autorización,
no descarga completada ni lectura humana. Si falla la auditoría, no se entrega el
contenido. Las solicitudes rechazadas antes de autenticarse siguen el manejo de
seguridad HTTP; no generan un evento documental de un actor autenticado.

Los diagnósticos de PDFBox están deshabilitados para evitar fragmentos no confiables
en logs del parser.

No se almacenan documentos personales, nombres originales, contenido, credenciales
ni ubicaciones en esta auditoría. Las ubicaciones técnicas solo están en el journal,
que necesita identificar qué objeto limpiar. El acceso SQL a ambas tablas debe
limitarse al personal operativo autorizado. No se agrega una pantalla ni un endpoint
público para consultar/modificar la auditoría.

Consultas operativas de solo lectura (reemplazar el UUID de ejemplo):

```sql
SELECT occurred_at, actor_id, account_type, action, result, attachment_id, request_id
FROM enrollment_document_audit
WHERE application_id = '00000000-0000-0000-0000-000000000000'
ORDER BY occurred_at DESC;

SELECT institution_id, action, count(*)
FROM enrollment_document_audit
WHERE result = 'DENIED' AND occurred_at >= now() - interval '1 day'
GROUP BY institution_id, action;
```

## Journal y eliminación

Las cargas crean una intención PENDING durable en una transacción independiente
antes de escribir el objeto. La transacción del adjunto bloquea esa intención
hasta terminar y la confirma ACTIVE junto con el adjunto. Si hay rollback o caída,
la intención permanece PENDING y vence a las 24 horas. Ese plazo corresponde a
cargas abandonadas, no a la retención de documentos confirmados.

Reemplazar o retirar conserva la entrega histórica y su objeto con referencia
activa. Estas operaciones no crean trabajos de eliminación física. El journal
continúa limpiando cargas abandonadas o revertidas; no se purgan versiones
históricas automáticamente.

Cada minuto, el trabajador reclama hasta 100 trabajos vencidos usando
`FOR UPDATE SKIP LOCKED`. Mantiene el bloqueo durante la eliminación y verifica
que no haya referencias activas; estas se preservan. Un objeto ya inexistente es
éxito. Los fallos se reintentan a los 1, 2, 4, 8, 16, 32 y luego cada 60 minutos.
No se descartan trabajos tras un máximo de intentos. Una caída después del borrado
físico permite repetirlo sin daño. Auditoría y estado DONE se confirman juntos.
Los errores SQL dejan el trabajo pendiente y producen una señal operativa sin
volcar payloads o excepciones del proveedor que puedan contener información sensible.

No hay vencimiento automático de archivos ACTIVE ni purga automática de auditoría.
Los directorios locales vacíos pueden permanecer; no contienen documentación.

```sql
SELECT state, count(*), min(created_at) AS oldest_created_at
FROM enrollment_storage_jobs GROUP BY state;

SELECT id, institution_id, application_id, attachment_id, attempts, last_error,
       created_at, updated_at, next_attempt_at
FROM enrollment_storage_jobs
WHERE state = 'PENDING'
ORDER BY next_attempt_at;

SELECT count(*) AS overdue_jobs, min(next_attempt_at) AS oldest_due
FROM enrollment_storage_jobs
WHERE state = 'PENDING' AND next_attempt_at < now() - interval '1 hour';
```

## Destinos y backups

Cada trabajo conserva proveedor, ubicación (directorio absoluto o bucket), prefijo
y ruta relativa. Nunca se ejecuta en otro destino. El arranque rechaza configuraciones
incompatibles con trabajos PENDING o ACTIVE del journal. Los trabajos pendientes se
procesan en su destino original y no se descartan para cambiar de configuración.

Esta entrega parte de un entorno de prueba limpio. No incluye alias de variables,
tratamiento especial ni migración de adjuntos anteriores. Un futuro cambio de destino
con archivos que deban conservarse requerirá un procedimiento específico de traslado.

Los backups conservan documentos y metadatos según su propia política; una baja
lógica/física no purga snapshots anteriores. Al restaurar, recuperar el journal y
los objetos del mismo punto temporal; los PENDING volverán a ejecutarse.

## Despliegue y aceptación pendiente

La migración `20260922135824__secure_enrollment_documents.sql` crea auditoría y
journal. Desplegar la API antes de la UI:
la UI usa `canReadAttachments` y, si falta, oculta documentos. Ejecutar la migración
solo mediante el procedimiento habitual de Flyway del entorno aprobado.

Además de la aceptación documentada en ENROLLMENT-DOCUMENTS.md, verificar:

- Aislamiento institucional y por trayecto; propietario con sesión en otra institución.
- Personal con lectura de solicitudes pero sin lectura documental, tanto en descarga
  como en respuestas de solicitudes; UPLOAD sin DELETE no puede reemplazar.
- Borradores/períodos no editables; certificados médicos con la misma política.
- MIME falsificado, PDF ilegible/cifrado/truncado, imagen corrupta y exceso de píxeles;
  originales válidos conservan exactamente su contenido.
- Persistencia de rechazos tras rollback; falla de auditoría bloquea el contenido.
- Rollback/caída tras escribir y antes de confirmar; limpieza tras 24 horas.
- Reemplazo concurrente, reinicio del trabajador y caída después de borrar;
  referencias activas preservadas y reintentos idempotentes.
- API/UI con no-store/nosniff; cuenta sin permiso sin nombres ni metadatos.
- S3 real: carga, lectura, eliminación, errores IAM y rechazo de cambios de destino.

Estos escenarios se documentan como aceptación pendiente, no como pruebas ejecutadas.
No se agregaron ni ejecutaron tests automatizados. Compilación y análisis estático
no verifican transacciones PostgreSQL, concurrencia, renderizado ni acceso real a AWS.

### Verificación de esta implementación

- `compileJava`: correcto con Java 21 y PDFBox 3.0.8.
- Spotless: aplicado únicamente a los Java modificados.
- UI: ESLint de los archivos modificados y TypeScript `--noEmit`: correctos.
- Compose: configuración válida para desarrollo, staging y producción.
- Plantilla CloudFormation: sintaxis YAML revisada; no validada ni desplegada en AWS.
- `git diff --check`: correcto en los tres repositorios.

La verificación original de seguridad fue estática; la comprobación local posterior
de migraciones/HTTP/UI se registra en ENROLLMENT-DOCUMENTS.md. Siguen pendientes
concurrencia exhaustiva y S3 real. No se ejecutaron tests automatizados. Los tests existentes que construyen directamente los servicios de
adjuntos/aprobación/rechazo y los de almacenamiento local necesitan adaptar sus
imports, dependencias y llamadas al contrato común; ese
trabajo de tests no se incluyó en esta entrega. No se afirma compatibilidad de la
suite existente ni verificación visual del frontend.
