#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v gradle >/dev/null 2>&1; then
  echo "gradle is required on PATH to run deterministic-output verification." >&2
  echo "This repository does not yet ship a Gradle wrapper." >&2
  exit 1
fi

echo "Running repeated-execution deterministic CLI verification..."
gradle -p "$PROJECT_ROOT" test --tests dev.xhtmlinlinecheck.cli.FaceletsVerifyDeterministicOutputTest
