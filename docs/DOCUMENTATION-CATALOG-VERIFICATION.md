# Verificación local del catálogo documental

Fecha: 2 de octubre de 2026. Implementación local de API e interfaz, sin commits, publicación ni despliegue.

## Alcance y preservación

- Se respaldó la base de desarrollo con `pg_dump` y se restauró en PostgreSQL 18 aislado. La base original no recibió las nuevas migraciones ni los datos de verificación y quedó nuevamente detenida.
- La API utilizó el puerto 8088; la interfaz, 3008; PostgreSQL, 55432; Redis, 56379. Archivos, logs y credenciales temporales se mantuvieron separados del almacenamiento de desarrollo.
- Otras copias de la base se enriquecieron con requisitos, snapshots, adjuntos y auditoría anteriores al catálogo para comprobar ambas migraciones conservadoras. El pase final aplicó sus versiones definitivas mediante Flyway, inició correctamente la API y comprobó la descarga de un archivo histórico con contenido idéntico.
- Las modificaciones anteriores de autenticación, acceso público institucional y QA se conservaron. No se crearon ni modificaron tests de esta entrega y no se ejecutaron suites automatizadas.

## Checks estáticos

| Área | Comprobación | Resultado |
|---|---|---|
| API | `compileJava` | Correcto |
| API | Spotless acotado a los archivos Java de la entrega y `spotlessCheck` completo | Correcto |
| API | `staticAnalysis` | Correcto; compila fuentes de tests, pero no ejecuta tests |
| UI | TypeScript completo, `tsc --noEmit` | Correcto |
| UI | ESLint sobre archivos de la entrega, sin warnings | Correcto |
| UI | Prettier sobre archivos de la entrega | Correcto |
| UI | Detector Impeccable sobre componentes cambiados | Sin hallazgos |
| Ambos | `git diff --check` y revisión de cambios propios | Sin errores de whitespace |

## Evidencia manual por escenario del plan

| ID | Escenario | Evidencia |
|---|---|---|
| M01 | Definición compartida, exigencias distintas | Un documento asignado a CAV con `AT_SUBMISSION` y a CAVA con `BEFORE_CONFIRMATION`; condiciones específicas separadas e impacto visible. |
| M02 | Actualizar borradores sin perder entregas | PDF cargado; cambio a PNG conserva requisito y adjunto e informa `needsReplacement`. El retiro opcional funciona y conserva su versión en el historial. |
| M03 | Retirar y reactivar | El requisito recupera el mismo UUID. Retirado rechaza nuevas cargas con HTTP 400 y permite consultar historial con HTTP 200. |
| M04 | No modificar enviadas | Reactivar y editar el catálogo no reactiva ni reescribe snapshots enviados. La copia migrada conserva nombre, instrucciones, nivel, formatos y orden históricos. |
| M05 | Conflictos concurrentes | Dos ediciones simultáneas con la misma revisión: una HTTP 200 y otra 409, tanto para definición como para asignación. |
| M06 | Edición simultánea al envío | Dos peticiones esperando el bloqueo institucional. La asignación cambia de opcional a obligatoria; el envío posterior devuelve 400 identificando el documento nuevo, no envía con condiciones antiguas. |
| M07 | Migración conservadora | Dos coincidencias exactas se unifican; tres entradas ambiguas quedan separadas. Comparaciones antes/después de snapshots enviados y referencias de adjuntos sin diferencias; auditoría preservada y revisiones históricas nulas. Borradores reconciliados. |
| M08 | Adicionales y aprobación | Cargar y aceptar el primero no exige completar los demás. Faltantes `AT_SUBMISSION` bloquean admisión provisoria; `BEFORE_CONFIRMATION` bloquea confirmación. Con obligatorios aceptados, la confirmación funciona con opcionales pendientes. Un pedido en provisional no altera IDs de matrículas. |
| M09 | Duplicados y estados cerrados | Duplicado HTTP 409 con ID del requisito existente; un lote con documento nuevo y duplicado no deja cabecera ni requisitos parciales. Pedidos rechazados en borrador, rechazada, cancelada y aprobada. Pedido y cargas admitidos en enviada con período cerrado. |
| M10 | Permisos y asociaciones | Actor restringido a un trayecto: selección contextual HTTP 200, catálogo general y otro trayecto denegados; creación de catálogo HTTP 403. Lote mixto denegado sin cambios de revisiones/orden. Referencias extranjeras rechazadas en API y por FK institucional; FK de adjuntos impide referenciar un requisito de otra inscripción; el trigger de identidad impide cambiar de documento una asignación existente. |
| M11 | Interfaz y solicitante | Catálogo de plataforma e institucional; guardado compartido con impacto; pedido desde detalle con motivo/responsable; solicitante sin acción administrativa; paso documental e historial. Creación inline conserva ID al cancelar el trayecto, sin asignarlo. Diálogo de pedido permanece abierto al presionar Escape mientras está pendiente y termina al liberarse el bloqueo. Vista de escritorio de 1280 px y paso documental móvil de 390 px, sin desborde horizontal observado. |

### Correcciones encontradas durante la verificación

- La persistencia de pedidos nuevos usa `flush` de la inscripción gestionada, evitando devolver un identificador nulo por el merge de hijos nuevos.
- Los requisitos retirados se excluyen de los gates y capacidades de aprobación. Una inscripción enviada con solo requisitos retirados se aprobó correctamente.
- Si no queda ningún requisito vigente, desaparece el paso documental, pero el detalle del borrador conserva una sección de historial retirado.
- Los selectores dentro de diálogos usan identificadores independientes del formulario principal para mantener sus etiquetas accesibles.
- La creación inline conserva la definición confirmada; los resultados no confirmables bloquean reintentos de creación hasta recargar.

## Límites de esta evidencia

Esta no es una certificación exhaustiva de todos los escenarios y combinaciones:

- No se inyectaron todos los fallos de transporte ni respuestas de creación inciertas para verificar su recuperación en navegador.
- No se reprodujo el guardado parcial de varios requisitos del trayecto con un fallo intermedio y posterior reintento; se revisó la conservación de IDs y revisiones en el código.
- No se verificó volumen de más de 100 borradores ni catálogos/asignaciones con varias páginas reales.
- No se recorrieron todas las combinaciones de roles, dispositivos y navegadores ni se realizó una auditoría completa con lector de pantalla.
- No hay evidencia de staging/producción ni ejecución de tests automatizados.

Los respaldos, logs de comprobaciones y capturas se conservaron fuera de los repositorios en un directorio privado local de verificación documental.
