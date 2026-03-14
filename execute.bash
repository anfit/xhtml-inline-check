#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TASK_FILE="$PROJECT_ROOT/docs/development-tasks.md"

if [[ ! -f "$TASK_FILE" ]]; then
  echo "Task file not found: $TASK_FILE" >&2
  exit 1
fi

if [[ ! -s "$TASK_FILE" ]]; then
  echo "No task found in $TASK_FILE" >&2
  exit 1
fi

# Read first line
task_line=$(head -n 1 "$TASK_FILE")

# Remove first line from file
tail -n +2 "$TASK_FILE" > "$TASK_FILE.tmp"
mv "$TASK_FILE.tmp" "$TASK_FILE"

echo "Executing task:"
echo "$task_line"

codex exec \
  -C "$PROJECT_ROOT" \
  -s workspace-write \
  "You are about to execute a task in the context of this repository. Before acting assess if it is still relevant. Read and then update CONTEXT.md. ${task_line}. When done commit all the changes."

if [[ -n "$(git -C "$PROJECT_ROOT" status --porcelain)" ]]; then
  git -C "$PROJECT_ROOT" add -A

  commit_message="$task_line"
  commit_message="${commit_message:0:72}"

  git -C "$PROJECT_ROOT" commit -m "$commit_message"
else
  echo "No changes to commit."
fi
