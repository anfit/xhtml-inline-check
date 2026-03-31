#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v gradle >/dev/null 2>&1; then
  echo "gradle is required on PATH to build the executable jar." >&2
  echo "This repository does not yet ship a Gradle wrapper." >&2
  exit 1
fi

echo "Building executable uber jar (fatJar)..."
gradle -p "$PROJECT_ROOT" fatJar

echo "Build complete. Jar output(s):"
find "$PROJECT_ROOT/build/libs" -maxdepth 1 -type f -name '*-all.jar' -print
