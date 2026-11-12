# Exportación de solicitudes en PDF

La API ya no genera PDFs. Entrega los datos de la planilla y el navegador del
usuario arma el documento con `@react-pdf/renderer` (ver `bemol-ui`,
`src/features/enrollment-pdf`). No se instala Chromium, no se usan temporales
y no se almacena nada: cada exportación se arma en el momento.

## Contrato

Todas las rutas responden `EnrollmentPdfData` en JSON:

| Ruta | Uso |
| --- | --- |
| `GET /institutions/{institutionId}/enrollment-applications/{applicationId}/pdf` | Exportación institucional |
| `GET /admin/enrollment-applications/{institutionId}/{applicationId}/pdf` | Exportación de plataforma |
| `GET …/self-service/{personId}/enrollment-applications/{applicationId}/pdf` | Exportación propia o de un representado |
| `POST …/enrollment-pdf-settings/preview` | Ejemplo con la configuración sin guardar |
| `POST …/enrollment-forms/preview-pdf` | Ejemplo con un formulario sin publicar |

Los permisos y estados exportables no cambian: solo se exportan solicitudes
presentadas, aprobadas provisoriamente, aprobadas o rechazadas.

`institutionId` y `logoUrl` permiten al cliente pedir el logo por la ruta pública
del frontend (`/api/public/institutions/{id}/logo?v=…&format=png`). La API no
firma URLs de S3 para la exportación. Las fechas viajan como instantes ISO-8601;
el cliente las muestra como `dd/MM/yyyy HH:mm` en `America/Argentina/Cordoba`.
Los valores faltantes llegan como «Sin informar».

## Diseño de la planilla

El diseño se conserva en el frontend, no en la API. Es un formulario
institucional A4 con marco oscuro completo, cerrado en cada salto de página.
El título principal lleva una banda oscura y las secciones usan franjas grises
de borde a borde. Manrope (400, 500 y 700) se sirve desde
`bemol-ui/public/fonts/manrope`, con su licencia OFL.

El logo se ajusta sin deformarse a la izquierda de la cabecera. El nombre y el
contacto van centrados, y las fechas a la derecha; la fecha de presentación solo
aparece cuando existe. Cada página muestra «Página N / M». Las secciones no se
cortan entre páginas, salvo las que habilitan `allowPageBreaks`. La última fila
de la tabla académica comparte bloque con las firmas, para que las firmas nunca
queden solas en una hoja. Los valores «Sin informar» se muestran en gris; Manrope
no tiene cursiva, por lo que no se inclinan.
