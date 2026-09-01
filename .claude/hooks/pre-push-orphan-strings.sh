#!/bin/bash
# PreToolUse gate: no locale string may outlive its default-locale key.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool.
# Delegates to orphan_strings_check.py, which compares every
# `values-<locale>/*.xml` resource name against the union of names declared in
# that tree's default `values/*.xml`. Anything present in a locale but absent
# from the default is an orphan: in an Android res tree Android lint reports it
# as an [ExtraTranslation] ERROR, which aborts `:amethyst:lint<Variant>` and
# therefore the whole `test-and-build-android` CI job.
#
# Why a dedicated hook instead of "just run lint": `:amethyst:lintFdroidBenchmark`
# takes ~19 minutes on a warm daemon, so nobody runs it per-commit. This check is
# a directory scan and finishes in well under a second.
#
# Run the scan by hand any time with:  .claude/hooks/orphan_strings_check.py
set -uo pipefail

hook_dir="$(cd "$(dirname "$0")" && pwd)"
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# --- Is this call a push/PR boundary? ---
payload="$(cat)"

# Cheap pure-bash pre-filter before paying for a python spawn. The gate below
# can only answer "yes" for a payload containing "push" (a git push command) or
# "pull_request" (the create_pull_request MCP tool), so anything else is a
# guaranteed no. This hook runs on EVERY Bash tool call, and the spawn it skips
# costs ~35ms each time.
case "$payload" in
  *push*|*pull_request*) ;;
  *) exit 0 ;;
esac

printf '%s' "$payload" | python3 "$hook_dir/lib/git_push_gate.py" || exit 0

exec python3 "$hook_dir/orphan_strings_check.py"
