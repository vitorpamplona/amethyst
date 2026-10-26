---
name: ngit-pr
description: Opening, revising or merging a PR via `gh` (GitHub) or `ngit` (nostr proposals: `pr/feat/*` branches, gitworkshop.dev, `nostr://` remote). Use when a PR task touches either path; finds remotes by URL, covers the three-mains gate and slow pushes.
---

# Pull requests: GitHub **and** git-over-nostr

This repo can be contributed to **two** ways, via **two kinds** of remote. Both end up in GitHub `main`.
Opening a PR at all is gated by AGENTS.md "Git Workflow" (only when the human asks).

| Remote kind | URL pattern | Role |
|--|--|--|
| **GitHub** | `github.com/vitorpamplona/amethyst` | **Canonical** `main`. Moves constantly (bots merge often). Standard `gh` PR flow. |
| **git-over-nostr** | `nostr://…/relay.ngit.dev/amethyst` | `ngit`. A push fans out to GitHub **and** the GRASP git servers and publishes nostr events. PRs are **proposals** (the `pr/feat/*` branches), reviewed on **gitworkshop.dev**, *not* GitHub PRs. |

## Step 0: identify YOUR remotes (names are not universal)

Remote **names are per-clone**. In the maintainer's checkout the GitHub remote is `upstream` and the nostr remote is `origin`, but yours may differ, and **you may have only one** (e.g. cloned straight from `nostr://…`; pushing it still reaches GitHub via fan-out). Detect by **URL**, never assume a name:

```bash
git remote -v
GH_REMOTE=$(git remote -v    | awk '/github\.com/   {print $1; exit}')   # may be empty
NOSTR_REMOTE=$(git remote -v | awk '/nostr:\/\//     {print $1; exit}')  # may be empty
```

## Which path?

| | **GitHub path** (`gh`) | **nostr path** (`ngit`) |
|--|--|--|
| Needs | a GitHub remote + `gh auth status` | a `nostr://` remote + `ngit` ≥ 2.5.0 |
| PR lives on | GitHub only | nostr + GitHub + GRASP (fans out) |
| Use when | Default. Simplest, no alignment gate. | The PR must be reviewable over nostr (gitworkshop), or you're revising/merging an existing **proposal** (`pr/feat/*`). |

**Default to GitHub** unless the task is specifically about a nostr proposal (a gitworkshop link, a `pr/feat/*` branch, "the PRs on the nostr remote"). One remote decides it for you. Revise/merge a PR on **the same path it was created**.

## The build gate (both paths, every create, revise and merge)

`main` has no branch protection, and the JVM/Android builds accept APIs that break the shared code: `HashMap.putIfAbsent`, `Dispatchers.IO` without `import kotlinx.coroutines.IO`, the stdlib `use` on skia or okio types. Four PRs merged in one day broke `main` this way (#4529, #4536, #4540, #4541), each merged while its CI was red or still running. So, before **every** push, `ngit send` or merge:

```bash
PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push   # the pre-push hook with its opt-in KMP metadata compiles on: every check CI runs that a plain push skips
```

That is the script the git pre-push hook runs, but a plain `git push` leaves the KMP metadata compiles out unless `PREPUSH_KMP_METADATA=1` is set or it pushes to `main`, and `.git/hooks/pre-push` is a copy that only `:amethyst:preBuild` or `./gradlew installGitHook` refreshes: a fresh clone or web container may have none. Run `./gradlew installGitHook` once per checkout, and run the script by hand before `ngit send` and `ngit pr merge`, which publish without a plain `git push` of your branch. On macOS, when `iosMain` changed, also run `./gradlew :commons:compileKotlinIosSimulatorArm64 :commonsUI:compileKotlinIosSimulatorArm64`.

**Merge only on green CI.** `gh pr checks <n> --repo vitorpamplona/amethyst --watch` and wait for every job, `lint` and the iOS jobs included, to pass; the jobs run side by side, so a `lint` failure no longer skips the rest. An ngit merge publishes `main` directly, with no PR checks on GitHub, so the gate above is the only check it gets.

---

# GitHub path (`gh`)

Branch off `main`, `git push -u "$GH_REMOTE" feat/<slug>`, then
`gh pr create --repo vitorpamplona/amethyst --base main --head feat/<slug>`.
Revise by pushing more commits to the same branch; review with `gh pr view <n> --repo vitorpamplona/amethyst --comments`;
the maintainer merges with `gh pr merge <n> --merge` (or `--squash`) once `gh pr checks <n>` is green (the build gate above). GitHub is the source of truth here — no three-mains gate.

---

# nostr path (`ngit`)

**Mental model:** a proposal is a *linear patch series off `main`*, published as nostr events. A "revision" is a new version of that proposal. Merging applies the series to `main` and publishes a merged-status event. There is **no** PR number; `ngit pr merge` makes a plain merge commit (amend it to a readable message).

## The three-mains alignment gate (the thing that breaks everything)

Up to **three** `main` heads drift apart:

- GitHub main (`$GH_REMOTE/main`) — newest, moves every few minutes
- nostr main (`$NOSTR_REMOTE/main`) — **lags**, often far behind
- local `main`

**Every create/revise/merge requires the proposal's base to equal the nostr `main`, and pushing `main` to the nostr remote requires GitHub's main to be an ancestor of what you push.** When misaligned, ngit **rejects pre-flight and publishes nothing** (safe — realign and retry). Don't `--force` past it.

```bash
git fetch --all
echo "github=$([ -n "$GH_REMOTE" ] && git rev-parse "$GH_REMOTE/main")  \
nostr=$(git ls-remote "$NOSTR_REMOTE" -h refs/heads/main | awk '{print $1}')  \
local=$(git rev-parse main)"
# all present heads equal → proceed.
# local behind GitHub?  git merge --ff-only "$GH_REMOTE/main"  (or "$NOSTR_REMOTE/main" if that's all you have)
# nostr behind local?   git push "$NOSTR_REMOTE" main   (clean fast-forward only)
```

With **only** the nostr remote: align local `main` to `$NOSTR_REMOTE/main`; GitHub is handled by fan-out. If GitHub diverged from nostr (`out of sync with nostr` on push), that's a maintainer `ngit sync --ref-name refs/heads/main --force` situation — **stop and ask the human**, never run a forced sync unprompted.

## Pushes are slow — run them in the background

`git push "$NOSTR_REMOTE" …`, `ngit send` and `ngit pr merge` fan out to relays + GRASP servers and **routinely exceed 2 minutes**. Run them as a background command and poll (e.g. `git ls-remote "$NOSTR_REMOTE"` for the expected ref); a foreground call hits the tool timeout even while the push is succeeding.

## Identity

`ngit account whoami` shows the signing key; `ngit send`/`ngit pr merge` sign with **that** key regardless of original author. The maintainer (`_@vitorpamplona.com`) revising/merging a contributor's proposal with their own key is expected.

## List / view

```bash
ngit pr list                                   # open + draft
ngit pr list --status open,draft,closed,merged,applied
ngit pr view <FULL-hex-event-id | nevent>      # FULL id, not the short prefix
```

In `git branch -r`, proposals show as `<nostr-remote>/pr/feat/<slug>(<short-id>)` — the `(...)` is an ngit annotation; the real ref is `pr/feat/<slug>`. Status `applied` == merged.

## Create

Push a `pr/`-prefixed branch (linear, off the aligned `main`):

```bash
git push -o 'title=My title' -o 'description=line1\n\nline2' -u "$NOSTR_REMOTE" pr/feat/<slug>
```

Cover letter, labels: `ngit send --help`.

## Revise (publish a new version)

The revision must be a **linear series off the current `main`** (a merge commit is the wrong shape).

```bash
# 1. align (gate above), then build the linear series:
git checkout -b <work> main
git cherry-pick <original-pr-tip>      # existing PR commits
git cherry-pick <your-new-commits…>    # yours on top   (or: git rebase main)

# 2. the build gate: must pass before you send.
bash .git-hooks/pre-push

# 3. publish as a new version, linked to the proposal:
ngit send --in-reply-to <proposal-nevent> \
  --subject "<keep or update title>" \
  --description "<what changed>" \
  -d main                              # commits in main..HEAD
```

`--in-reply-to` threads it under the same proposal on gitworkshop. **No `--force`** once the base equals the nostr `main`. Verify: `git ls-remote "$NOSTR_REMOTE" | grep <short-id>` shows `refs/pr/<full-id>/head` at your new tip.

## Merge into main

The merge is **local**; the merged-status event publishes on the subsequent push.

```bash
# 1. ALIGN (mandatory) — all present mains equal.
git checkout main
ngit pr merge <FULL-hex-event-id> -d        # local merge commit; marks proposal "applied" (--squash to squash)

# 2. amend the generic merge message to something readable:
git commit --amend -F - <<'MSG'
Merge PR: <title>

Merges nostr proposal <short-id> into main:
- <commit summaries>

<your own attribution trailer, if any>
MSG

# 3. the build gate on the merged tree (no GitHub PR checks run on this path), then sanity-check and publish (background — slow):
bash .git-hooks/pre-push
[ -n "$GH_REMOTE" ] && { git merge-base --is-ancestor "$GH_REMOTE/main" HEAD && echo "clean FF push" || echo "github moved; realign"; }
git push "$NOSTR_REMOTE" main
```

Confirm: `ngit pr list --status applied` shows it, and `git fetch "$GH_REMOTE"` fast-forwards GitHub's main to your merge.

## Cleanup

Delete throwaway branches pushed to the nostr remote with `git push "$NOSTR_REMOTE" --delete <branch>` (per-GRASP "non-existent ref" warnings are harmless fan-out). Delete the local `pr/feat/<slug>(...)` branches ngit creates and your work branch.

## Failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `proposal builds on a commit N ahead of 'origin/main'` | base ≠ stale nostr `main` | realign `main`, rebase series, resend (no `--force`) |
| `! [remote rejected] main … out of sync with nostr` | GitHub main diverged from nostr | maintainer `ngit sync … --force` — **ask the human** |
| push "succeeds" but nothing on gitworkshop | pushed a plain branch, not a proposal | use the `pr/` prefix or `ngit send --in-reply-to` |
| `ngit pr view`/`merge` "failed to parse event id" | used the short prefix | pass the **full** hex id or `nevent` |
| push hangs / times out at 2 min | normal GRASP fan-out latency | run in background; verify via `git ls-remote` |
| `main` CI red after a merge: `lint` fails in `compile…KotlinMetadata` | a JVM-only API in shared code, merged without the build gate | fix forward on a branch; run `PREPUSH_KMP_METADATA=1 bash .git-hooks/pre-push` before the next merge |
| PR CI `lint-android` red with `UnsafeOptInUsageError` | a media3 `@UnstableApi` call without an opt-in; it compiles and passes every test | `@OptIn(UnstableApi::class)` with `import androidx.annotation.OptIn` (Kotlin's `OptIn` does not satisfy lint); the hook's lint step catches it before the push |
