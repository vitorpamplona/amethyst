#!/bin/bash
# Stop hook: format Kotlin sources, but only when the working tree actually
# has modified Kotlin files — skips the Gradle invocation on Q&A-only turns.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

if git status --porcelain 2>/dev/null | grep -qE '[.]kts?$'; then
  ./gradlew spotlessApply 2>/dev/null
fi

exit 0
