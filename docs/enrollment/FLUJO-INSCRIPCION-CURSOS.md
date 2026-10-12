# FLUJO DE INSCRIPCIÓN POR CURSOS Y CURSADAS

Las solicitudes nuevas se inician por trayecto y se vinculan automáticamente al único ciclo lectivo activo de la institución, siempre que exista oferta abierta del trayecto para ese ciclo. El ciclo permanece fijo al retomar o cancelar la solicitud; los cursos seleccionados deben pertenecer a él. Cada hija conserva su propio período de inscripción: no se exige un único período para toda la solicitud. Los borradores se recuperan por institución, persona, trayecto y ciclo, sin impedir nuevas solicitudes de otros cursos después del envío.

La migración `20261011033241__bind_enrollment_application_academic_year.sql` recupera ciclos históricos solo cuando los cursos y períodos registrados identifican uno inequívocamente. Los registros antiguos sin contexto suficiente siguen siendo consultables; las solicitudes nuevas no pueden crearse sin ciclo.

 El postulante o estudiante selecciona cursos concretos, incluidos cursos sin instrumento y sin nivel. Puede elegir varios cursos del mismo espacio académico. El instrumento pertenece al curso ofrecido: un curso instrumental requiere instrumento; uno no instrumental no lo admite.

El catálogo informa disponibilidad actual, sin reservar cupos. Una advertencia de falta de cupos permite continuar. Los períodos individuales se generan al crear los horarios y se consultan sin escrituras. Los cursos grupales usan el horario de la clase sin período individual.

La instrumentalidad del espacio puede modificarse cuando todos sus cursos están eliminados lógicamente. Los cursos no eliminados, aunque estén inactivos, mantienen el bloqueo. Los cursos históricos conservan su instrumento y solo pueden restaurarse si este coincide con la instrumentalidad actual del espacio. Esta regla se valida en la aplicación y en PostgreSQL mediante `20260920040607__allow_instrumental_changes_after_course_deletion.sql`. El formato individual o grupal conserva su restricción mientras existan cursos, incluidos los eliminados, porque determina la estructura de sus horarios históricos.

## Resolución y estados

| Entidad | Transición | Responsable y validaciones |
| --- | --- | --- |
| Principal | DRAFT → SUBMITTED | Titular; datos requeridos, cursos del trayecto/ciclo/institución, período abierto y ausencia de solicitudes o cursadas duplicadas propias. |
| Principal | DRAFT → CANCELLED | Titular; cancela también las hijas pendientes. |
| Principal | SUBMITTED → PROVISIONALLY_APPROVED | Permiso de aprobación; requisitos documentales obligatorios iniciales aceptados, con documentación pendiente de entrega posterior. |
| Principal | PROVISIONALLY_APPROVED → APPROVED | Permiso de aprobación; confirmación definitiva tras aceptar todos los requisitos obligatorios pendientes. |
| Principal | SUBMITTED → APPROVED | Permiso de aprobación; validación documental completa. No crea cursadas directamente. Evalúa cada hija y envía a espera las que no tienen cupo. |
| Principal | SUBMITTED → REJECTED | Permiso de rechazo; requiere motivo y resuelve las hijas pendientes. |
| Hija | PENDING → WAITLISTED | Al aprobar la principal sin cupos o al agotarlos por otra incorporación; asigna número único por curso. |
| Hija | PENDING/WAITLISTED → ENROLLED | Permiso de inscripción; principal aprobada, curso activo, clase del curso, días de esa clase, períodos precalculados correspondientes para individuales, cupos y ausencia de superposición/duplicación. |
| Hija | PENDING/WAITLISTED → REJECTED | Personal autorizado con motivo, rechazo principal o cierre del curso. |
| Hija | PENDING/WAITLISTED → CANCELLED | Cancelación del borrador principal. |
| Cursada | Creación → ENROLLED / IN_PROGRESS | Aceptación efectiva de la hija o alta manual autorizada; crea estudiante cuando corresponde, asignaciones y auditoría. |
| Cursada | ENROLLED → WITHDRAWN/ADMINISTRATIVELY_WITHDRAWN | Permiso de baja, motivo y versión vigente; libera horarios sin incorporar a nadie automáticamente. |
| Cursada | ENROLLED → COMPLETED | Cierre del curso o del ciclo; libera horarios. IN_PROGRESS pasa a PENDING_RESULT; conserva resultados ya registrados. |
| Estado académico | IN_PROGRESS, PENDING_RESULT, REGULARIZED, PROMOTED, PASSED, FAILED, NOT_APPLICABLE | Actualización administrativa con motivo y versión. Las bajas no admiten cambios; una cursada finalizada no vuelve a IN_PROGRESS. |

Los estados terminales de las hijas no pueden reabrirse. Repetir la aceptación de una hija ya incorporada devuelve su misma cursada. El número de espera se conserva como dato histórico. Liberar un cupo mantiene la hija en WAITLISTED hasta una confirmación explícita con clase y horarios.

Las solicitudes antiguas basadas en planes/espacios se conservan para consulta; no se permite iniciar solicitudes nuevas por plan ni enviar una solicitud con espacios sin cursos concretos.

## Acceso

- El estudiante consulta cursadas filtradas por su persona e institución.
- El profesor usa una vista propia, derivada exclusivamente de sus asignaciones de clase. El rol docente no concede consulta administrativa global.
- La administración conserva sus permisos institucionales para consultar, incorporar, dar de baja y actualizar resultados.
- La lista de espera se abre desde la acción contextual del curso. Incorporar usa el mismo diálogo y operación de aceptación de hijas, con revalidación de cupos.

Los controladores y payloads Java son la referencia del contrato HTTP; no se mantiene una especificación paralela en este documento. `TeacherCourseController`, `CourseEnrollmentController` e `InstitutionalEnrollmentApplicationController` separan las tres fronteras de acceso.

## Concurrencia y migración

Las mutaciones de inscripción toman primero un bloqueo de la institución, dentro de la misma transacción. Aceptar, cancelar, rechazar, crear cursos, cerrar y reemplazar clases usan ese orden. Esto serializa incorporaciones concurrentes de una institución; favorece consistencia frente a paralelismo dentro del mismo establecimiento.

`20260920021420__harden_course_enrollment_integrity.sql` incorpora la relación directa entre cursada e hija, unicidad, claves foráneas compuestas y validaciones de contexto/capacidad en PostgreSQL. Completa los períodos precalculados de horarios existentes, cancela hijas pendientes de principales canceladas y regulariza cursadas que quedaron activas en cursos cerrados. Conserva el historial y detiene la migración ante datos incompatibles con las restricciones; no borra esos registros silenciosamente.

No se reemplazan clases que ya tienen cursadas, incluidas históricas. En cursos todavía sin cursadas, se eliminan primero los períodos y luego se reconstruyen clases y horarios.

La migración debe aplicarse con el procedimiento normal de despliegue y respaldo del entorno. Las pruebas usan bases efímeras: ejecutarlas no aplica esta migración a la base persistente de desarrollo ni publica cambios.

## Verificación

`CourseEnrollmentFlowPostgresIntegrationTest` recorre envío, aprobación, aceptación, bajas, espera, cierre y consultas con servicios reales, Flyway y PostgreSQL. Incluye último cupo concurrente grupal, exclusión del período individual, numeración concurrente de espera, rechazo de relaciones inválidas en base, reemplazo de clases y autorización HTTP docente.

Las pruebas frontend de `EnrollmentWizard`, `EnrollmentCoursesSelector`, validación/asignación horaria y navegación cubren selección múltiple, advertencias, autoguardado sin perder cursos fuera del catálogo cargado, períodos ocupados y separación de accesos.

No sustituyen una comprobación visual con usuarios reales. Los resultados de las suites y cualquier limitación del entorno se informan junto con la entrega.

### Resultado de esta implementación

- API: suite rápida completa (722 pruebas) y suite de integración completa (85 pruebas), sin fallos en las ejecuciones finales correspondientes. La última modificación del bloqueo de creación se comprobó además con las pruebas enfocadas de creación y cierre.
- Frontend: suite completa (739 pruebas), seguida de 119 pruebas enfocadas después de incorporar los últimos cambios y la cobertura de lista de espera. TypeScript y ESLint sin errores.
- La primera ejecución general de integración tuvo una colisión aleatoria en el `iso_code` de una fixture de `EmailVerificationPostgresIntegrationTest`; la repetición completa pasó. No se modificó el comportamiento de autenticación por ese fallo de datos de prueba.
- Sin revisión visual autenticada en navegador ni aplicación de la migración en una base persistente. Sin commit, push ni despliegue.

## Alcance adicional confirmado

El proyecto conserva los nombres de niveles derivados de su orden (`Nivel <orden>`) y la administración de turnos desde el panel de plataforma. Son requisitos confirmados junto con el flujo de inscripción; no se retiran como parte de su revisión. La migración histórica `20260919182215__rename_academic_levels_to_derived_names.sql` normaliza nombres existentes y se conserva sin reescribir su historial. Su comentario original «dev scope» no constituye una restricción ejecutable del perfil.

## Correcciones de la revisión

La generación de períodos individuales usa la duración exacta del horario, sin truncar segundos ni fracciones, y una cantidad finita de bloques. Cada bloque debe caber dentro del horario. La migración `20260920033712__enforce_individual_slot_boundaries.sql` valida también límites, duración y alineación de los períodos en PostgreSQL. Si existen períodos históricos incompatibles, detiene la migración para que se revisen; no elimina períodos ni asignaciones automáticamente.

Las acciones de rechazo, baja y resultado académico validan el tipo del motivo. El formulario de espacios envía siempre un valor explícito para la condición instrumental. El alta manual conserva la URL de origen, incluidos filtros y paginación, y valida el destino tanto al abrir el formulario como al completar la acción en el servidor.

La migración `20260920032504__validate_individual_slot_boundaries.sql` permanece vacía porque el observador de desarrollo la aplicó antes de que se completara su contenido. La validación está en `20260920033712__enforce_individual_slot_boundaries.sql`; se conserva el historial original sin ejecutar Flyway repair.
