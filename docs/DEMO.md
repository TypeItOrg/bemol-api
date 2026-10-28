# Preparación y recorrido de la demo

El dataset cubre el recorrido de inscripción y casos alternativos sin reutilizar
la misma solicitud para acciones incompatibles. Usa el modelo actual por trayecto
y ciclo, con períodos asociados a cada curso.

## Carga local desde cero

Para una base local de pruebas descartable, desde el repositorio de la API:

    make reset-data
    make dev

Esperar a que la API termine las migraciones y la inicialización de roles.
Después, en otra terminal:

    SEED_INSTITUTION=019e18e4-d919-76d8-9848-7f1b14e64452 make seed-demo

El comando reset-data elimina los volúmenes locales de PostgreSQL y adjuntos.
No usarlo en el VPS: sus volúmenes y operación pertenecen a bemol-infra.
La recreación del VPS se realiza sobre su destino de prueba identificado.

La institución se identifica por UUID. El año predeterminado es el actual.
El seed exige que su ciclo esté activo y que los períodos permitan operar ahora.
Una carga con otro ciclo activo se
rechaza y revierte, en lugar de dejar una oferta que no permita inscribirse.
Se conservan los períodos de develop: Vocacionales del 01/08 al 30/11
y Profesorado del 01/09 al 15/12, con cinco planes y uno respectivamente.

## Ejecución en el VPS

Usar los archivos de este checkout actualizado y una API ya inicializada.
La base debe contener la institución activa y sus roles. Seleccionar el proyecto,
archivos de entorno y servicios reales, por ejemplo con el Compose preparado:

    COMPOSE_PROJECT_NAME=nombre-real-del-proyecto \
    COMPOSE_FILE=/opt/bemol-infra/compose.yaml:/opt/bemol-infra/compose.staging.yaml \
    COMPOSE_ENV_FILES=/opt/bemol-infra/.env.staging,/opt/bemol-infra/build/staging/domains.env \
    SEED_API_SERVICE=api SEED_DB_SERVICE=postgres \
    SEED_INSTITUTION=019e18e4-d919-76d8-9848-7f1b14e64452 make seed-demo

Confirmar los nombres y rutas contra el despliegue real. Este comando no despliega
aplicaciones. SEED_COMPOSE_FILE permite indicar un único archivo Compose;
para una combinación usar COMPOSE_FILE. Ejecutar desde el checkout de la API,
pues de allí se leen los SQL y PDFs.

Con SEED_TRANSPORT=psql, usar las variables libpq habituales e indicar
SEED_STORAGE_DIR, el storage local de la API accesible a esa shell.
El loader requiere almacenamiento local; no carga los archivos en S3.

## Cuentas de demostración

La contraseña pública para cuentas nuevas es **BemolDemo2026!**. Las cuentas
existentes conservan su contraseña. El administrador de plataforma se crea por
el bootstrap configurado de la API, no por este dataset.

| Documento | Persona | Uso |
| --- | --- | --- |
| 99000001 | Lucía Ferreyra | Solicitud enviada al CAVI, DNI pendiente de revisión. |
| 99000002 | Mateo Soria | Postulante sin solicitudes: iniciar una inscripción desde cero. |
| 99000003 | Camila Roldán | Tres cursadas activas de CAVB Instrumento, con horarios. |
| 99000004 | Julián Pereyra | Piano ocupa el segundo turno; Lenguaje Musical ya está aprobado y tiene historial. |
| 99000005 | Valeria Molina | Docente de Piano y de clases grupales; consultar estudiantes y horarios. |
| 99000006 | Gabriel Acosta | Docente de Guitarra y de clases grupales. |
| 99000007 | Mariana Suárez | Responsable familiar sin privilegios administrativos. |
| 99000008 | Nicolás Herrera | Autoridad institucional para configuración, revisión y resolución. |
| 99000009 | Ana Vega | Representante sin perfil de postulante o estudiante. |
| 99000010 | Sofia Rios | Postulante y representante. |
| 99000011 | Diego Luna | Estudiante y representante. |
| 99000021 | Sofía Ferreyra | Borrador completo, cursos elegidos y documentación pendiente de cargar. |
| 99000022 | Bruno Ferreyra | Vinculación pendiente con Mariana, con partida de nacimiento de muestra. |
| 99000023 | Elena Vega | Admisión provisoria y cursadas; falta certificado de aptitud física para confirmar. |
| 99000024 | Tomás Benítez | Solicitud enviada con DNI observado, disponible para demostrar rechazo. |
| 99000025 | Paula Castro | DNI reemplazado: versión anterior observada y nueva pendiente de revisión. |
| 99000026 | Santiago Luna | Admitido, en espera para Piano de CAVB Instrumento, sin turnos libres. |
| 99000027 | Laura Díaz | Administrativa con acceso limitado a CAVB Instrumento. |
| 99000028 | Florencia Ruiz | Solicitud ya rechazada, con motivo e historial. |
| 99000029 | Martín Torres | Borrador cancelado y cursos cancelados. |

Mariana tiene vínculos activos con Lucía, Camila y Sofía; el de Bruno requiere
resolución institucional. Seleccionar a cada persona para operar con su identidad.
Los vínculos y personas son ficticios. Lucía y Sofía no tienen cuenta propia; Camila tiene el ingreso propio suspendido y sigue siendo representable. Los casos de representación agregados en develop se conservan.

## Documentación

Los requisitos se asignan a los trayectos utilizados por los escenarios:

| Requisito | Exigencia | Demostración |
| --- | --- | --- |
| Documento Nacional de Identidad (DNI) | Obligatorio al enviar | Bloqueo sin archivo, revisión, observación y reemplazo. |
| Certificado de aptitud física | Obligatorio para confirmar | Admisión provisoria, carga posterior y confirmación. |
| Presentación musical personal | Opcional | Una entrega opcional no bloquea la admisión. |

Se admiten PDF, JPEG y PNG. Los PDFs están en src/main/resources/db/seed/assets.
Los documentos nuevos se identifican como muestras ficticias sin validez:
no son reproducciones oficiales ni contienen datos personales reales.
Su contenido genérico se reutiliza entre solicitudes.

El script assets/generate_samples.py regenera los PDFs nuevos con Python y
ReportLab. El seed usa los PDFs versionados y no necesita esas dependencias.

## Recorrido central

1. Como Nicolás, mostrar la oferta y los requisitos del CAVI.
2. Como Mariana, seleccionar a Sofía y retomar su borrador.
3. Intentar enviar sin DNI, cargar la muestra y enviar. El certificado puede faltar.
4. Como Nicolás, revisar el DNI y admitir provisoriamente.
5. Incorporar los cursos y asignar el turno de Guitarra. La admisión y la
   incorporación efectiva son acciones diferentes.
6. Como Mariana, cargar el certificado de Sofía. Como Nicolás, aceptarlo y
   confirmar definitivamente. Descargar el DNI adjunto para mostrar la consulta
   de documentación almacenada.
7. Volver a Mariana para consultar cursadas y horarios; Camila es un respaldo
   con tres cursadas ya preparadas.
8. Como Valeria o Gabriel, consultar clases y estudiantes.

Para ingresar a una institución recién creada desde plataforma, preparar primero
su autoridad y oferta. El dataset requiere una institución existente y activa.

## Casos de respaldo y orden de las acciones

- Lucía permite revisar y aprobar sin hacer previamente todo el formulario.
- Tomás permite rechazar sin consumir el caso de aprobación.
- Paula permite descargar la versión observada y revisar el reemplazo actual.
- Elena permite completar una admisión provisoria ya preparada.
- Bruno permite revisar el respaldo y aprobar una vinculación pendiente.
- Santiago permite mostrar una espera con falta de cupo real. Dar de baja el Piano
  de Julián libera un turno; incorporar a Santiago requiere confirmar explícitamente
  su comisión y horario.
- Laura permite contrastar el acceso a CAVB Instrumento con otro trayecto.
- Julián tiene un resultado previo con historial. Registrar un resultado en una
  cursada activa la finaliza y libera horarios: hacerlo después de mostrar la
  grilla y el caso de cupos.

El docente consulta sus clases; los resultados académicos son una acción
administrativa. El recorrido no presupone carga de notas numéricas.
La exportación de una planilla de solicitud PDF no aparece en el develop
revisado; la descarga demostrable corresponde a los documentos adjuntos.

## Repetición del ensayo

La carga es transaccional e idempotente: repetirla conserva el estado que dejó
la UI, archivos, contraseñas y permisos. Al terminar imprime el estado actual
de cada caso.

Si detecta solicitudes del seed anterior por plan, solicita recrear la base
de prueba. No convierte esos registros ni modifica migraciones históricas.
Para recuperar todos los puntos de partida después de un ensayo local,
recrear el entorno descartable y repetir la carga inicial.

Los PDFs se copian antes de la transacción SQL. Un error revierte la base
de datos, pero puede dejar los archivos de muestra sin referencias.

## Verificación local del 28/10/2026

Se usaron PostgreSQL 18, Redis y la API compilada desde develop actualizado,
en contenedores descartables. No se modificó el VPS.

- Carga de los diez escenarios con las migraciones y constraints reales.
- Ingreso de autoridad, representante, docente, administrativa limitada y los
  tres perfiles de representación incorporados en develop.
- Consulta representada de personas sin cuenta y con ingreso propio suspendido.
- Consulta administrativa y denegación de acceso fuera del trayecto de Laura.
- Rechazo de aprobación con DNI pendiente; aceptación después de revisarlo,
  sin crear estudiante antes de la incorporación efectiva.
- Envío bloqueado sin DNI y exitoso después de adjuntarlo.
- Circuito de Sofía: admisión provisoria, tres incorporaciones, horarios,
  certificado, revisión y confirmación definitiva.
- Confirmación de Elena bloqueada sin certificado y exitosa después de revisarlo.
- Rechazo con motivo, descarga del DNI histórico y aprobación del vínculo de Bruno.
- Consulta de horarios representados y de estudiantes desde la clase docente.
- Lista de espera: incorporación rechazada sin cupo y exitosa tras dar de baja
  el Piano de Julián.
- Reejecución del seed después del ensayo: mismas solicitudes, selecciones,
  roles, alcances, cursadas, vínculos y adjuntos.

Los recorridos se verificaron mediante HTTP autenticado. No se ensayó la
interfaz en navegador ni se comprobó envío de correo externo.
