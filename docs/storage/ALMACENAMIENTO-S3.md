# ALMACENAMIENTO GENERAL EN AMAZON S3 Y LOCAL

La seguridad documental y la operación de auditoría/limpieza se describen en
[Seguridad documental](../enrollment/SEGURIDAD-DOCUMENTAL.md).

## Arquitectura actual

Todos los módulos usan `common.storage.StorageService`: escritura por clave,
lectura como recurso/stream y eliminación física. Spring selecciona una sola
implementación para toda la aplicación mediante `STORAGE_PROVIDER`. No hay
selección de proveedor por módulo ni por archivo:

| Proveedor | Implementación | Destino |
| --- | --- | --- |
| `local` (predeterminado) | `LocalStorageService` | Directorio o volumen persistente |
| `s3` | `S3StorageService` | Bucket privado de Amazon S3 |

Se utiliza el SDK oficial **AWS SDK for Java 2.x**, con la versión centralizada en
el BOM de `build.gradle`. El cliente se cierra al apagar la aplicación y tiene
límites de 30 segundos por intento y 2 minutos por operación del SDK, incluidos los
reintentos. Esos límites no son un plazo total para la descarga HTTP al usuario.

El servicio común no depende de inscripciones, MultipartFile, UUID de solicitud,
PDFBox ni extensiones concretas. Recibe una clave relativa, tipo de contenido,
tamaño y un `InputStreamSource`. Por ejemplo, un módulo puede usar
`images/<uuid>.webp` y otro `documents/<uuid>.pdf`; ambos usan el mismo backend.
Las claves se validan para impedir rutas absolutas y segmentos `.`/`..`.

En inscripciones, `EnrollmentFilePolicy` prepara y valida el archivo antes de
llamar al servicio: hasta 10 MiB, contenido PDF/JPEG/PNG y nombre generado con UUID.
PostgreSQL conserva los metadatos y la
ruta relativa `enrollments/<solicitud UUID>/<archivo UUID>.<extensión>`; el contenido se guarda
en el proveedor. En S3, la clave es `<prefijo><ruta relativa>`.

La autorización institucional y del solicitante se resuelve antes de leer el
archivo. El navegador descarga a través de la API; no recibe credenciales AWS ni
URLs públicas. No hace falta configurar CORS en S3 para este flujo.

Los permisos, la validación y la auditoría de documentos siguen en inscripciones;
no se convierten en reglas globales para otras imágenes o archivos. El journal de
inscripciones consume el servicio común y conserva su limpieza transaccional.

Las variables canónicas son `STORAGE_PROVIDER`, `STORAGE_LOCAL_DIR`,
`STORAGE_S3_BUCKET`, `STORAGE_S3_PREFIX` y `AWS_REGION`, más las credenciales AWS.

## Preparar la cuenta y el bucket

1. Crear la cuenta AWS y un bucket S3 de propósito general con nombre único.
   Elegir la región donde se guardará la documentación y usar la misma en
   `AWS_REGION`. Separar los buckets de desarrollo, staging y producción.
2. Mantener **Block Public Access** habilitado y **Object Ownership: Bucket owner
   enforced** (ACL deshabilitadas). La API no envía ACL ni administra el bucket.
3. Mantener el cifrado predeterminado **SSE-S3** para la configuración inicial.
   KMS queda fuera de esta entrega. El cliente utiliza el cifrado predeterminado
   del bucket. Exigir HTTPS mediante la política preparada en
   `bemol-infra/deploy/aws/storage.yaml`.
4. Crear una identidad exclusiva para la API y asociarle la política siguiente.
   En EC2, preferir un rol de instancia. En un VPS fuera de AWS, proporcionar
   credenciales de esa identidad por el entorno privado del contenedor.

Reemplazar `NOMBRE_DEL_BUCKET` y `bemol/` por los valores elegidos. Esta es
una política de identidad IAM, no una política pública del bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ManageApplicationObjects",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::NOMBRE_DEL_BUCKET/bemol/*"
    }
  ]
}
```

El prefijo también incluye los objetos temporales `.storage-check-*` usados al
arrancar. No se requieren permisos para crear buckets ni modificar políticas.
Sin `s3:ListBucket`, S3 puede responder 403 ante una clave inexistente; la API
conserva ese error de almacenamiento y solo traduce los 404 a archivo no encontrado.
Si se necesita distinguir ese caso, conceder además `s3:ListBucket` sobre
`arn:aws:s3:::NOMBRE_DEL_BUCKET` a la identidad de la API.

La configuración soportada utiliza un bucket nuevo sin versionado ni Object Lock.
La limpieza utiliza `DeleteObject`, no purga versiones históricas ni retenciones
bloqueadas. No configurar expiración de documentos activos. Los backups conservan
sus propias copias hasta vencer su política de conservación: el borrado en la API
no implica borrado de backups.

## Probar localmente primero

No se necesita cuenta de AWS. Copiar `.env.dev.example` a `.env.dev` si todavía no
existe y configurar `STORAGE_PROVIDER=local`. Ejecutar `make dev`: Compose monta
`bemol-api-storage-dev` en `/workspace/storage`. Al ejecutar Java directamente,
`STORAGE_LOCAL_DIR` define el directorio y su valor predeterminado es `storage`.

Cargar un PDF o imagen en una solicitud editable y comprobar descarga autenticada,
reemplazo y eliminación. Inscripciones organiza sus claves bajo `enrollments/`;
otros módulos pueden usar `images/` o `documents/`, siempre en el mismo proveedor.

## Activar S3 en desarrollo

En `bemol-api`, copiar `.env.dev.example` a `.env.dev` solo si este último no existe.
Completar el archivo privado:

```dotenv
STORAGE_PROVIDER=s3
STORAGE_S3_BUCKET=NOMBRE_DEL_BUCKET
AWS_REGION=us-east-1
STORAGE_S3_PREFIX=bemol/
AWS_ACCESS_KEY_ID=REEMPLAZAR
AWS_SECRET_ACCESS_KEY=REEMPLAZAR
AWS_SESSION_TOKEN=
```

Usar la región real del bucket. Si las credenciales son temporales, completar
también `AWS_SESSION_TOKEN` y renovar los tres valores al vencer. Con un rol IAM
disponible para el contenedor, dejar las tres variables de credenciales vacías.
Nunca usar credenciales de la cuenta raíz ni versionar secretos.

`compose.yaml` carga `.env.dev` mediante `env_file`. El contenedor no hereda
automáticamente las variables AWS del shell ni el directorio `~/.aws` del host.
Al ejecutar Java directamente, el SDK sí puede usar los perfiles AWS disponibles
para ese proceso; los archivos `.env` no se cargan automáticamente fuera de Compose.

Después de completar la configuración, recrear el servicio con el flujo habitual
`make dev`. La selección es global para todos los módulos.

## Staging y producción

La configuración compartida pertenece a `bemol-infra`. Su `.env.example` documenta
las mismas variables y `compose.yaml` las pasa al contenedor `api`. Completar
`.env.staging` o `.env.production` en el host correspondiente y seguir
`bemol-infra/docs/S3.md` para validar y aplicar la configuración.

## Arranque y comprobación pendiente en AWS

Con `provider=s3`, la API escribe, lee y elimina un objeto temporal al arrancar.
Si falta el bucket, las credenciales, los permisos o la conectividad, el arranque
falla. No se cambia automáticamente a local, evitando repartir archivos entre
destinos ante un problema de configuración.

Cuando exista la cuenta, comprobar con una solicitud de inscripción de prueba:

1. Arranque saludable y ausencia del objeto temporal tras completar la comprobación.
2. Carga y descarga autenticada de un PDF y una imagen, conservando su contenido.
3. Rechazo de descarga desde un usuario sin acceso a la solicitud.
4. Reemplazo y eliminación desde una solicitud editable, comprobando el resultado
   en el bucket después de que se ejecute el trabajador de limpieza.

Estas comprobaciones requieren un bucket real y todavía están pendientes.

## Cambiar de proveedor

La configuración no copia archivos. Esta entrega parte de datos de prueba y no
incluye compatibilidad ni migración de adjuntos anteriores. Para comenzar con S3,
usar un entorno de prueba limpio y configurar el bucket y las credenciales.
Los registros de nuevas cargas y eliminaciones conservan su destino: el cambio no
puede redirigir trabajos existentes a otro proveedor. Ver
[el control operativo de destinos](../enrollment/SEGURIDAD-DOCUMENTAL.md#destinos-y-backups).

## Referencias

- [Credenciales del SDK Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).
- [Límites de espera del SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/timeouts.html).
- [Seguridad de S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html).
- [Permisos y respuestas de GetObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html).
- [AWS Free Tier](https://aws.amazon.com/free/): revisar las condiciones al crear la
  cuenta. La oferta para cuentas nuevas utiliza créditos y un plan gratuito de
  hasta seis meses; no asumir los antiguos doce meses gratuitos de S3.
