# Piloto de historial con Hibernate Envers

> Este documento describe la prueba original. El alcance ampliado, sus campos,
> migración y exclusiones están en [Cobertura actual de Envers](ENVERS-COBERTURA.md).

## Alcance y activación

Prueba de backend para `Shift` y `Course`, incluyendo `CourseClass`,
`CourseClassTeacher`, `CourseClassDay` y `CourseClassSchedule`. No reemplaza la
Auditoría funcional, no cambia sus eventos ni incorpora endpoints o pantallas.
El reemplazo de clases ahora usa borrados por entidad para conservar sus bajas
históricas, sin cambiar los eventos de la Auditoría funcional.

La dependencia usa la versión administrada por Spring Boot 4.0.6:
Hibernate ORM y Envers **7.2.12.Final**, sin actualizar el framework.

En la prueba original estaba **desactivado por defecto**, tanto en la aplicación
como en los tests. La configuración vigente está en [Cobertura actual](ENVERS-COBERTURA.md):

```properties
spring.jpa.properties.hibernate.integration.envers.enabled=false
```

En esa etapa, sólo el test del piloto lo activaba mediante `@TestPropertySource`.
La activación posterior no modifica perfiles ni Compose.

La migración aditiva
`src/main/resources/db/migration/20261018200200__add_envers_pilot_history.sql`
crea siete tablas con prefijo `envers_poc_`: revisiones y seis historiales. No
reescribe migraciones aplicadas, no modifica datos existentes ni hace backfill.
Las tablas se crean aunque el registro de revisiones esté desactivado.

## Datos conservados

- Turnos: nombre, estado, institución y fecha de eliminación lógica.
- Cursos: estado, eliminación lógica y referencias a institución, espacio del
  plan, instrumento y ciclo lectivo.
- Clases: número, docente por identificador, días, cupo, duración y horarios.
- Revisión: fecha, tipo de actor, identificador de la persona o cuenta de
  plataforma y correlación con la petición, cuando están disponibles.

Los campos están seleccionados explícitamente; no se audita toda la entidad.
Quedan fuera descripciones privadas, datos personales de docentes, credenciales
y timestamps de mantenimiento. Las relaciones a entidades no auditadas guardan
su referencia, **no una copia histórica de sus nombres o datos**. Un curso no
tiene nombre propio: el nombre del espacio académico queda fuera de este piloto.

El historial se escribe en la misma transacción: un rollback tampoco deja una
revisión confirmada. Es historial técnico por entidad, no un evento de negocio.

### Reemplazo de clases

`ReplaceCourseClassesUseCase` conserva autorización, bloqueo institucional y del
curso, y rechazo de cursos cerrados o con inscripciones históricas. Primero
elimina los turnos individuales precalculados (fuera del alcance Envers). Luego
borra horarios, docentes, días y clases, en ese orden de dependencias, mediante
los métodos `deleteBy…` derivados de Spring Data JPA: cargan las entidades y
ejecutan sus callbacks, en lugar de enviar un `DELETE` JPQL masivo.

Un `flush` antes de crear el nuevo árbol libera la unicidad del número de clase
dentro del curso. Bajas, altas, nuevos turnos precalculados y Auditoría funcional
pertenecen a la misma transacción. La corrección no necesita otra migración ni
repara retroactivamente revisiones incompletas generadas por borrados masivos.

## Resultados comprobados

| Escenario | Resultado |
| --- | --- |
| Crear y renombrar un turno | Se recuperan el nombre anterior y el nuevo, con actor y petición. |
| Repetir un cambio de turno sin diferencias | No genera otra revisión. |
| Cambiar solo su descripción privada | No genera revisión Envers; la Auditoría funcional sigue funcionando. |
| Desactivar, eliminar lógicamente y restaurar | Se conservan los estados y la fecha de eliminación. |
| Modificar una entidad administrada sin `repository.save` | Se captura mediante dirty checking. |
| Cancelar una transacción después de un flush | No persisten cambios ni historial. |
| Modificar un turno que existía antes del piloto | La primera revisión contiene el estado posterior; no reconstruye el anterior. |
| Crear y reemplazar clases de un curso | Se guardan los nuevos docentes y horarios exactos. |
| Reemplazar clases mediante el caso de uso real | Se capturan las bajas de clases, docentes, días y horarios junto con las altas del nuevo árbol; las consultas históricas no conservan clases fantasma. |
| Cancelar un reemplazo después de un flush | Se conserva el árbol anterior y no quedan nuevas revisiones ni eventos de Auditoría funcional. |
| Reemplazar clases por los mismos valores | Hay nuevas revisiones técnicas porque se recrean sus identificadores, aunque el cambio sea semánticamente nulo. |
| Cambiar hijos de un curso | No implica una revisión del curso raíz; hay que consultar el historial de los hijos. |
| Consultar la baja de un hijo reemplazado | Conserva el docente, día, cupo y horarios anteriores; las revisiones previas siguen reconstruyendo el árbol original. |
| Arrancar desactivado y migrar una base con datos previos | La Auditoría funcional sigue registrando; datos e historial Flyway previos se preservan y las siete tablas nuevas quedan vacías. |

El límite de los borrados masivos está documentado por
[Hibernate Envers](https://docs.hibernate.org/orm/7.2/userguide/html_single/#envers).
Por eso el reemplazo ya no utiliza consultas masivas sobre las cuatro entidades
auditadas de clases. El historial completo del árbol se consulta a través de
esas entidades: no se agrega una copia de todos los hijos a la revisión raíz.

### Costo observado

Para un curso con una clase, un docente, un día y un horario:

- Creación: cinco filas históricas y una fila de revisión.
- Reemplazo: cuatro bajas y cuatro altas históricas, y una fila de revisión.

Estas escrituras se suman a la Auditoría funcional. No se midió latencia, carga
concurrente ni crecimiento del almacenamiento; los conteos no son un benchmark.

## Verificación

Se agregaron **tres tests de integración**, sin tests unitarios nuevos:

- `EnversPilotPostgresIntegrationTest`: historial y privacidad de turnos;
  historial completo del reemplazo de clases, rollback y convivencia con la
  Auditoría funcional, incluyendo reemplazos semánticamente nulos.
- `EnversPilotSafetyPostgresIntegrationTest`: desactivación explícita y
  migración sobre una base con datos e historial previos.

Usan bases PostgreSQL aisladas para el piloto, con Flyway y los casos de uso
reales, sin agregar datos a las bases de otras suites. El arranque con validación
de esquema comprueba también una instalación nueva con Envers activado.

```sh
./gradlew integrationTest --tests '*EnversPilot*PostgresIntegrationTest'
```

Para la corrección de clases se reutilizaron esos tres tests del piloto, sin
agregar métodos de test ni unitarios. También pasaron los 15 tests existentes de
`CourseEnrollmentFlowPostgresIntegrationTest`, incluyendo reconstrucción de
turnos individuales y preservación ante reemplazos rechazados.

Validación inicial del piloto, antes de ampliarlo: los tres tests y la regresión completa de **983
tests** pasaron. También pasaron `spotlessCheck`, `codeStructureCheck`,
`nullAwayMain`, `nullAwayTest`, `ecjMain` y `ecjTest`. El test existente de esquema
se ajustó de 70 a 77 tablas por las siete tablas nuevas del piloto.

## Conclusión

Envers sirve para recuperar valores históricos sin escribir manualmente cada
comparación, pero **no es un reemplazo directo de la Auditoría actual**.

Los borrados masivos del reemplazo de clases están resueltos. Antes de ampliarlo
o activarlo fuera de una prueba hay que resolver el ruido del reemplazo de
clases, el estado inicial de datos existentes y la API de consulta de un agregado
completo. Una futura API también necesitaría
autorización por institución y selección segura de campos. Después corresponde
medir rendimiento y almacenamiento.

Por ahora se conserva la Auditoría funcional como referencia; este piloto queda
aislado, desactivado y sin cambios en frontend.
