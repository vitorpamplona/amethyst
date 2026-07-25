# Buzz agent `--exec` wrapper

`agent-exec.sh` is the reference command you point `amy buzz agent serve --exec` at to turn
Buzz job requests into pull requests. It runs a coding agent (Claude Code by default) on the
task inside the job's isolated git worktree, then commits, pushes the job branch, and opens a
PR — printing the PR URL as the job result. **It never touches the default branch and never
force-pushes; the merge is a human action on GitHub.**

See the design in [`cli/plans/2026-07-25-buzz-agent-support-channel.md`](../../cli/plans/2026-07-25-buzz-agent-support-channel.md).

## The contract (how the scheduler calls it)

| Channel | Meaning |
|---|---|
| **stdin** | the task text (the kind-43001 request) |
| **cwd** | the job's git worktree — a fresh branch off `--base-ref` |
| **env** | `BUZZ_JOB_ID` `BUZZ_REQUESTER` `BUZZ_CHANNEL` `BUZZ_RELAY` `BUZZ_AGENT` `BUZZ_UPVOTES` `BUZZ_BRANCH` `BUZZ_WORKTREE` `BUZZ_BASE_REF` |
| **stdout** | becomes the job **result** (kind-43004) — the wrapper prints the PR URL |
| **non-zero exit** | becomes the job **error** (kind-43006); stderr is the detail |

Its steps: read task → run agent → verify a diff exists → commit → push the branch → open (or
reuse) the PR → print the URL.

## Usage

```bash
amy buzz agent serve wss://your-buzz-relay <channel-uuid> \
  --exec /path/to/tools/buzz-agent/agent-exec.sh \
  --worktree /path/to/amethyst \
  --accept-from-channel \
  --parallel 2
```

`--worktree` is **required** (the wrapper needs `BUZZ_BRANCH`/`BUZZ_WORKTREE`). `--parallel N`
runs N jobs at once, each in its own worktree+branch.

## Config knobs (env)

| Var | Default | Purpose |
|---|---|---|
| `AGENT_CMD` | *(unset)* | Override the whole agent invocation. Receives the prompt on **stdin** and as `$AGENT_PROMPT`. Use this for Goose/Codex or a custom runner. |
| `AGENT_ALLOWED_TOOLS` | `Edit,Write,Read,Bash,Glob,Grep` | Claude Code `--allowedTools` — the agent's capability boundary. |
| `COMMIT_PREFIX` | `feat` | Prefix for the auto-commit subject when the agent didn't commit. |

Adjust the default `claude -p … --permission-mode acceptEdits --allowedTools …` line to match
your Claude Code version, or bypass it entirely with `AGENT_CMD`.

## Security — the guardrails that make this safe

Buzz authorizes by identity, not capability flags, so **none** of the "can't merge or destroy
`main`, can't code other things" guarantees come from Buzz. They come from three things you
configure here:

1. **A PR-only git credential.** Authenticate `gh` on the agent host with a **fine-grained PAT
   scoped to the `amethyst` repo only**, granting exactly **Contents: Read and write** +
   **Pull requests: Read and write** — and nothing else. No Administration, no org scope. The
   token can open PRs on feature branches; it cannot merge, bypass checks, or reach other repos.
2. **Branch protection on the default branch.** Protect `main`: require a pull request, require
   review approval, require status checks (CI) to pass, and **block force-pushes and branch
   deletion**. Do **not** grant the bot an exception. This is what actually stops a bad merge —
   the wrapper only ever pushes `claude/job-*` feature branches.
3. **Scoped intake + agent tools.** Run the scheduler with `--accept-from` / `--accept-from-channel`
   so only your team's keys can file jobs, keep `AGENT_ALLOWED_TOOLS` tight, and run on a host
   whose checkout is amethyst only — so the agent "can't code other things."

The merge is deliberately outside this loop: a completed job's result is its PR, and a human
merges it on GitHub.

## Testing

`agent-exec.sh` is verified end-to-end against a throwaway repo with a stubbed `gh` + agent
(reads task → runs agent → commits the diff → pushes the feature branch → prints the PR URL;
and errors cleanly when the agent makes no changes). Point `AGENT_CMD` at a stub to dry-run the
git/PR plumbing without invoking a real agent.
