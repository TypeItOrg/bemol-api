#!/bin/sh
set -eu

if [ "$(id -u)" -eq 0 ]; then
  mkdir -p /workspace/storage /home/gradle/.gradle
  chown -R -h -P gradle:gradle -- /workspace/storage /home/gradle/.gradle
  exec runuser -u gradle -- "$0" "$@"
fi

WATCH_PATHS="src build.gradle settings.gradle"
APP_PID=""

start_app() {
  ./gradlew bootRun &
  APP_PID=$!
  echo ">>> Iniciando Spring Boot"
}

stop_app() {
  if [ -n "${APP_PID}" ] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}

shutdown() {
  stop_app
  exit 0
}

trap shutdown INT TERM

start_app

while inotifywait -r -e modify,create,delete,move ${WATCH_PATHS}; do
  echo ">>> Se han detectado cambios en el código fuente, reiniciando..."
  stop_app
  start_app
done
