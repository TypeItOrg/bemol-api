# ÍNDICE DE AUTENTICACIÓN Y AUTORIZACIÓN

Esta carpeta contiene la documentación de los subsistemas de autenticación y autorización del proyecto bemol-api. Está pensada para que el equipo entienda cómo funciona cada pieza, por qué se tomaron ciertas decisiones y qué casos particulares existen.

## Documentos

| Documento | Qué cubre |
|---|---|
| [`MODELO-DE-AUTENTICACION.md`](MODELO-DE-AUTENTICACION.md) | Los dos modelos de autenticación (institucional y plataforma), entidades involucradas y cómo Spring los distingue |
| [`LOGIN-Y-JWT.md`](LOGIN-Y-JWT.md) | Flujo de login para ambos modelos, contenido del JWT, validación en cada request y principales autenticados |
| [`REFRESH-Y-LOGOUT.md`](REFRESH-Y-LOGOUT.md) | Rotación de refresh tokens, detección de reutilización, blacklist en Redis y cierre de sesión |
| [`AUTORIZACION-Y-PERMISOS.md`](AUTORIZACION-Y-PERMISOS.md) | Cómo se resuelven permisos dinámicamente, anotaciones custom, aspectos y diferencias entre roles y permisos |
| [`CASOS-PARTICULARES.md`](CASOS-PARTICULARES.md) | Decisiones de diseño no obvias, edge cases, bootstrap de admin y protecciones |
| [`ESTADO-DE-SEGURIDAD.md`](ESTADO-DE-SEGURIDAD.md) | Estado implementado, invariantes de seguridad, aislamiento institucional, concurrencia y cobertura de pruebas |

## Orden recomendado de lectura

1. `MODELO-DE-AUTENTICACION.md` — empezar por los tipos de usuario
2. `LOGIN-Y-JWT.md` — cómo se autentican
3. `REFRESH-Y-LOGOUT.md` — cómo se mantiene la sesión
4. `AUTORIZACION-Y-PERMISOS.md` — cómo se autorizan las acciones
5. `CASOS-PARTICULARES.md` — decisiones y escenarios especiales
6. `ESTADO-DE-SEGURIDAD.md` — garantías actuales y puntos a considerar al extender el sistema

## Documentación relacionada

- [`docs/AUTENTICACION.md`](../AUTENTICACION.md) — visión general del subsistema de autenticación y autorización
- [`docs/ARQUITECTURA.md`](../ARQUITECTURA.md) — arquitectura general del sistema
- [`AGENTS.md`](../../AGENTS.md) — convenciones del proyecto para desarrollo con asistentes de IA
