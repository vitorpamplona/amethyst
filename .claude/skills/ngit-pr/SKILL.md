---
name: ngit-pr
description: How to create, review, revise, and merge pull requests in this repo, which can be published TWO ways — GitHub (the `gh` CLI) and git-over-nostr (the `ngit` CLI, where PRs are nostr **proposals** reviewed on gitworkshop.dev). Use whenever a task involves opening/updating/merging a PR by either mechanism, the `pr/feat/*` branches, the `ngit` or `gh` CLIs, gitworkshop.dev, or a `nostr://` remote. Remote names vary per clone (and a collaborator may have only one) — this skill identifies remotes by URL, and covers the three-mains alignment gate the nostr flow depends on.
---

# Pull requests: GitHub **and** git-over-nostr

This repo can be contributed to **two** ways, via **two kinds** of remote. Both end up in GitHub `main`.

| Remote kind | URL pattern | Role |
|--|--|--|
| **GitHub** | `github.com/vitorpamplona/amethyst` | **Canonical** `main`. Moves constantly (bots merge often) — a moving target. |
| **git-over-nostr** | `nostr://…/relay.ngit.dev/amethyst` | `ngit`. A push fans out to GitHub **and** the GRASP git servers and publishes nostr events. PRs are **proposals**, reviewed on **gitworkshop.dev**. |

## Step 0 — identify YOUR remotes (names are not universal)

Remote **names are per-clone**. In the maintainer's checkout the GitHub remote is `upstream` and the nostr remote is `origin`, but yours may differ, and **you may have only one of them** (e.g. cloned straight from `nostr://…`, so the nostr remote is your `origin` and there is no separate GitHub remote — pushing it still reaches GitHub via fan-out). Detect by **URL**, never assume a name:

```bash
git remote -v
GH_REMOTE=$(git remote -v    | awk '/github\.com/   {print $1; exit}')   # GitHub remote (may be empty)
NOSTR_REMOTE=$(git remote -v | awk '/nostr:\/\//     {print $1; exit}')  # git-over-nostr remote (may be empty)
echo "github=$GH_REMOTE  nostr=$NOSTR_REMOTE"
```

The examples below use `$GH_REMOTE` / `$NOSTR_REMOTE` — substitute whichever you have.

## Which path?

| | **GitHub path** (`gh`) | **nostr path** (`ngit`) |
|--|--|--|
| Needs | a GitHub remote + `gh auth status` | a `nostr://` remote + `ngit` ≥ 2.5.0 |
| PR lives on | GitHub only | nostr + GitHub + GRASP (fans out) |
| Use when | Default; PR only needs to be on GitHub. Simplest, no alignment gate. | The PR must be visible/reviewable over nostr (gitworkshop), or you're revising/merging an existing **proposal** (a `pr/feat/*`). |

**Default to GitHub** unless the task is specifically about a nostr proposal (e.g. "the PRs on origin", a gitworkshop link, a `pr/feat/*` branch). If you only have one remote, that decides the path for you. Revise/merge a PR on **the same path it was created** — don't revise a GitHub PR via ngit or vice-versa.

---

# GitHub path (`gh`)

The normal flow most of this repo's history uses ("Merge pull request #NNNN …"). Requires a GitHub remote (`$GH_REMOTE`) and `gh auth status` OK.

```bash
# create — branch off main, push, open the PR
git checkout -b feat/<slug> main
git push -u "$GH_REMOTE" feat/<slug>
gh pr create --repo vitorpamplona/amethyst --base main --head feat/<slug> \
  --title "feat: …" --body "…"

# review / list
gh pr list --repo vitorpamplona/amethyst
gh pr view <number> --repo vitorpamplona/amethyst   # --comments for the thread

# revise — push more commits to the same branch
git push "$GH_REMOTE" feat/<slug>

# merge (maintainer)
gh pr merge <number> --repo vitorpamplona/amethyst --merge   # or --squash
```

GitHub is the source of truth for this path — no three-mains gate. Standard Git Workflow rules from CLAUDE.md still apply (conventional commits, never `--no-verify`).

---

# nostr path (`ngit`)

Requires a `nostr://` remote (`$NOSTR_REMOTE`) and `ngit` ≥ 2.5.0 (`ngit --version`).

**Mental model:** an ngit PR ("proposal") is a *linear patch series off `main`*, published as nostr events. A "revision" is a new version of that proposal. Merging applies the series to `main` and publishes a merged-status event. There is **no** GitHub PR number; `ngit pr merge` makes a plain merge commit (amend it to a readable message).

## ⚠ The three-mains alignment gate (the thing that breaks everything)

Up to **three** `main` heads drift apart:

- GitHub main (`$GH_REMOTE/main` if you have it) — newest, moves every few minutes
- nostr main (`$NOSTR_REMOTE/main` tracking ref) — **lags**, often far behind
- local `main`

**Every create/revise/merge requires the proposal's base to equal the nostr `main`, and pushing `main` to the nostr remote requires GitHub's main to be an ancestor of what you push.** When misaligned, ngit **rejects pre-flight and publishes nothing** (safe — nothing half-breaks; realign and retry). Don't `--force` past it.

```bash
git fetch --all
echo "github=$([ -n "$GH_REMOTE" ] && git rev-parse "$GH_REMOTE/main")  \
nostr=$(git ls-remote "$NOSTR_REMOTE" -h refs/heads/main | awk '{print $1}')  \
local=$(git rev-parse main)"
# all present heads equal → proceed.
# local behind GitHub?  git merge --ff-only "$GH_REMOTE/main"  (or "$NOSTR_REMOTE/main" if that's all you have)
# nostr behind local?   git push "$NOSTR_REMOTE" main   (clean fast-forward only)
```

If you have **only** the nostr remote: align local `main` to `$NOSTR_REMOTE/main`; GitHub is handled by fan-out, and any GitHub/GRASP disagreement surfaces as an ngit rejection on push. If GitHub diverged from nostr (`out of sync with nostr` on push), that's a maintainer `ngit sync --ref-name refs/heads/main --force` situation — **stop and ask the human**, don't run a forced sync unprompted.

## Pushes are slow — run them in the background

`git push "$NOSTR_REMOTE" …`, `ngit send`, and `ngit pr merge` fan out to relays + GRASP servers and **routinely exceed 2 minutes**. Run with `run_in_background: true` and poll (e.g. `git ls-remote "$NOSTR_REMOTE"` for the expected ref). A foreground call hits the 2-minute tool timeout even while the push is actually succeeding.

## Identity

`ngit account whoami` shows the signing key; `ngit send`/`ngit pr merge` sign with **that** key regardless of original author. The maintainer (`VitorPamplona`, `_@vitorpamplona.com`) revising/merging a contributor's proposal with their own key is expected.

## List / view

```bash
ngit pr list                                   # open + draft
ngit pr list --status open,draft,closed,merged,applied
ngit pr view <FULL-hex-event-id | nevent>      # FULL id, not the short prefix
```

In `git branch -r`, proposals show as `<nostr-remote>/pr/feat/<slug>(<short-id>)` — the `(...)` is an ngit annotation; the real ref is `pr/feat/<slug>`. Status `applied` == merged.

## Create

Push a `pr/`-prefixed branch (linear, off current `main`):

```bash
git push -o 'title=My title' -o 'description=line1\n\nline2' -u "$NOSTR_REMOTE" pr/feat/<slug>
```

Advanced (cover letter, labels): `ngit send` — see `ngit send --help`.

## Revise (publish a new version)

The revision must be a **linear series off the current `main`** (a merge commit is the wrong shape).

```bash
# 1. align (gate above), then build the linear series:
git checkout -b <work> main
git cherry-pick <original-pr-tip>      # existing PR commits
git cherry-pick <your-new-commits…>    # yours on top   (or: git rebase main)

# 2. verify it compiles + tests pass; tree == your intended change.

# 3. publish as a new version, linked to the proposal:
ngit send --in-reply-to <proposal-nevent> \
  --subject "<keep or update title>" \
  --description "<what changed>" \
  -d main                              # SINCE_OR_RANGE "main" → commits in main..HEAD
```

`--in-reply-to` threads it under the same proposal on gitworkshop. **No `--force`** once the base equals the nostr `main`. `proposal builds on a commit N ahead of 'origin/main'` ⇒ gate unmet — realign and re-rebase, don't force. Verify: `git ls-remote "$NOSTR_REMOTE" | grep <short-id>` shows `refs/pr/<full-id>/head` at your new tip.

## Merge into main

The merge is **local**; the merged-status event publishes on the subsequent push.

```bash
# 1. ALIGN (mandatory) — all present mains equal.

git checkout main
ngit pr merge <FULL-hex-event-id> -d        # local merge commit; marks proposal "applied"
                                            #   --squash for a squash merge

# 2. amend the generic merge message to something readable:
git commit --amend -F - <<'MSG'
Merge PR: <title>

Merges nostr proposal <short-id> into main:
- <commit summaries>

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG

# 3. sanity-check, then publish (background — slow):
[ -n "$GH_REMOTE" ] && { git merge-base --is-ancestor "$GH_REMOTE/main" HEAD && echo "clean FF push" || echo "github moved; realign"; }
git push "$NOSTR_REMOTE" main
```

Confirm: `ngit pr list --status applied` shows it `applied`, and (if you have it) `git fetch "$GH_REMOTE"` fast-forwards GitHub's main to your merge.

## Cleanup

Delete throwaway branches pushed to the nostr remote (e.g. a `merge/*` used before switching to the proper flow): `git push "$NOSTR_REMOTE" --delete <branch>` (the per-GRASP "non-existent ref" warnings are idempotent fan-out). Delete the local merged branches ngit creates (`pr/feat/<slug>(...)`) and your work branch.

## Failure modes — quick reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| `proposal builds on a commit N ahead of 'origin/main'` | base ≠ stale nostr `main` | realign `main`, rebase series, resend (no `--force`) |
| `! [remote rejected] main … out of sync with nostr` | GitHub main diverged from nostr | maintainer `ngit sync … --force` — **ask the human** |
| push "succeeds" but nothing on gitworkshop | pushed a plain branch, not a proposal | use `pr/`-prefix or `ngit send --in-reply-to` |
| `ngit pr view`/`merge` "failed to parse event id" | used the short prefix | pass the **full** hex id or `nevent` |
| push hangs / times out at 2 min | normal GRASP fan-out latency | run in background; verify via `git ls-remote` |
