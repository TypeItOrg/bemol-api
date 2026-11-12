# Auditoría: definición y flujo

Cada entidad define qué expone; el caso de uso identifica la operación y conserva la transacción; el recorder compara y registra; el frontend presenta. No hay reflexión, captura automática de JPA ni catálogo global manual de traducciones.

## Definir una entidad

Las entidades auditables implementan `AuditSubject`:

```java
@Override
public AuditSnapshot.Snapshot auditSnapshot() {
  return AuditSnapshot.fields()
      .expose("name", "Nombre", name)
      .compare("description", "Descripción", description)
      .expose("active", "Estado", active)
      .build();
}
```

- `expose` permite persistir anterior y nuevo; `compare` sólo detecta el cambio. Un campo omitido no se audita.
- Las etiquetas se declaran junto al campo. Las claves técnicas permanecen estables.
- Una referencia puede comparar su ID y mostrar otro valor mediante la sobrecarga de `expose`. No se serializan entidades completas.
- Los códigos visibles llevan `{code, label}`; las etiquetas de sus valores pertenecen al dominio productor.
- `AuditSnapshot.date` y `dateTime` producen `{type: "DATE" | "DATETIME", value}`. La comparación puede conservar el valor ISO normalizado.
- Los grupos usan `labeledValues()` para publicar valores y etiquetas seguras. Las etiquetas de campos privados no se incluyen en el grupo visible.
- Los snapshots copian mapas y colecciones anidadas. Los conjuntos se ordenan por identidades o claves funcionales estables, no por IDs auxiliares regenerados.

Los datos privados sólo se comparan en memoria: no forman parte de persistencia, HTTP, búsqueda ni logs. `Snapshot.toString()` no imprime valores. Las contraseñas se capturan por separado del perfil; credenciales WebAuthn, handles y tokens no se incorporan a los snapshots.

## Registrar la operación

El caso de uso captura el snapshot anterior, modifica el estado y entrega el posterior a `recordSnapshotChange`. La entidad no accede a repositorios, sesión, notificaciones ni otras coordinaciones.

El recorder agrega actor real, identidad activa, representación, institución, fecha, origen y petición. Las modificaciones sin diferencias efectivas no generan eventos. Un rollback no deja un éxito auditado. Los eventos semánticos, como envío de una solicitud, publicación de formulario o resultado de correo, pueden registrarse sin un diff.

Las acciones y módulos se declaran en sus dominios: `academic/audit`, `auth/audit`, `authorization/audit`, `institutional/audit`, `enrollment/audit` y `common/mail`. Pequeños proveedores Spring los aportan a `AuditOperations`, que agrega y valida unicidad de códigos y coherencia de módulos. Las opciones están disponibles aunque no existan eventos.

Cada operación declara también dónde se ve: su `AuditFeed` (`ACTIVITY` por defecto, `EMAIL` para correo) y si es `platformOnly` (por ejemplo, las cuentas de administradores de plataforma). Las consultas reciben una `AuditView` (feed y audiencia de plataforma o institución) y preguntan al catálogo qué acciones y módulos son visibles; no hay códigos de módulo ni prefijos de acción en servicios o consultas. En plataforma, el feed se deriva del módulo filtrado; en la institución, del endpoint (`/activity/audit-events` o `/activity/emails`). Un filtro por acción o módulo no visible en la vista es inválido. Las acciones históricas sin definición vigente siguen apareciendo en el feed de actividad.

Cada entidad define su `AUDIT_TYPE`; correo aporta su recurso desde el dominio de envío. Persistencia y HTTP conservan `action` y `entityType` como códigos de texto, sin enums globales.

## Persistencia y consultas

Se conservan tabla y columnas. `details` contiene `entityName`, `changedFields`, `before`, `after`, `fieldLabels` y el contexto seguro que necesite la operación. No hay marcador de versión de formato, migración, backfill ni borrado de eventos.

Las consultas están en `audit/interfaces`, el mapeo HTTP en `audit/payloads` y la coordinación real en servicios. Se conservan autorización, aislamiento institucional, filtros, búsquedas combinadas, nombres actuales, orden estable y paginación. Los joins para nombres actuales siguen siendo necesarios. Las etiquetas de tipos se pasan como parámetros a las opciones SQL para buscar, contar y ordenar antes de paginar, sin un `CASE` de traducciones.

Las respuestas aportan `actionLabel`, `entityLabel`, `module` y `moduleLabel`. Los selectores consultan opciones paginadas del backend, no catálogos completos en el navegador.

## Presentación y datos anteriores

El frontend muestra únicamente los campos declarados con etiquetas, también en grupos anidados. Formatea valores simples, fechas y códigos con etiqueta sin ramas por entidad. Distingue valores privados no registrados, `null`, ausencia de estado anterior y listas vacías.

Se retiraron los catálogos manuales de atributos/valores, los renderizadores específicos de roles/permisos/requisitos y el consumo de `history`. También se retiró `/activity/audit-events/labels`, utilizado exclusivamente por auditoría; las definiciones y endpoints propios de autorización permanecen.

Las filas anteriores siguen almacenadas, listadas y accesibles. El API conserva sus detalles originales; las vistas muestran los metadatos generales, pero no reconstruyen detalles sin etiquetas ni inventan traducciones mediante un catálogo histórico. No se excluyen eventos por antigüedad.

Los historiales funcionales de cursadas, admisión, documentación y accesos son independientes y no se eliminan. Correo conserva su presentación y restricciones de acceso propias; su resultado se persiste en una transacción independiente después de SMTP. Aceptación SMTP no equivale a entrega al destinatario.

## Incorporar un campo o entidad

1. Implementar o ampliar el snapshot en su entidad, con comparación, exposición y etiquetas explícitas.
2. Capturarlo desde la operación existente y registrar su acción semántica dentro de la transacción.
3. Aportar recurso/operaciones mediante el proveedor de su dominio cuando sean nuevos.
4. Comprobar privacidad, referencias, independencia, colecciones y no-op; conservar las regresiones del dominio.

Agregar un campo del vocabulario existente requiere cambiar la entidad y su comprobación, no un catálogo central ni el frontend. La matriz y los escenarios de control están en `AUDITORIA-COBERTURA.md`. La aceptación visual y la verificación de almacenamiento externo real se acreditan por separado de las pruebas automatizadas.
