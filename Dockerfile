# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS deps

WORKDIR /workspace

COPY --chmod=0755 gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

FROM deps AS builder

WORKDIR /workspace

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

FROM builder AS extract

WORKDIR /workspace

RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination build/extracted

FROM eclipse-temurin:21-jre-jammy AS prod

ARG UID=10001
ARG CHROMIUM_VERSION=157.0.8103.38

COPY --chmod=0755 docker/install-chromium.sh /usr/local/bin/install-chromium

RUN install-chromium "${CHROMIUM_VERSION}"

ENV BEMOL_ENROLLMENT_PDF_CHROMIUM_EXECUTABLE=/opt/chrome/chrome

RUN adduser \
        --disabled-password \
        --gecos "" \
        --home "/nonexistent" \
        --no-create-home \
        --shell "/usr/sbin/nologin" \
        --uid "${UID}" \
        appuser \
    && install -d -o appuser -g appuser /app/logs /app/storage

WORKDIR /app
USER appuser


COPY --chown=appuser:appuser --from=extract /workspace/build/extracted/dependencies/ ./
COPY --chown=appuser:appuser --from=extract /workspace/build/extracted/spring-boot-loader/ ./
COPY --chown=appuser:appuser --from=extract /workspace/build/extracted/snapshot-dependencies/ ./
COPY --chown=appuser:appuser --from=extract /workspace/build/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

FROM gradle:9.4.1-jdk21-jammy AS dev

ARG CHROMIUM_VERSION=157.0.8103.38

WORKDIR /workspace

USER root

COPY --chmod=0755 docker/install-chromium.sh /usr/local/bin/install-chromium

RUN install-chromium "${CHROMIUM_VERSION}"

ENV BEMOL_ENROLLMENT_PDF_CHROMIUM_EXECUTABLE=/opt/chrome/chrome

RUN apt-get update \
    && apt-get install --yes --no-install-recommends inotify-tools \
    && rm -rf /var/lib/apt/lists/*

COPY --chmod=0755 gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
COPY docker/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh

RUN chmod +x /usr/local/bin/dev-entrypoint.sh \
    && mkdir -p /workspace/storage \
    && chown -R gradle:gradle /workspace

EXPOSE 8080

CMD ["/usr/local/bin/dev-entrypoint.sh"]
