# ALMACENAMIENTO LOCAL Y COMPATIBLE CON S3

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
| `s3` | `S3StorageService` | Bucket privado compatible con S3, como Amazon S3 o Cloudflare R2 |

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
llamar al servicio: hasta 10 MiB, contenido PDF/JPEG/PNG/WebP estático y nombre generado con UUID.
PostgreSQL conserva los metadatos y la
ruta relativa `enrollments/<solicitud UUID>/<archivo UUID>.<extensión>`; el contenido se guarda
en el proveedor. En S3, la clave es `<prefijo><ruta relativa>`.

La autorización institucional y del solicitante se resuelve antes de leer el
archivo. El navegador descarga a través de la API; no recibe credenciales S3 ni
URLs públicas. No hace falta configurar CORS en S3 para este flujo.

Los permisos, la validación y la auditoría de documentos siguen en inscripciones;
no se convierten en reglas globales para otras imágenes o archivos. El journal de
inscripciones consume el servicio común y conserva su limpieza transaccional.

Las variables canónicas son `STORAGE_PROVIDER`, `STORAGE_LOCAL_DIR`,
`STORAGE_S3_BUCKET`, `STORAGE_S3_PREFIX`, `STORAGE_S3_ENDPOINT`, `STORAGE_S3_REGION`,
`STORAGE_S3_ACCESS_KEY_ID`, `STORAGE_S3_SECRET_ACCESS_KEY` y `STORAGE_S3_SESSION_TOKEN`.
`STORAGE_S3_ENDPOINT` vacío mantiene los valores
predeterminados del SDK para AWS. Con un endpoint explícito se usa direccionamiento
por ruta y se desactiva `chunked encoding`, según la configuración recomendada de R2.

Con `STORAGE_PROVIDER=s3`, `STORAGE_S3_ACCESS_KEY_ID` y
`STORAGE_S3_SECRET_ACCESS_KEY` son obligatorias; el token de sesión es opcional
para credenciales temporales. Las claves son independientes del proveedor. Una
configuración ausente o parcial se rechaza al arrancar. No se usa la cadena
automática de credenciales del SDK ni se consultan perfiles o roles de instancia.
La región se toma únicamente de `STORAGE_S3_REGION`, con valor predeterminado
`us-east-1`.

Otros servicios deben implementar `PutObject`, `GetObject` y `DeleteObject` con
la configuración anterior. Validar cada servicio antes de activarlo; no se afirma
compatibilidad con toda la API de S3 ni con proveedores no probados.

## Preparar la cuenta y el bucket en AWS

1. Crear la cuenta AWS y un bucket S3 de propósito general con nombre único.
   Elegir la región donde se guardará la documentación y usar la misma en
   `STORAGE_S3_REGION`. Separar los buckets de desarrollo, staging y producción.
2. Mantener **Block Public Access** habilitado y **Object Ownership: Bucket owner
   enforced** (ACL deshabilitadas). La API no envía ACL ni administra el bucket.
3. Mantener el cifrado predeterminado **SSE-S3** para la configuración inicial.
   KMS queda fuera de esta entrega. El cliente utiliza el cifrado predeterminado
   del bucket. Exigir HTTPS mediante la política preparada en
   `bemol-infra/deploy/aws/storage.yaml`.
4. Crear una identidad exclusiva para la API y asociarle la política siguiente.
   Proporcionar sus claves mediante las variables `STORAGE_S3_*` del entorno
   privado del contenedor.

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
STORAGE_S3_REGION=us-east-1
STORAGE_S3_PREFIX=bemol/
STORAGE_S3_ENDPOINT=
STORAGE_S3_ACCESS_KEY_ID=REEMPLAZAR
STORAGE_S3_SECRET_ACCESS_KEY=REEMPLAZAR
STORAGE_S3_SESSION_TOKEN=
```

Usar la región real del bucket. Si las credenciales son temporales, completar
también `STORAGE_S3_SESSION_TOKEN` y renovar los tres valores al vencer. Las claves
de acceso y secreta no pueden quedar vacías con el proveedor `s3`. Nunca usar
credenciales de la cuenta raíz ni versionar secretos.

`compose.yaml` carga `.env.dev` mediante `env_file`. El contenedor no hereda
automáticamente las variables del shell. Al ejecutar Java directamente, exportar
las variables `STORAGE_S3_*`: los archivos `.env` no se cargan automáticamente
fuera de Compose y no se utilizan los perfiles del SDK.

Después de completar la configuración, recrear el servicio con el flujo habitual
`make dev`. La selección es global para todos los módulos.

## Usar Cloudflare R2

Se reutilizan `STORAGE_PROVIDER=s3`, el SDK y `S3StorageService`; no existe un
proveedor `r2` separado ni hace falta cambiar el frontend.

1. Crear un bucket R2 privado y distinto por entorno. No habilitar acceso público
   mediante `r2.dev` ni dominios personalizados.
2. Crear un token R2 con permiso **Object Read & Write** limitado al bucket de la
   aplicación. Guardar su **Access Key ID** y **Secret Access Key** en el archivo
   privado del entorno; no usar el token Bearer de la API de Cloudflare.
3. Usar el endpoint S3 de la cuenta mostrado por Cloudflare, sin incluir el bucket.
   Los buckets con jurisdicción requieren su endpoint específico.

```dotenv
STORAGE_PROVIDER=s3
STORAGE_S3_BUCKET=NOMBRE_DEL_BUCKET
STORAGE_S3_PREFIX=bemol/
STORAGE_S3_ENDPOINT=https://ACCOUNT_ID.r2.cloudflarestorage.com
STORAGE_S3_REGION=auto
STORAGE_S3_ACCESS_KEY_ID=ACCESS_KEY_ID_DE_R2
STORAGE_S3_SECRET_ACCESS_KEY=SECRET_ACCESS_KEY_DE_R2
STORAGE_S3_SESSION_TOKEN=
```

Las mismas variables `STORAGE_S3_*` sirven para R2 y otros servicios compatibles.
No hace falta una cuenta AWS, políticas IAM, Workers ni CORS para el flujo actual
de descarga a través de la API. No configurar expiración de documentos activos ni
retenciones que impidan la limpieza física. La comprobación de arranque también
escribe, lee y elimina un objeto temporal en R2.

## Assets públicos y logo de Bemol en emails

El bucket configurado en `STORAGE_S3_*` contiene documentación privada: no habilitar
su acceso público. Para imágenes estáticas que cualquiera puede ver, crear otro
bucket, por ejemplo `bemol-assets-dev`. Publicar un prefijo dentro del bucket de
documentos no reemplaza esta separación.

Para comenzar desde el panel de Cloudflare:

1. En **R2 → Overview → Create bucket**, crear `bemol-assets-dev` con clase
   **Standard** y ubicación automática.
2. Abrir ese bucket y usar **Upload** para cargar `logo-email.png` en la raíz.
   El archivo existente está en `bemol-ui/public/brand/logo-email.png`.
3. Sólo en el bucket de assets, ir a **Settings → Public Development URL → Enable**
   y confirmar el acceso público. Ese acceso permite leer sus objetos, pero no
   habilita subidas anónimas. No guardar allí documentos ni información personal.
4. Copiar la URL pública y agregar `/logo-email.png`. Abrirla sin sesión de
   Cloudflare y comprobar que devuelve la imagen.
5. Completar el archivo privado del entorno, sin modificar el destino documental:

```dotenv
BRAND_LOGO_URL=https://pub-REEMPLAZAR.r2.dev/logo-email.png
```

`r2.dev` es sólo para desarrollo. Para producción, configurar **Settings → Custom
Domains → Add** en el bucket de assets y usar una URL HTTPS de ese dominio. Debe
ser una URL pública estable y sin credenciales, consulta ni fragmento; no usar una
URL prefirmada, porque el logo dejaría de cargar en los emails cuando venza.

La API únicamente coloca esta URL en el HTML: no necesita nuevas credenciales S3,
no descarga el asset al renderizar el correo y no administra el bucket público.
Las subidas iniciales se hacen desde el panel. No hace falta agregar un endpoint
de uploads ni configurar CORS para insertar la imagen en un email.

Si `BRAND_LOGO_URL` está vacío, se mantiene el logo del frontend en
`/brand/logo-email.png`. El valor se usa en los emails de plataforma y como respaldo
para instituciones sin logo propio; no reemplaza los logos institucionales. Sus
links de acceso y recuperación siguen usando los orígenes canónicos existentes.

Después de cambiar el entorno hay que recrear la API. En desarrollo, usar `make dev`
para reconstruir el código actual y aplicar las variables. No alcanza con
reiniciar un contenedor con un entorno anterior.

## Staging y producción

La configuración compartida pertenece a `bemol-infra`. Su `.env.staging.example`
documenta las mismas variables y `compose.yaml` las pasa al contenedor `api`.
Completar el archivo privado del entorno en el host correspondiente y seguir
`bemol-infra/docs/S3.md` para validar y aplicar la configuración. Staging es el
único entorno compartido configurado actualmente en ese repositorio.

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
También deben realizarse con R2 antes de habilitarlo en un entorno compartido.

## Cambiar de proveedor

La configuración no copia archivos. Esta entrega parte de datos de prueba y no
incluye compatibilidad ni migración de adjuntos anteriores. Para comenzar con S3,
usar un entorno de prueba limpio y configurar el bucket y las credenciales.
Los registros de nuevas cargas y eliminaciones conservan su destino: el cambio no
puede redirigir trabajos existentes a otro proveedor. En AWS sin endpoint explícito,
la ubicación sigue siendo el bucket; con endpoint explícito se registra
`<endpoint sin barra final>/<bucket>`. Así, cambiar de cuenta R2 o de servicio se
detecta aunque el bucket tenga el mismo nombre. Cambiar `STORAGE_S3_ENDPOINT`
tampoco migra objetos ni actualiza el journal. Ver
[el control operativo de destinos](../enrollment/SEGURIDAD-DOCUMENTAL.md#destinos-y-backups).

## Referencias

- [Límites de espera del SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/timeouts.html).
- [Cloudflare R2 con AWS SDK para Java](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/).
- [Credenciales y endpoints de R2](https://developers.cloudflare.com/r2/api/tokens/).
- [Acceso público y dominios de R2](https://developers.cloudflare.com/r2/buckets/public-buckets/).
- [Subida de objetos desde el panel de R2](https://developers.cloudflare.com/r2/objects/upload-objects/).
- [Seguridad de S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html).
- [Permisos y respuestas de GetObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html).
- [AWS Free Tier](https://aws.amazon.com/free/): revisar las condiciones al crear la
  cuenta. La oferta para cuentas nuevas utiliza créditos y un plan gratuito de
  hasta seis meses; no asumir los antiguos doce meses gratuitos de S3.
