# DESARROLLO LOCAL Y ENTORNOS

Esta guía describe cómo configurar, ejecutar y operar el entorno de desarrollo local para el backend de Boero.

## 1. Responsabilidades y Alcance

- **Este repositorio (`boero-api`)**: Es dueño del código de la aplicación, el `Dockerfile`, la configuración de desarrollo local en `compose.yaml` y las migraciones Flyway.
- **Repositorio de infraestructura (`boero-infra`)**: Administra la topología compartida, Nginx, certificados TLS, despliegues y procedimientos de rollback para ambientes de staging y producción.
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
cp .env.dev.example .env.dev
```

El archivo `.env.dev` contiene valores predefinidos listos para desarrollo local (conexiones a contenedores Docker, claves dummy de JWT y configuración de correo apuntada a Mailpit).

### Paso 2: Iniciar el entorno con Make

```bash
make dev
```

Este comando levanta la pila completa en segundo plano utilizando `compose.yaml`.

### Paso 3: Puertos y servicios disponibles

Una vez iniciado el entorno, los siguientes servicios quedan disponibles en `localhost`:

| Servicio | Puerto Local | Descripción |
|---|---|---|
| **Boero API** | `8080` | Endpoint HTTP de la aplicación (`http://localhost:8080`). |
| **PostgreSQL** | `5432` | Base de datos relacional (`boero_db`, usuario `boero`, clave `boero`). |
| **Redis** | `6379` | Almacenamiento en memoria para tokens, sesiones y rate limiting. |
| **Mailpit (Web)** | `8025` | Interfaz web para inspeccionar correos salientes (`http://localhost:8025`). |
| **Mailpit (SMTP)** | `1025` | Servidor SMTP simulado sin envío real a internet. |

## 4. Topología de Servicios en Desarrollo

El archivo `compose.yaml` orquesta cuatro servicios interconectados a través de la red `boero-api-network-dev`:

1. **`dev` (`boero-api-dev`)**:
   - Contenedor que ejecuta la aplicación Spring Boot mediante el target `dev` del Dockerfile.
   - Monta el volumen de caché de Gradle (`gradle-cache`) y el volumen persistente de almacenamiento local (`boero-api-storage-dev` en `/workspace/storage`).
   - Posee reglas de `develop.watch`: sincroniza cambios en `src/` automáticamente hacia el contenedor e inicia un rebuild ante modificaciones en dependencias (`build.gradle`, `settings.gradle`, etc.).
2. **`postgres` (`boero-api-postgres-dev`)**:
   - PostgreSQL 18 sobre Alpine Linux.
   - Ejecuta un script de inicialización (`docker/postgres-entrypoint.sh`) que prepara las extensiones y la base de datos de desarrollo.
   - Posee healthcheck configurado con `pg_isready`.
3. **`redis` (`boero-api-redis-dev`)**:
   - Redis 7 sobre Alpine Linux, configurado sin persistencia en disco para optimizar velocidad de reinicio local.
4. **`mailpit` (`boero-api-mailpit-dev`)**:
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
| `make reset-data` | **Atención:** Detiene y elimina los volúmenes de datos (`postgres-data` y `storage`). |
| `make migration <nombre>` | Genera un nuevo archivo de migración Flyway con timestamp UTC oficial. |
| `make format` | Ejecuta `spotlessApply` para formatear el código. |
| `make format-check` | Ejecuta `spotlessCheck` para validar formato. |
| `make static-analysis` | Ejecuta el análisis estático completo (NullAway + ECJ). |
| `make test` | Ejecuta la suite de pruebas mediante Gradle. |
| `make seed-demo` | Carga el dataset demo de inscripciones en la base de datos. |

## 7. Relación con Staging y Producción

- La preparación de imágenes de producción (`ghcr.io/typeitorg/boero-api`) se realiza a través de CI.
- Los ambientes compartidos de Staging y Producción requieren una VPS provisionada bajo las especificaciones de `boero-infra`.
- La configuración existente en el repositorio no implica que un entorno externo esté actualmente encendido o desplegado.
