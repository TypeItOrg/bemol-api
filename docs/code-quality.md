# Legibilidad y tamaño del código

## Responsabilidades

- Los controllers conservan HTTP y autorización por método; no coordinan persistencia.
- Las entidades conservan sus invariantes, cálculos y transiciones propias.
- Los casos de uso coordinan autorización, locks, repositorios y efectos externos dentro de una transacción explícita.
- Los colaboradores que requieren esa transacción usan `Propagation.MANDATORY`; no abren transacciones independientes para completar un paso del mismo flujo.
- Los factories/mappers de respuestas concentran el mapeo. No cargan documentos ni exponen rutas de storage sin pasar por la autorización correspondiente.
- Las fachadas existentes conservan las firmas usadas por controllers y otros casos de uso. Una extracción no justifica cambiar los contratos HTTP, el orden de locks o las reglas de negocio.

Separar validación, preparación, I/O y retorno con espacios y nombres claros. Usar llaves en todas las guardas y bucles. Extraer un método o colaborador cuando represente una responsabilidad reconocible, no para repartir arbitrariamente un método entre archivos.

## Presupuesto de tamaño

```bash
./gradlew codeStructureCheck
```

La tarea verifica todos los archivos Java de **producción**: como máximo 250 líneas físicas después de aplicar Spotless, incluyendo imports, anotaciones y líneas vacías. Se integra en `check` y en el job de análisis estático de CI; no ejecuta tests. Genera `build/reports/code-structure.txt`.

Las únicas excepciones están identificadas y tienen un techo congelado en [`gradle/code-structure.gradle`](../gradle/code-structure.gradle):

| Archivo | Techo | Motivo |
| --- | ---: | --- |
| `authorization/enums/PermissionCode.java` | 574 | Catálogo único de permisos y sus reglas exhaustivas; dividir el enum cambiaría su identidad y complicaría la autorización. |
| `enrollment/entities/EnrollmentApplication.java` | 448 | Mapeo JPA y comportamiento intrínseco de un mismo agregado; trasladar transiciones a servicios debilitaría la entidad. |

No son permisos para seguir agrandando esas clases. La tarea rechaza excepciones que ya no son necesarias o apuntan a archivos eliminados. Una excepción nueva requiere justificar cohesión y compatibilidad, no sólo que una clase haya superado el límite.

Los archivos de tests no se fragmentan para satisfacer esta métrica: deben organizarse por escenario y mantener la cobertura de coordinación y persistencia. El tamaño es una señal para revisar responsabilidades, no una garantía de buen diseño.

## Verificaciones independientes de tests

```bash
./gradlew compileJava compileTestJava
./gradlew spotlessCheck codeStructureCheck
./gradlew staticAnalysis
```

Compilan la aplicación y los tests existentes, verifican formato, tamaño, contratos JSpecify y advertencias del compilador; no ejecutan suites. `staticAnalysis` utiliza los toolchains declarados en `gradle/static-analysis.gradle`.

Para Java modificado, aplicar el formatter sólo al alcance trabajado:

```bash
./gradlew spotlessApply -PspotlessIdeHook="/ruta/absoluta/Archivo.java,/ruta/absoluta/Otro.java"
```

Luego revisar el diff y ejecutar `spotlessCheck`. La validación funcional y PostgreSQL siguen requiriendo las suites correspondientes cuando su ejecución esté dentro del alcance autorizado.
