# Revisión de permisos y alcances — 22/10/2026

La revisión abarca los repositorios actuales `bemol-api`, `bemol-ui` y la configuración de `bemol-infra`. Se crearon datos locales descartables y se revisaron autorización, delegación, consultas, sesiones, interfaz y persistencia. No se desplegaron cambios en entornos compartidos.

## Qué significa el aviso de permisos inactivos

El alcance pertenece a **cada asignación de rol**. Hay permisos que operan sobre entidades compartidas por toda la institución y otros que admiten uno o más trayectos.

| Catálogo | Cantidad | Comportamiento |
| --- | ---: | --- |
| Permisos que admiten trayectos | 40 | Se limitan a los trayectos de la asignación. |
| Permisos que requieren toda la institución | 44 | No se otorgan desde una asignación por trayecto. |
| Permisos configurables | 82 | Disponibles en el editor y verificados también en Chromium. |
| Reservas de calificaciones | 2 | No configurables; no hay una operación funcional implementada para probar. |

Un rol mixto con **Ver usuarios** y **Ver cursos**, limitado al Profesorado, permite consultar cursos de ese trayecto y no permite gestionar usuarios. Se comprobó la respuesta `403` de usuarios. Un permiso global otorgado por otro rol sigue vigente: el cálculo une alcances **por permiso**, sin ampliar los restantes.

Se eliminó el bloque explicativo permanente. El alcance ocupa todo el ancho; los trayectos se muestran debajo, también a todo el ancho, con su contenedor, etiquetas y selector anteriores. Si el alcance excluye permisos, un control breve indica cuántos: el detalle se abre sólo al solicitarlo y aclara que otros roles pueden otorgarlos. Los roles que sólo admiten alcance institucional siguen sin ofrecer la selección de trayectos.

## Fallos corregidos

### P1 — Las selecciones de cursada se devolvían sin su permiso de lectura

`GetEnrollmentApplicationUseCase` pedía lectura de la inscripción y solicitaba siempre sus cursadas. La UI tenía un control separado, pero el payload podía incluirlas aunque faltara `ENROLLMENT_APPLICATION_COURSE_READ`.

`EnrollmentApplicationCourseAuthorization` controla ahora ese campo en la fábrica de respuestas, incluida la ruta compatible. El mismo control se aplica a `data.courses` y a las selecciones históricas de espacios e instrumentos del formulario; ocultar sólo la lista principal dejaba otra representación de esos datos expuesta. Se conservan el acceso propio validado en autoservicio y el de plataforma. Las pruebas HTTP usan JWT y sesiones persistidas, sin desactivar filtros: ausencia del permiso, alcance institucional, trayecto autorizado, otro trayecto y titular.

También se agregó la dependencia de lectura de inscripción al permiso de lectura de cursadas. La migración `20261022120008__require_application_read_for_course_selections.sql`, creada con `make migration`, completa esa dependencia en roles existentes. La prueba de upgrade parte de la versión anterior, conserva datos, comprueba que otros roles no reciban el permiso y valida una segunda ejecución sin pendientes.

### P2 — Se ocultaban roles después de la primera página

Con 57 roles, el formulario institucional cargaba 50 y no tenía un mecanismo para alcanzar los demás. Además, un rol asignado fuera de esa página perdía los metadatos necesarios para editar su alcance. La variante con permiso de revocación sin asignación tampoco cargaba esos metadatos.

La asignación conserva la lista de «Roles disponibles» con su botón «Asignar» por fila. Los catálogos que superan una página ofrecen búsqueda y «Ver más roles». El filtro de usuarios también usa búsqueda y paginación. Las asignaciones existentes cargan su metadata por ID cuando hace falta. Los formularios de plataforma también usan opciones paginadas. Las rutas proxy mantienen separadas las sesiones institucional y de plataforma, validan los parámetros y conservan las respuestas de denegación.

### P2 — El mensaje no explicaba la regla ni distinguía otros roles

El texto anterior presentaba como cambio futuro un estado que podía estar ya guardado y no explicaba la diferencia entre permisos compartidos y permisos por trayecto. La explicación nueva se refiere expresamente a la asignación y muestra los nombres afectados. Las asignaciones que requerirían revocar roles sin permiso mantienen su botón deshabilitado.

### P2 — Aprobar dependía de una lectura documental que el permiso no requería

El servidor permitía aprobar con el permiso de aprobación, pero el indicador usado por el botón también exigía lectura de adjuntos. Un rol con aprobación y sus dependencias declaradas podía ejecutar la operación y no encontrar la acción disponible en la UI. Los indicadores `canConfirm` y `canApproveProvisionally` se alinearon con la autorización y las reglas documentales del caso de uso. No se conceden permisos adicionales para leer documentos. La regresión HTTP comprueba el indicador y ejecuta la aprobación con ese rol mínimo, manteniendo ocultos documentos y cursadas no autorizados.

### Consistencia de catálogo y documentación

Se agregó `institution:audit:read` al catálogo tipado de la UI, que ahora contiene los mismos 84 códigos que el backend. La documentación de autorización se actualizó para reflejar los dos snapshots cacheados, sus nombres actuales, la unión de alcances y la revocación de sesiones.

Se corrigió también una incompatibilidad del verificador de infraestructura: algunas versiones de Compose omiten booleanos `false` al exportar JSON. El test ahora comprueba tanto la configuración explícita `create_host_path: false` del YAML como cualquier valor normalizado presente, sin cambiar la configuración de despliegue.

## Pruebas y límites de cobertura

La [matriz CSV](matriz-permisos-2026-10-22.csv) contiene una fila por cada permiso, incluyendo los reservados, su capacidad de limitarse a trayectos y la verificación correspondiente.

| Capa | Verificación |
| --- | --- |
| PostgreSQL | Los 84 permisos: sin asignación, institución, trayectos, unión de roles, permiso ajeno, otra institución, retiro del grant global, revocación, plataforma y ausencia de sesión. |
| Aspectos de controladores | Cada método anotado se prueba con cada uno de los 84 permisos, sin permisos y sin autenticación. Se ejecuta el aspecto real sobre cuerpos de controlador simulados; esta capa no representa todos los flujos de negocio. |
| HTTP y negocio | Filtros, JWT, sesiones y PostgreSQL reales para el detalle de inscripción y las nuevas opciones de rol. La suite completa conserva las pruebas de cursos, documentos, auditoría, delegación y concurrencia. |
| UI | Helpers para los 84 códigos, controles de alcance, roles de páginas posteriores, metadatos asignados, errores y separación de sesiones. |
| Navegador | Chromium, 1440×1000 y 390×844. Se eligieron y persistieron los 82 permisos configurables y se recorrió la asignación y modificación de alcances. |
| Infraestructura | Revisión de Compose, Redis persistente, TTL e invalidación; comprobaciones de configuración y workflows con el verificador existente. |

Resultados de validación:

- Suite completa de API: **1.186 pruebas aprobadas**, sin omitidas; Spotless aprobado.
- Después del repaso de serialización y capacidades de aprobación: **105 pruebas rápidas de inscripción** y sus regresiones PostgreSQL/HTTP, incluyendo datos históricos de espacios e instrumentos y aprobación sin lectura documental.
- Interfaz: **1.196 pruebas aprobadas**, con el timeout habitual de CI; TypeScript, ESLint completo, Prettier y build de producción de Next.js aprobados.
- Análisis del backend: **NullAway y ECJ aprobados** para código principal y tests; **codeStructureCheck aprobado**, sin ampliar sus límites.
- Infraestructura: **make test aprobado**, incluidos backups, 13 comprobaciones de configuración y 10 del verificador de aceptación.

La matriz de grants usa la caché `simple` del perfil de test; el recorrido de navegador utiliza la API con PostgreSQL 18 y Redis 7 locales. Las pruebas de integración de autenticación también conservan su infraestructura real. Esta evidencia no equivale a ejecutar cada combinación posible de datos de negocio ni a una comprobación de staging o producción.

Las reservas `INSTITUTION_GRADES_ENTER` y `INSTITUTION_GRADES_ENTER_FINAL` se probaron en el motor de resolución, pero no se presentan como funcionalidades operativas. `INSTITUTION_READ` habilita la pantalla de gestión; los datos públicos de institución siguen teniendo acceso público deliberado.

## Evidencia del navegador

El recorrido comprobó:

1. Buscar un rol fuera de los primeros 50, asignarlo y guardar desde el formulario.
2. Ver sus metadatos de alcance después de recargar.
3. Denegación de gestión de usuarios con el rol mixto limitado a un trayecto.
4. Ampliación a toda la institución y acceso permitido después de volver a ingresar.
5. Reducción a un trayecto y nueva denegación después de volver a ingresar.
6. Revocación de las sesiones anteriores al guardar cambios de roles y alcances.
7. Denegación al pedir recursos de otra institución.
8. Encontrar ese mismo rol desde el filtro de usuarios.
9. Marcar los 82 permisos configurables en el editor y comprobar su persistencia exacta en la API.
10. Etiquetas de controles y ausencia de desborde horizontal en escritorio y móvil.
11. Seleccionar sólo lectura de cursadas marca y bloquea automáticamente la dependencia de lectura de inscripción; se guardan exactamente ambos permisos.

Las capturas y los resultados del recorrido están en [evidencias-permisos-2026-10-22](evidencias-permisos-2026-10-22/).

El bloque permanente se reemplaza por un detalle opcional. Las capturas de los controles de alcance son [escritorio](evidencias-permisos-2026-10-22/roles-restaurados-escritorio.png), [móvil](evidencias-permisos-2026-10-22/roles-restaurados-movil.png) y [detalle del alcance](evidencias-permisos-2026-10-22/roles-simplificados-detalle.png). El [recorrido de los controles restaurados](evidencias-permisos-2026-10-22/roles-restaurados-recorrido.json) comprueba que alcance y trayectos ocupan todo el ancho, con los trayectos debajo, sus etiquetas y contenedor anteriores, selección y eliminación funcionales y ausencia de desborde a 390 px. Pasaron las cuatro pruebas del selector, TypeScript y ESLint de los archivos modificados. El [recorrido previo](evidencias-permisos-2026-10-22/roles-simplificados-recorrido.json) también comprobó teclado y foco, cambios de alcance, guardado y recarga y búsqueda fuera de la primera página. Esta restauración de controles no cambia las reglas de autorización.

## Repetir la verificación

La restauración de la lista de roles se comprobó en Chromium: cargar roles posteriores a los primeros 50, buscar, asignar con el botón, guardar y recargar. Pasaron las tres suites afectadas (15 pruebas), TypeScript, ESLint y Prettier. Las capturas de [escritorio](evidencias-permisos-2026-10-22/roles-lista-original-escritorio.png) y [móvil](evidencias-permisos-2026-10-22/roles-lista-original-movil.png), junto con el [recorrido](evidencias-permisos-2026-10-22/roles-lista-original-recorrido.json), documentan ese control. El alcance y la selección de trayectos conservan su disposición vertical y sus controles anteriores.

En `bemol-api`, con JDK 21 para la aplicación, JDK 25 para NullAway y Docker disponibles:

```sh
make format
make format-check
./gradlew codeStructureCheck
make static-analysis
make test
```

Para concentrarse en los cambios de autorización:

```sh
./gradlew fastTest --tests '*PermissionControllerMatrixTest' --tests '*PermissionCodeDependencyTest'
./gradlew integrationTest --tests '*PermissionMatrixPostgresIntegrationTest' --tests '*PermissionHttpPostgresIntegrationTest' --tests '*FlywayBranchMigrationIntegrationTest'
```

En `bemol-ui`:

```sh
make format
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test --runInBand
pnpm build
```

La verificación final usa el timeout predeterminado de Jest y la misma ejecución serial de la suite utilizada en CI. No se omitieron pruebas.

Las pruebas generan sus propios datos. Para publicar estos cambios, la API debe actualizarse antes o junto con la UI, que consume las nuevas rutas de opciones; Flyway aplica la nueva dependencia de permisos. La entrega se realiza mediante commits sobre `develop` en los tres repositorios y no incluye un despliegue a entornos compartidos.
