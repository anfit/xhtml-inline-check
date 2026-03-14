#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SMOKE_OLD="$PROJECT_ROOT/fixtures/support/smoke/old/root.xhtml"
SMOKE_NEW="$PROJECT_ROOT/fixtures/support/smoke/new/root.xhtml"

if ! command -v gradle >/dev/null 2>&1; then
  echo "gradle is required on PATH to run baseline verification." >&2
  echo "This repository does not yet ship a Gradle wrapper." >&2
  exit 1
fi

echo "Running baseline Gradle tests..."
gradle -p "$PROJECT_ROOT" test

echo "Installing the facelets-verify distribution..."
gradle -p "$PROJECT_ROOT" installDist

echo "Running the smoke CLI invocation..."
gradle -p "$PROJECT_ROOT" runFaceletsVerify --args="$SMOKE_OLD $SMOKE_NEW"
