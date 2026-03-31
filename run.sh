#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_GLOB="$PROJECT_ROOT/build/libs/*-all.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "java is required on PATH to run the executable jar." >&2
  exit 1
fi

if compgen -G "$JAR_GLOB" >/dev/null; then
  JAR_PATH="$(ls -1t $JAR_GLOB | head -n 1)"
else
  echo "No executable jar found under build/libs/. Building one now..."
  "$PROJECT_ROOT/build.sh"
  JAR_PATH="$(ls -1t $JAR_GLOB | head -n 1)"
fi

echo "Running $JAR_PATH"
exec java -jar "$JAR_PATH" "$@"
