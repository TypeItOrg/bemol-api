# Exportación de solicitudes en PDF

La API convierte una plantilla Thymeleaf autocontenida mediante Chrome for Testing
157.0.8103.38. No necesita Playwright, servicios externos ni descargas al iniciar.
El logo y Manrope se embeben; los textos son escapados y no se ejecutan scripts ni
se cargan recursos externos. La fuente conserva su licencia en
`src/main/resources/pdf/Manrope-OFL.txt`.

## Diseño de la planilla

El PDF usa un formulario institucional, no los componentes visuales de la web.
El título principal lleva una banda oscura y las secciones usan franjas grises
de borde a borde, con un contorno oscuro completo. El marco común se conserva en
los saltos de página; el espacio interior se aplica a los datos, no a los
encabezados ni a las líneas de la tabla.
El marco aporta los laterales de los encabezados; sus separadores horizontales
usan el mismo trazo de 0,75 pt. Las uniones con el título, la tabla y los bordes
de página comparten línea para no acumular grosores.
Se reserva 0,75 pt por fuera del marco, a la derecha y abajo de cada fragmento,
para que Chromium no recorte el borde al ajustar el área imprimible a su
cuadrícula de píxeles.
El logo se ajusta, sin deformarse, a la altura disponible del bloque de texto;
no impone una altura fija. El nombre y contacto van centrados, y las fechas a la
derecha, compactas, sin divisor vertical y alineadas al mismo margen izquierdo.
Ambos bloques de fecha comparten el mismo ancho. La fecha de presentación solo
aparece cuando está disponible. Se mantiene Manrope embebida, A4, paginación y espacios
de firma configurables. La última selección académica comparte grupo de impresión
con las firmas, para evitar una hoja con las firmas aisladas. En la tabla, el
espacio y su plan se alinean a la izquierda; nivel, instrumento y estado se
centran tanto vertical como horizontalmente.

Se conservan los datos y las condiciones de aparición de los campos de inscripción.
Los valores faltantes se muestran como «Sin informar», en gris y cursiva, tanto
en los campos como en la cabecera y la tabla académica. Las respuestas negativas
conservan el estilo normal; no se sustituyen valores por renglones vacíos ni se
agregan declaraciones al formulario. No se imprime una leyenda técnica fija al
final; el pie solo aparece cuando la institución configura su propio texto.

## Desarrollo

Las imágenes `dev` y `prod` instalan el mismo navegador durante el build, en
`/opt/chrome/chrome`. `BEMOL_ENROLLMENT_PDF_CHROMIUM_EXECUTABLE` permite señalar una
instalación equivalente al ejecutar Java fuera del contenedor. El proceso recibe
un entorno mínimo, sin credenciales de la API, y usa `setsid`/`kill` de Linux para
aislar y terminar su grupo de procesos.

`make dev` ejecuta ahora Java y el watcher como `gradle`, no como root: Chromium
necesita un usuario sin privilegios. El entrypoint prepara como root únicamente
los propietarios de `/workspace/storage` y `/home/gradle/.gradle`, sin seguir
enlaces simbólicos ni cambiar modos, y se reejecuta inmediatamente como `gradle`.
Esto conserva el contenido de volúmenes locales antiguos creados por root sin
borrarlos ni recrearlos. La imagen productiva conserva `appuser` desde su inicio.
El Compose local aplica `no-new-privileges` y el perfil seccomp versionado descrito
debajo; no desactiva el sandbox de Chromium ni AppArmor.

## Perfil seccomp

`docker/chromium-seccomp.json` parte del perfil predeterminado de Moby seccomp
v0.2.3, utilizado por Docker Engine 29.8.0. La base está fijada al commit
`836ae4d37ef2ec995c77c99fc55f5b5f3af3a897`; su archivo original tiene SHA-256
`536529b665dd0972c37bfb569f5d4ac8a53592e7b00752bc39ff063ca9864c74`.
La licencia Apache 2.0 se conserva en `docker/chromium-seccomp.LICENSE`.

Fuentes: [perfil base inmutable](https://raw.githubusercontent.com/moby/profiles/836ae4d37ef2ec995c77c99fc55f5b5f3af3a897/seccomp/default.json)
y [dependencia del Engine](https://github.com/moby/moby/blob/docker-v29.8.0/go.mod).

Se conservan todas las reglas originales y se añaden cinco excepciones, solo para
`amd64` y `arm64`, con los argumentos exactos utilizados por Chromium 157:

| Llamada | Argumento permitido |
| --- | --- |
| `clone` | `268435473`: `CLONE_NEWUSER \| SIGCHLD` |
| `clone` | `1879048209`: `CLONE_NEWUSER \| CLONE_NEWPID \| CLONE_NEWNET \| SIGCHLD` |
| `clone` | `536870929`: `CLONE_NEWPID \| SIGCHLD` |
| `unshare` | `268435456`: `CLONE_NEWUSER` |
| `chroot` | Encierro del proceso dentro de su nuevo namespace de usuario |

`chroot` sigue sujeto a los permisos del kernel; no se añade `CAP_SYS_CHROOT` ni
`CAP_SYS_ADMIN` al contenedor. `clone3`, `setns` y las demás llamadas conservan la
política original. La prueba `ChromiumSeccompProfileTest` verifica las cinco reglas
y la integridad de la base, eliminando únicamente esas extensiones antes de
comparar su hash normalizado.

El archivo es leído por Docker desde el host antes de crear el contenedor. La
copia de `bemol-infra/security/chromium-seccomp.json` y su licencia deben conservar
exactamente los mismos bytes. Su preparación no supone un despliegue ni valida
el kernel de otro servidor.

Pruebas focalizadas:

```sh
./gradlew fastTest --tests '*ChromiumSeccompProfileTest' --tests '*ChromiumEnrollmentPdfRendererTest'
BEMOL_ENROLLMENT_PDF_CHROMIUM_EXECUTABLE=/opt/chrome/chrome \
  ./gradlew integrationTest --tests '*ChromiumEnrollmentPdfRendererIntegrationTest'
```

La prueba de navegador real se omite si el ejecutable no existe; ese resultado no
valida el runtime. Cuando existe, debe poder ejecutar su sandbox con el perfil
acotado, sin capacidades añadidas ni flags que lo desactiven. La suite cubre A4,
acentos, valores negativos, datos incompletos,
logo ausente/horizontal/vertical y selecciones multipágina. La inspección visual
de todas las páginas sigue siendo necesaria. Los ejemplos ficticios HTML/PDF se
conservan en `build/reports/enrollment-pdf` para esa revisión.

## Historial de preparación

El 5 de noviembre de 2026 pasaron los tres casos reales con Chrome 157.0.8103.38
instalado en el host, con su sandbox activo. La imagen productiva construye, pero
el smoke local con usuario no privilegiado, raíz de solo lectura, capacidades
eliminadas, `no-new-privileges`, 1 CPU, 1 GiB y temporales de 256 MiB falla con
`No usable sandbox!`. No se pudo realizar la calibración de veinte exportaciones
bajo esos controles; el runtime compartido no debe considerarse aprobado.

El 6 de noviembre de 2026 se autorizó el perfil seccomp acotado. Un canario en
Docker generó un PDF real con usuario `10001`, raíz de solo lectura,
`cap_drop: ALL`, `no-new-privileges` y AppArmor `docker-default` sin modificar.
La vista previa autenticada de Bemol también mostró el PDF desde la API local en
Docker, tanto en escritorio como en móvil. No se guardó la configuración ni se
desplegó ningún cambio.

### Calibración local en Docker

La imagen productiva local `bemol-api:pdf-seccomp-local`, con Java 21.0.13 y Chrome
157.0.8103.38, pasó el control con el contexto Spring completo y dependencias
ficticias aisladas. Cada candidato ejecutó una primera generación en frío y otras
20 secuenciales, con 1 CPU y temporales de 256 MiB. No se cambiaron los porcentajes
de heap ni las protecciones indicadas. Se midió el pico del cgroup completo
(JVM y Chromium) y el uso de temporales cada 10 ms.

| RAM disponible | Pico total | Margen libre | Resultado |
| --- | --- | --- | --- |
| 1 GiB | 1024 MiB | 0 % | No cumple el margen mínimo del 25 % |
| 1,5 GiB | 1054,77 MiB | 31,33 % | Aprobado; menor candidato válido |
| 2 GiB | 1120,38 MiB | 45,29 % | Aprobado; no es necesario |

Con **1536 MiB**, la primera generación tardó 1717 ms y las 21 estuvieron entre
1104 y 1717 ms; el pico de temporales fue 2,97 MiB. Los 21 PDFs completos se
validaron con PDFBox. Las tres variantes ficticias (sin logo, logo horizontal y
vertical) contienen 80 selecciones, siete páginas A4, acentos, textos largos,
respuestas negativas y datos faltantes. Se revisaron visualmente sus 21 páginas.
No quedaron archivos de render ni procesos Chromium activos o zombis.

Se observó el proceso renderer real con UID 10001, `CapEff=0`, `NoNewPrivs=1`,
namespace PID anidado y dos filtros seccomp frente al único filtro de Docker en
la JVM, conservando AppArmor `docker-default`. Esto verifica el sandbox interno,
no solo que Chromium haya producido un archivo.

Pasaron las diez pruebas focalizadas de renderer y perfil, `spotlessCheck` y la
construcción de la imagen. No se ejecutó la suite completa. Los ejemplos,
métricas y el harness temporal se conservaron como evidencia local, sin añadir
endpoints ni herramientas de benchmark a la aplicación.

El control local amd64 está aprobado. No demuestra compatibilidad con el kernel
del VPS ni valida arm64; comprobar esos entornos antes de desplegar. No hubo
commits, publicación de imágenes ni despliegues.

## Límites y fallos

Hay un render activo por instancia, sin cola. Otro pedido recibe `503` con
`Retry-After: 5`. El límite externo es de diez segundos; al vencer se termina el
grupo de procesos, se descarta la salida y se limpian los temporales privados.
Solo se entregan archivos completos, con salida exitosa, cabecera y final PDF,
hasta 16 MiB. Los archivos de trabajo no son almacenamiento histórico.

El kernel y el perfil de seguridad deben admitir el sandbox de Chromium para el
usuario no privilegiado. Mantener el perfil versionado y no agregar `--no-sandbox`,
capacidades, ejecución como root o cambios al host para sortear una incompatibilidad.
Un fallo de sandbox es un bloqueo operativo a resolver explícitamente, no una
validación exitosa.
