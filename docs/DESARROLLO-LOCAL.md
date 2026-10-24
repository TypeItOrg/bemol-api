# DESARROLLO LOCAL Y ENTORNOS

Esta guía describe cómo configurar, ejecutar y operar el entorno de desarrollo local para el backend de Bemol.

## 1. Responsabilidades y Alcance

- **Este repositorio (`bemol-api`)**: Es dueño del código de la aplicación, el `Dockerfile`, la configuración de desarrollo local en `compose.yaml` y las migraciones Flyway.
- **Repositorio de infraestructura (`bemol-infra`)**: Administra la topología compartida, Nginx, certificados TLS, despliegues y procedimientos de rollback para ambientes de staging y producción.
- **Aislamiento**: `compose.yaml` se utiliza **exclusivamente** para desarrollo local. No deben agregarse configuraciones de staging ni producción a este archivo.

## 2. Requisitos Previos

- **Java 21 LTS** (Temurin recomendado).
- **Docker** y **Docker Compose v2** instalados y activos.
- **GNU Make**.
- Git.

## 3. Inicio Rápido

### Paso 1: Configurar variables de entorno

Copiar la plantilla de variables de entorno de desarrollo:

```bash
test -e .env.dev || cp .env.dev.example .env.dev
```

El archivo `.env.dev` contiene valores predefinidos listos para desarrollo local (conexiones a contenedores Docker, claves dummy de JWT y configuración de correo apuntada a Mailpit).

### Paso 2: Iniciar el entorno con Make

El proyecto Compose y sus recursos locales se llaman `bemol-api`. Los volúmenes
se crean automáticamente para una instalación nueva. El usuario autorizó recrear
los datos descartables de pruebas; esto no es un procedimiento de migración para
un entorno con datos que conservar. No reutilizar variables privadas con nombres
de base/usuario anteriores.

```bash
make dev
```

Este comando levanta la pila completa en segundo plano utilizando `compose.yaml`.

### Paso 3: Puertos y servicios disponibles

Una vez iniciado el entorno, los siguientes servicios quedan disponibles en `localhost`:

| Servicio | Puerto Local | Descripción |
|---|---|---|
| **Bemol API** | `8080` | Endpoint HTTP de la aplicación (`http://localhost:8080`). |
| **PostgreSQL** | `5432` | Base de datos relacional (`bemol_db`, usuario `bemol`, clave `bemol`). |
| **Redis** | `6379` | Almacenamiento en memoria para tokens, sesiones y rate limiting. |
| **Mailpit (Web)** | `8025` | Interfaz web para inspeccionar correos salientes (`http://localhost:8025`). |
| **Mailpit (SMTP)** | `1025` | Servidor SMTP simulado sin envío real a internet. |

## 4. Topología de Servicios en Desarrollo

El archivo `compose.yaml` orquesta cuatro servicios interconectados a través de la red `bemol-api-network-dev`:

1. **`dev` (`bemol-api-dev`)**:
   - Contenedor que ejecuta la aplicación Spring Boot mediante el target `dev` del Dockerfile.
   - Monta el volumen de caché de Gradle (`gradle-cache`) y el volumen persistente de almacenamiento local (`bemol-api-storage-dev` en `/workspace/storage`).
   - Posee reglas de `develop.watch`: sincroniza cambios en `src/` automáticamente hacia el contenedor e inicia un rebuild ante modificaciones en dependencias (`build.gradle`, `settings.gradle`, etc.).
2. **`postgres` (`bemol-api-postgres-dev`)**:
   - PostgreSQL 18 sobre Alpine Linux.
   - Ejecuta un script de inicialización (`docker/postgres-entrypoint.sh`) que prepara las extensiones y la base de datos de desarrollo.
   - Posee healthcheck configurado con `pg_isready`.
3. **`redis` (`bemol-api-redis-dev`)**:
   - Redis 7 sobre Alpine Linux, configurado sin persistencia en disco para optimizar velocidad de reinicio local.
4. **`mailpit` (`bemol-api-mailpit-dev`)**:
   - Atrapa todos los emails de verificación de cuentas y recuperación de contraseñas para permitir pruebas locales seguras.

## 5. Migraciones de Base de Datos (Flyway)

Flyway es el único mecanismo autorizado para modificar el esquema de base de datos.

### Crear una nueva migración

Para crear un archivo de migración con el timestamp UTC oficial del proyecto:

```bash
make migration <nombre_descriptivo_en_snake_case>
```

Ejemplo:

```bash
make migration add_enrollment_cancellation_reason
```

Esto generará el archivo con formato:

```text
src/main/resources/db/migration/yyyyMMddHHmmss__add_enrollment_cancellation_reason.sql
```

### Reglas estrictas de migraciones
- **Inmutabilidad**: Una migración aplicada en un entorno persistente **nunca se modifica ni se renombra**.
- **Ramas en paralelo**: La configuración común usa `spring.flyway.out-of-order=true` en desarrollo local, staging y producción. Una migración pendiente con timestamp anterior a la última aplicada se ejecuta al integrarse la feature; no se renumera para ajustarla al orden de merge. Se mantienen las validaciones de nombres y checksums. Esto no reemplaza el procedimiento de recreación para corregir archivos ya aplicados en entornos descartables.
- **Compatibilidad**: Comprobar instalación desde cero y actualización desde el historial previo. Una migración tardía debe funcionar sobre el esquema existente; la opción no resuelve dependencias SQL ni preserva automáticamente datos eliminados. En ambientes persistentes, respaldar y verificar la recuperación antes de cambios destructivos. El rollback de imagen no revierte migraciones.
- **Edición segura en desarrollo**: Al crear una migración, editar el archivo generado antes de que el watcher aplique el archivo vacío. Si se necesita corregir un archivo en desarrollo descartable, usar el procedimiento de recreación limpia; nunca usar `Flyway repair` ni saltear validaciones.

## 6. Comandos Habituales y Verificación

El proyecto cuenta con tareas de Gradle organizadas según su costo y velocidad:

```bash
# Validar formato con Google Java Format
./gradlew spotlessCheck

# Aplicar correcciones automáticas de formato
./gradlew spotlessApply

# Suite de pruebas unitarias y slices rápidos (sin PostgreSQL)
./gradlew fastTest

# Pruebas de integración con PostgreSQL y Testcontainers
./gradlew integrationTest

# Análisis estático completo (NullAway + ECJ)
./gradlew staticAnalysis

# Suite completa de tests
./gradlew test
```

### Comandos Make disponibles

El archivo [`Makefile`](../Makefile) centraliza los atajos más frecuentes de Docker y desarrollo:

| Comando | Acción |
|---|---|
| `make dev` | Inicia los servicios con Compose, watch activado y rebuild automático. |
| `make down` | Detiene y remueve los contenedores de desarrollo. |
| `make logs` | Muestra los logs en tiempo real del contenedor del API (`dev`). |
| `make ps` | Muestra el estado actual de los contenedores de Compose. |
| `make reset-data` | **Destructivo:** detiene la pila y elimina los volúmenes locales de PostgreSQL, adjuntos y caché. Sólo para datos de pruebas descartables. |
| `make migration <nombre>` | Genera un nuevo archivo de migración Flyway con timestamp UTC oficial. |
| `make format` | Ejecuta `spotlessApply` para formatear el código. |
| `make format-check` | Ejecuta `spotlessCheck` para validar formato. |
| `make static-analysis` | Ejecuta el análisis estático completo (NullAway + ECJ). |
| `make test` | Ejecuta la suite de pruebas mediante Gradle. |
| `make seed-demo` | Carga el dataset demo de inscripciones en la base de datos. |

### Migraciones con watch y retiro del slug institucional

Antes de generar una migración, detener la API y el watcher de Compose: `make migration`
crea primero un archivo vacío que el entorno en watch podría aplicar antes de editarlo.

El retiro de `Institution.slug` se implementa en
`20261024032009__drop_retired_institution_slug.sql`. El archivo
`20261024031419__remove_institution_slug.sql` debe permanecer vacío: ya se registró
así en un entorno local con checksum 0. No usar `repair` ni reescribir ese registro.
La migración elimina los slugs actuales y reconstruye la búsqueda por nombre;
conserva la columna histórica en `institutions_aud`. Respaldar la base antes de
aplicarla; revertir el código anterior requiere restaurar los slugs y su esquema
desde ese respaldo, no alcanza con volver al binario anterior.

### Escenario conectado del seed

`SEED_INSTITUTION=<uuid> SEED_YEAR=2026 make seed-demo` agrega a Mariana Suárez
dos personas a cargo con vínculos activos: Lucía Ferreyra, postulante con solicitud
enviada al CAVI, y Camila Roldán, estudiante con solicitud aprobada y tres cursadas
del CAVB instrumental (Lenguaje Musical, Ensamble y Piano). Las cursadas reutilizan
comisiones, docentes y horarios existentes; incluyen el turno individual y sus
historiales, sin superposiciones. Ambas solicitudes tienen un PDF real de presentación
musical; el de Lucía está pendiente de revisión y el de Camila está aceptado.

El seed es aditivo y transaccional, fuera de Flyway. Los IDs son estables por
institución/dataset y las solicitudes existentes no se reinician al repetirlo.
No reemplaza contraseñas, permisos ni archivos. Requiere almacenamiento local:
con Compose copia los PDFs al servicio `SEED_API_SERVICE` (default `dev`); con
`SEED_TRANSPORT=psql` indicar `SEED_STORAGE_DIR`, el directorio de la API accesible
desde la shell. `COMPOSE_FILE` y `COMPOSE_ENV_FILES` permiten seleccionar los
archivos de infraestructura al ejecutarlo explícitamente en un entorno compartido.
Los PDFs se preparan antes de la transacción; si SQL falla, pueden quedar estos
dos archivos de muestra sin referencias, pero no registros parciales en la base.
Hacer backup e inventario antes de ejecutarlo sobre una base persistente; no usar
`reset-data` para ampliar una demo existente.

## 7. Relación con Staging y Producción

- La preparación de imágenes de producción (`ghcr.io/typeitorg/bemol-api`) se realiza a través de CI.
- Los ambientes compartidos de Staging y Producción requieren una VPS provisionada bajo las especificaciones de `bemol-infra`.
- La configuración existente en el repositorio no implica que un entorno externo esté actualmente encendido o desplegado.
