#!/bin/bash
# PreToolUse gate: make sure Kotlin is spotless-clean BEFORE it leaves the box.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool. Runs
# `spotlessApply`; if that reformats any tracked .kt/.kts file, the push/PR is
# blocked (exit 2) so the agent commits the formatting fix first. This turns
# CI's `spotlessCheck` failure into an in-session block — no red PR, no round
# trip. `spotlessApply` runs the same formatters CI's `spotlessCheck` verifies,
# so a clean apply means a green check.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

# --- Parse the tool call off stdin; decide whether this call is a boundary. ---
payload="$(cat)"
should_gate="$(
  printf '%s' "$payload" | python3 -c '
import json, shlex, sys
try:
    data = json.load(sys.stdin)
except Exception:
    print("no"); sys.exit(0)
tool = data.get("tool_name", "")
if tool.endswith("create_pull_request"):
    print("yes"); sys.exit(0)
if tool != "Bash":
    print("no"); sys.exit(0)
cmd = (data.get("tool_input") or {}).get("command", "")
# Tokenize like a shell so `push` inside a quoted commit message or heredoc
# stays one token and is NOT mistaken for the push subcommand.
try:
    tokens = shlex.split(cmd, comments=True)
except ValueError:
    tokens = cmd.split()
GLOBAL_WITH_ARG = {"-c", "-C", "--namespace", "--git-dir", "--work-tree", "--exec-path"}
for i, t in enumerate(tokens):
    if t != "git" and not t.endswith("/git"):
        continue
    j = i + 1
    while j < len(tokens):  # skip git global options to reach the subcommand
        tok = tokens[j]
        if tok in GLOBAL_WITH_ARG:
            j += 2; continue
        if tok.startswith("-"):
            j += 1; continue
        break
    if j < len(tokens) and tokens[j] == "push":
        print("yes"); sys.exit(0)
print("no")
' 2>/dev/null
)"

[ "$should_gate" = "yes" ] || exit 0

# Nothing to format if no Kotlin is tracked/changed at all — cheap early out.
if ! git ls-files --error-unmatch '*.kt' '*.kts' >/dev/null 2>&1; then
  exit 0
fi

# Snapshot Kotlin state (vs HEAD, so staged + unstaged both count) before/after
# formatting; any delta means the committed tree wasn't spotless.
before="$(git diff HEAD -- '*.kt' '*.kts' 2>/dev/null | sha1sum)"

log="$(mktemp /tmp/spotless-gate.XXXXXX.log)"
if ! ./gradlew spotlessApply >"$log" 2>&1; then
  # Any failure blocks. The web sandbox pre-seeds the Gradle distribution (see
  # .claude/hooks/session-start.sh) and Gradle resolves deps through the proxy,
  # so spotlessApply no longer fails for infra reasons — a failure here is a
  # real formatting/compile error, not a restricted-sandbox hiccup.
  echo "BLOCKED: spotlessApply failed — fix the build/formatting error before pushing." >&2
  echo "----- gradle output (tail) -----" >&2
  tail -n 40 "$log" >&2
  rm -f "$log"
  exit 2
fi
rm -f "$log"

after="$(git diff HEAD -- '*.kt' '*.kts' 2>/dev/null | sha1sum)"

if [ "$before" != "$after" ]; then
  echo "BLOCKED: spotlessApply reformatted Kotlin files that were about to be pushed." >&2
  echo "The changes below are now in your working tree. Commit them, then retry:" >&2
  echo >&2
  git diff --name-only HEAD -- '*.kt' '*.kts' >&2
  echo >&2
  echo "  git add -A && git commit -m 'style: apply spotless' && <retry the push>" >&2
  echo "(CI runs 'spotlessCheck'; pushing now would fail the lint job.)" >&2
  exit 2
fi

exit 0
