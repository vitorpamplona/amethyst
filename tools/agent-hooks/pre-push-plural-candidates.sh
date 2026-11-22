#!/bin/bash
# PreToolUse warning: a newly added English string that looks like it counts something.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool, and runs
# plural_candidates_check.py, which says what it flags and why. Unlike the other
# pre-push hooks this one NEVER blocks (it always exits 0): whether a count needs
# a plural depends on meaning and the call site. Exit 0 with plain stdout reaches
# neither agent's model, so the script prints hook JSON (--hook-json) instead.
# The terminal push gets the same warning from .git-hooks/pre-push.
set -uo pipefail

hook_dir="$(cd "$(dirname "$0")" && pwd)"
# Shared by Claude Code (.claude/settings.json) and Codex (.codex/hooks.json):
# resolve the repo from this script, not from either tool's env vars.
cd "$hook_dir/../.." || exit 0

# --- Is this call a push/PR boundary? ---
payload="$(cat)"

# Cheap pure-bash pre-filter before paying for a python spawn; see
# pre-push-orphan-strings.sh. This hook runs on EVERY Bash tool call.
case "$payload" in
  *push*|*pull_request*) ;;
  *) exit 0 ;;
esac

printf '%s' "$payload" | python3 "$hook_dir/lib/git_push_gate.py" || exit 0

python3 "$hook_dir/plural_candidates_check.py" --hook-json
exit 0
