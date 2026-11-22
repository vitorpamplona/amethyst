#!/bin/bash
# PreToolUse warning: a newly added English string that looks like it counts something.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool.
# Delegates to plural_candidates_check.py, which lists the `<string>`s HEAD adds
# to the two default catalogs (against the merge base with the upstream main)
# whose text counts a noun, hedges with `(s)`, or hardcodes `1 <noun>`. Such a
# string should be a `<plurals>` before any locale translates it.
#
# Unlike the other pre-push hooks this one NEVER blocks: whether a count needs a
# plural depends on meaning and the call site, which a regex cannot judge. It
# always exits 0. A PreToolUse hook's plain stdout on exit 0 reaches neither
# agent's model, so the report goes out as JSON: `additionalContext` is added to
# the model's context in both Claude Code and Codex, and `systemMessage` shows it
# to the user.
#
# The terminal push gets the same warning from .git-hooks/pre-push.
#
# Run the scan by hand any time with:  tools/agent-hooks/plural_candidates_check.py
#                                       tools/agent-hooks/plural_candidates_check.py --all
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

report="$(python3 "$hook_dir/plural_candidates_check.py" 2>&1)" || exit 0
[ -n "$report" ] || exit 0

printf '%s' "$report" | python3 -c '
import json, sys
report = sys.stdin.read()
print(json.dumps({
    "systemMessage": report,
    "hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": report},
}))
' || printf '%s\n' "$report" >&2
exit 0
