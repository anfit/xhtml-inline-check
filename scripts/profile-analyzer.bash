#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
iterations="${1:-5}"

export GRADLE_USER_HOME="$repo_root/.gradle-user-home"
export TEMP="$repo_root/.tmp"
export TMP="$repo_root/.tmp"
export XHTML_INLINE_CHECK_PROFILE=1

args_line='dummy/report.xhtml dummy/report-flattened.xhtml --base-old dummy --base-new dummy --format json'

for ((iteration=1; iteration<=iterations; iteration++)); do
  echo "Iteration $iteration"
  gradle runFaceletsVerify --args="$args_line" 2>&1 | grep '\[profile\]'
done
