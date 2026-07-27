#!/usr/bin/env bash
#
# agent-exec.sh — self-contained headless test for the `--exec` wrapper that turns a Buzz job
# into a pull request (tools/buzz-agent/agent-exec.sh).
#
# job-loop.sh proves the scheduler drives *an* --exec program; this proves the real one does the
# right thing with git and `gh`. Both the agent and `gh` are stubbed, so it needs no network, no
# credentials, and no Claude Code — it exercises the plumbing around them:
#
#   task on stdin → agent → verify a diff → commit → push the job branch → open/reuse PR → url
#
# The failure paths matter as much as the happy one: an agent that changed nothing must become a
# job error, and the default-branch guard must actually hold.
#
# Usage: ./agent-exec.sh
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
EXEC="$REPO_ROOT/tools/buzz-agent/agent-exec.sh"
BASE="$SCRIPT_DIR/state-agent-exec"
PASS=0; FAIL=0
check() { if [[ "$2" == "$3" ]]; then echo "  ✓ PASS $1 ($2)"; PASS=$((PASS+1)); else echo "  ✗ FAIL $1: got [$2] want [$3]"; FAIL=$((FAIL+1)); fi; }
contains() { if [[ "$2" == *"$3"* ]]; then echo "  ✓ PASS $1"; PASS=$((PASS+1)); else echo "  ✗ FAIL $1: [$2] lacks [$3]"; FAIL=$((FAIL+1)); fi; }

rm -rf "$BASE"; mkdir -p "$BASE/bin"

# --- stub gh: default branch, no existing PR, and a PR URL on create -------------------------
cat > "$BASE/bin/gh" <<'GH'
#!/usr/bin/env bash
case "$1 $2" in
  "repo view") echo main ;;
  "pr list")   echo "" ;;                       # no open PR for this head
  "pr create") echo "https://github.com/example/repo/pull/42" ;;
  *) exit 1 ;;
esac
GH
chmod +x "$BASE/bin/gh"
export PATH="$BASE/bin:$PATH"

# --- a bare "origin" so the real `git push` in the script has somewhere to go ----------------
git init -q --bare "$BASE/origin.git"
git init -q "$BASE/repo"
cd "$BASE/repo"
git config user.email t@t; git config user.name t
echo seed > seed.txt; git add -A; git -c commit.gpgsign=false commit -qm seed
git branch -M main
git remote add origin "$BASE/origin.git"
git push -q -u origin main

run_case() { # run_case <branch> <agent-cmd> ; echoes exit code, sets OUT/ERRTXT
    local branch="$1" agentcmd="$2" task="${3:-Add a greeting file}"
    git -C "$BASE/repo" checkout -q main
    git -C "$BASE/repo" worktree remove --force "$BASE/wt" 2>/dev/null
    git -C "$BASE/repo" worktree add -q -b "$branch" "$BASE/wt" main 2>/dev/null
    OUT="$(cd "$BASE/wt" && printf '%s' "$task" | env \
        BUZZ_JOB_ID=job123 BUZZ_REQUESTER=alice BUZZ_BRANCH="$branch" \
        BUZZ_WORKTREE="$BASE/wt" BUZZ_BASE_REF=main AGENT_CMD="$agentcmd" \
        bash "$EXEC" 2>"$BASE/err.txt")"
    RC=$?
    ERRTXT="$(cat "$BASE/err.txt")"
    return $RC
}

echo "> 1. happy path: agent edits a file → commit → push → PR url on stdout"
run_case "claude/job-1" 'echo "wrote greeting" && echo hello > greeting.txt'
check "exit code" "$?" "0"
contains "stdout is the PR url" "$OUT" "https://github.com/example/repo/pull/42"
check "branch landed on origin" "$(git -C "$BASE/origin.git" rev-parse --verify -q claude/job-1 >/dev/null && echo yes || echo no)" "yes"
check "commit was made" "$(git -C "$BASE/wt" rev-list --count main..HEAD)" "1"
contains "commit subject uses the task's first line" "$(git -C "$BASE/wt" log -1 --pretty=%s)" "feat: Add a greeting file"
contains "file the agent wrote is in the commit" "$(git -C "$BASE/wt" show --stat --oneline HEAD)" "greeting.txt"
contains "the agent got the task on stdin" "$ERRTXT" "Add a greeting file"

echo "> 2. agent makes no changes → job error, non-zero exit"
run_case "claude/job-2" 'echo "thought about it"'
check "exit code" "$?" "1"
contains "explains why" "$ERRTXT" "no changes"

echo "> 3. agent commits by itself → wrapper pushes, does not double-commit"
run_case "claude/job-3" 'echo x > f.txt && git add -A && git -c user.email=a@b -c user.name=a commit -qm "agent: own commit"'
check "exit code" "$?" "0"
check "exactly one commit" "$(git -C "$BASE/wt" rev-list --count main..HEAD)" "1"
contains "kept the agent's own subject" "$(git -C "$BASE/wt" log -1 --pretty=%s)" "agent: own commit"

echo "> 4. refuses to operate on the default branch"
# the worktree sits on a scratch branch, but the scheduler hands it BUZZ_BRANCH=main —
# exactly the mistake the guard exists to stop.
git -C "$BASE/repo" worktree remove --force "$BASE/wt" 2>/dev/null
git -C "$BASE/repo" worktree add -q -b scratch-guard "$BASE/wt" main
OUT="$(cd "$BASE/wt" && printf 'do a thing' | env BUZZ_JOB_ID=job4 BUZZ_BRANCH=main     BUZZ_WORKTREE="$BASE/wt" BUZZ_BASE_REF=main AGENT_CMD='echo hi > x.txt'     bash "$EXEC" 2>"$BASE/err.txt")"; RC=$?
ERRTXT="$(cat "$BASE/err.txt")"
check "exit code" "$RC" "1"
contains "says why" "$ERRTXT" "refusing to operate on the default branch"
check "nothing was pushed to main" "$(git -C "$BASE/origin.git" rev-parse main)" "$(git -C "$BASE/repo" rev-parse main)"

echo "> 5. missing scheduler env is a hard error, not a silent no-op"
OUT="$(printf 'task' | env -u BUZZ_BRANCH BUZZ_WORKTREE="$BASE/wt" bash "$EXEC" 2>&1)"; RC=$?
check "exit code" "$RC" "1"
contains "names the missing var" "$OUT" "BUZZ_BRANCH"

echo "> 6. empty task is rejected before the agent runs"
run_case "claude/job-6" 'echo should-not-run > ran.txt' "   "
check "exit code" "$?" "1"
contains "says why" "$ERRTXT" "empty task"

echo
echo "> RESULTS: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
