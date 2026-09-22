# Contribution Studio — a screen where users build, test and propose Amethyst changes with their own Claude Code

**Date:** 2026-09-22
**Status:** design / queued (no code yet)
**Owning module:** `amethyst/` (UI + routes), with state in `commons/`, protocol in `quartz/`, the runner in `cli/`

## Goal

A first-class screen in Amethyst where a **user** — not the maintainers — can:

1. describe a feature or fix they want in Amethyst,
2. hand it to **their own** Claude Code (their Anthropic account, their machine, their
   git credential),
3. watch it work, see the build/test output, review the diff on the phone,
4. and ship it as a pull request to the Amethyst repo — over **GitHub** or as a
   **NIP-34 proposal** on `nostr://…/relay.ngit.dev/amethyst`,

without the app ever holding an Anthropic key, a GitHub token, or a maintainer-controlled
service in the middle.

## The one hard constraint: Claude Code cannot run on the phone

Claude Code is a Node CLI that wants a real filesystem, `git`, a JDK and a Gradle daemon.
Amethyst's own checks are `./gradlew installPlayDebug`, `installFdroidDebug`,
`:amethyst:installPlayBenchmark` and `./gradlew test` (see `CONTRIBUTING-WITH-AI.md`) —
none of that runs inside an Android app, and shipping a toolchain inside the APK is a
non-starter for both size and Play policy.

So the screen is **a control plane, not an execution plane**. Three planes, with the app
owning only the first:

| Plane | Where | Owns |
|---|---|---|
| **Control + review** | Amethyst on the phone | intake, live transcript, diff review, approval gate, PR link |
| **Execution** | the user's own runner (laptop, home server, VPS) | checkout, Claude Code, Gradle, git push |
| **Merge** | GitHub / gitworkshop.dev | human review, branch protection, merge |

This is exactly the split `cli/plans/2026-07-25-buzz-agent-support-channel.md` landed for
the *team's* agent. This plan generalises it from "the maintainers' one bot" to "every user
brings their own runner", and adds the phone-side onboarding that plan deliberately left out.

## What already exists (survey — reuse first)

The repo is much closer to this than it looks. Almost every piece is built; what is missing
is onboarding and one write surface.

| Capability | Where it lives today | Verdict |
|---|---|---|
| Structured work intake (43001 request / 43002 accept / 43003 progress / 43004 result / 43005 cancel / 43006 error) | `quartz/buzz/jobs`, folded by `commons/…/model/buzz/BuzzJobs.kt` (`BuzzJobAggregator`) | **reuse** |
| Per-channel jobs board with file/upvote/cancel | `amethyst/…/buzz/JobBoardScreen.kt` + `JobBoardViewModel`, `Route.BuzzJobBoard` | **reuse** |
| Workflow runs + **human approval gate** (30620 / 46020 / 46001-46007 / 46010 / 46030 / 46031) | `quartz/buzz/workflow`, `commons/…/model/buzz/WorkflowRuns.kt` (`WorkflowRunAggregator`), `amethyst/…/buzz/WorkflowRunBoardScreen.kt`, `Route.BuzzWorkflowBoard` | **reuse** |
| Write helpers on the account (sign → local echo → publish to the group relay) | `AccountRelayGroupActions.fileBuzzJob` / `triggerBuzzWorkflow` / `approveBuzzWorkflowRun` / `denyBuzzWorkflowRun` | **reuse** |
| Agent authorization without giving it your key (NIP-OA owner attestation) | `quartz/buzz/oaOwnerAttestation`, `AgentAttestationScreen`, `amy buzz attest` | **reuse** |
| Agent config — personas (30175), managed agents (30177), agent profiles (10100) | `quartz/buzz/apPersonas`, `…/managedAgents`, `…/agentProfiles`, `AgentPersonaEditScreen` | **reuse** |
| Cost/telemetry — turn metrics (44200), observer frames (24200) | `quartz/buzz/amTurnMetrics`, `…/aoObserver`, `AgentConsoleScreen` | **reuse** |
| **Live agent transcript**, MLS-encrypted, over our own QUIC stack | `quartz/marmot/appComponents/agentTextStream/*`, `commons/…/marmot/MarmotAgentStreamWatcher.kt`, `marmotQuic/` | **reuse** (this is the "watch it work" pipe) |
| Diff in the room (40008) | `RenderBuzzDiff` in `…/chats/feed/types/RenderBuzzNotes.kt` | **reuse**, upgrade to full-screen |
| Git-over-nostr **read** client: repo announcements (30617/30618), issues (1621), patches (1617), PRs (1618/1619), statuses (1630-1633), GRASP lists (10317), code browser, commit log, file viewer, syntax highlighting | `amethyst/…/gitRepo/*` (`GitRepositoryScreen`, `GitCodeTab`, `GitFileViewer`, `GitCommitLog`, `GitPullRequestChanges`, `GitStatusActions`, `GitNewIssue`), `commonsUI/…/nip34Git/ui/CodeHighlighter.kt`, `quartz/nip34Git/*` | **reuse** |
| The runner, end to end: backlog scheduler, worktree isolation, gated workflow runner, `--exec` wrappers that run Claude Code → commit → push → PR | `amy buzz agent serve` / **`amy buzz agent up`** / **`amy buzz agent doctor`**, `amy buzz workflow run`, `tools/buzz-agent/{agent-exec,workflow-agent,workflow-ship}.sh` | **reuse as-is** |
| Publishing a contribution over nostr from the CLI | `amy git patch` (1617), `amy git pr --commit --clone` (1618), `amy git pr-update` (1619), `amy git status …`, `amy git grasp set` | **reuse** |
| Pairing envelope (kind 24134) | `quartz/buzz/pairing/PairingEvent.kt` — modeled + ingested, **no flow uses it** | **the gap** |
| Creating a patch/PR **from the app** | nothing — the app renders 1617/1618 read-only, and no `Account` helper builds them | **the gap** |

Genuinely new work is therefore small: a hub screen, a pairing handshake, a transcript
viewer, a diff-review screen, and the two publish helpers.

## Architecture

```
  ┌─ Amethyst (phone) ───────────────────┐        ┌─ user's runner (their box) ─────────┐
  │ Contribution Studio                  │        │ amy buzz agent up                   │
  │  • Setup / pair                      │        │  • watches the channel roster       │
  │  • New task            ── 43001 ─────┼───┐    │  • git worktree per run             │
  │  • Live transcript     ◀─ 24200/…    │   │    │  • --exec → claude -p (their key)   │
  │  • Build/test output   ◀─ agentText  │   │    │  • ./gradlew test / installFdroid   │
  │  • Diff review         ◀─ 40008/1617 │   ├────┤  • 46010 gate → waits for a human   │
  │  • Approve / deny      ── 46030/31 ──┼───┘    │  • on grant: push branch + open PR  │
  │  • PR / proposal link  ◀─ 43004      │        └─────────────────────────────────────┘
  └──────────────────────────────────────┘                        │
                    │                                            ▼
                    └── their own relay (amy serve --buzz)   GitHub PR  ·or·  ngit proposal
                        NIP-42 + NIP-OA gated                                 (gitworkshop.dev)
```

Nothing crosses a maintainer-run server. The user's relay is theirs
(`amy serve --buzz --members <npub>` — embedded geode, single JVM process, no Rust/Postgres),
their Anthropic credential never leaves their box, and the phone only ever sees signed
events it can verify.

### Choosing the execution backend

The screen should treat the backend as **pluggable**, because "their Claude Code" means
three different things to three different users:

| Backend | How the phone drives it | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Own runner over their Buzz relay** (`amy buzz agent up`) | 43001 / 46020 events; result + transcript come back as events | fully decentralised, already built end-to-end, works with any agent CLI, F-Droid-clean | user must have a box and run one command | **primary path** |
| **B. GitHub Actions + `claude-code-action`** | phone posts an issue comment (`@claude …`) with the user's own fine-grained token | no box to run; test matrix is CI's | needs a GitHub token in the app, centralised, weakest fit for Amethyst's nature | optional, behind a "I use GitHub" toggle |
| **C. Claude Code on the web / desktop session the user started** | phone doesn't drive it; it only reviews the resulting branch/PR through the existing git screens | zero new plumbing | read-only, no live loop | free — it already works via `GitRepositoryScreen` |

Ship A. Let B be a later adapter behind the same ViewModel interface. C needs nothing.

### Why the gated workflow path, not raw jobs

`amy buzz workflow run` pauses at a 46010 gate **after** the agent commits and **before**
anything is pushed (`workflow-agent.sh` then `workflow-ship.sh`). That is precisely the
review moment a phone is good at: read the diff, read the test output, tap Approve, and only
then does a branch leave the box. Raw 43001 jobs ship in one shot. Use workflows for the
default "propose a change" flow; keep the jobs board for fire-and-forget chores.

Note the documented divergence: on a real Buzz relay the *relay* executes workflows; on
self-hosted geode `amy` is the runner and emits the lifecycle events itself.

## Screen design

One new route, `Route.ContributionStudio`, reachable from the drawer next to
`Route.GitRepositories` (and deep-linked from the Amethyst repo's own
`GitRepositoryScreen` via a "Propose a change with your agent" action). Four sections:

1. **Setup** — one-time. Shows a checklist mirroring `amy buzz agent doctor`: relay reachable
   and NIP-42-authenticating, agent key attested (NIP-OA), repo checkout clean, git credential
   is PR-only, `main` branch-protected. Pairing is a QR/`nostrconnect`-style handshake on
   kind **24134** (`PairingEvent`, already modeled): the runner prints a QR containing relay
   URL + agent npub + a one-time secret; the phone scans it, publishes the pairing reply, and
   stores the binding. Until then the screen shows the exact `amy buzz agent up …` line to
   paste, with the relay URL and the user's npub pre-filled.
2. **Tasks** — the backlog. Reuses `WorkflowRunBoardScreen`'s grouping and
   `WorkflowRunAggregator`, scoped to *my* runs rather than a team channel, with
   "Needs your approval" pinned first. FAB → new task sheet (title, description, target
   flavour, "run tests" toggle).
3. **Run detail** — the heart of it. Live transcript from `MarmotAgentStreamWatcher`
   (collapsible tool-call blocks), a build/test panel fed by 46003 step events, the diff
   (per-file, reusing `CodeHighlighter` + the `UnifiedDiffParser` in
   `quartz/nip34Git/patch/`), and the approve/deny bar publishing 46030/46031.
4. **Ship** — after approval, the runner pushes and the run result carries either a GitHub PR
   URL or the nevent of a 1618 proposal. The proposal case renders inside the app through the
   existing PR screens (`Route.GitRepositoryPulls`), so the whole review loop stays in
   Amethyst. Contributors following `CONTRIBUTING-WITH-AI.md` get the build-log paste-ups
   generated from the step events rather than typed by hand.

## Gaps to build (prioritized)

**P0 — make the loop usable by one motivated user**

- **P0-1 `Route.ContributionStudio` hub + ViewModel.** Assembly only; state folding comes
  from the existing aggregators. ViewModel in `commons/…/viewmodels/`, screen in
  `amethyst/…/ui/screen/loggedIn/`. Size **M**.
- **P0-2 Pairing flow on kind 24134.** Runner side: a `amy buzz agent pair` that prints the
  QR and completes the handshake. Phone side: scan → verify → persist the binding
  (`AccountSettings`). Size **M**. Depends on the QR overhaul only for polish, not
  correctness.
- **P0-3 Run detail screen** — transcript + steps + approve/deny. The watcher and the write
  helpers exist; this is composition. Size **M**.
- **P0-4 Full-screen diff review.** Upgrade `RenderBuzzDiff` into a per-file reviewer on top
  of `UnifiedDiffParser` + `CodeHighlighter`; approve action emits 46030/46031. (This is
  P1-1 in the buzz plan — promoted, because for this audience the diff *is* the product.)
  Size **M**.

**P1 — close the nostr-native loop**

- **P1-1 Patch/PR publish helpers on `Account`** — build + sign 1617 (`GitPatchEvent`) and
  1618 (`GitPullRequestEvent`), mirroring the existing `GitStatusActions` write path, so a
  contribution can be proposed from the phone when the runner has already pushed a tip to a
  GRASP server. Size **S–M**.
- **P1-2 Persona/`respond_to` scoping UI** — the safety gate that keeps the user's agent
  pointed at this repo and this channel only. Size **S–M**.
- **P1-3 Attestation persistence** — `BuzzHeldAttestations` is in-memory; survive restart.
  Size **S–M**.
- **P1-4 Cost visibility** — surface 44200 turn metrics per run in the run detail, so a user
  sees what a task cost them. `AgentConsoleScreen` already reads them fleet-wide. Size **S**.

**P2 — nice to have**

- GitHub-Actions backend adapter (backend B).
- Artifact hand-off: the runner uploads the debug APK to the user's Blossom server
  (`amy blossom`) and the run result links it; the phone offers a download, **not** an
  install (see Policy below).
- Template tasks ("fix this issue" from a 1621 issue or a GitHub issue, "translate these
  strings" via the `find-missing-translations` skill) that pre-fill the task text.
- `desktopApp` parity — the desktop already hosts the same aggregators; the studio is a
  natural sidebar entry there and can drive a *local* runner without a relay hop.

## Security, policy and honesty

- **Blast radius is the git credential, not nostr.** Buzz authorizes by identity, not
  capability: an attested agent key has member-level reach *on the relay* and nothing more.
  What bounds its reach into code is the token handed to the runner. The Setup checklist must
  state this in plain words and verify it (`amy buzz agent doctor` already checks token scope,
  branch protection and a clean tree).
- **No third-party keys in the app.** Backend A never asks for an Anthropic key. Backend B
  would need a GitHub token — keep it opt-in, store it behind the existing privacy lock, and
  say what it can do.
- **The approval gate must be two-signer.** A run must not be able to self-approve; the
  approver key is named in the 46010 gate. For the single-user case the approver is the user's
  own npub on the phone, which is the point — the phone is the second factor.
- **Play policy.** Do **not** install APKs from this screen, and do not ship a "run arbitrary
  shell on your box" free-text field that isn't scoped to the task. Rendering a remote
  transcript and linking a build artifact is fine; becoming an installer or a generic remote
  shell is not. If an in-app preview of the built app is ever wanted, the `:napplet`
  sandboxed WebView host (`nappletHost`) is the only acceptable execution surface, and it
  holds no keys by design.
- **Prompt injection.** The transcript and diff are attacker-influenced text from the moment
  the task mentions an issue written by someone else. Render them as data — no autolinking of
  actions, no "tap to run" affordances derived from transcript content.
- **F-Droid parity.** Everything here is nostr + QUIC + Compose; no Play-only dependency.
  Backend B must not pull in a Google-proprietary client. New third-party deps go through the
  license gate in `CLAUDE.md` first.
- **Expectation setting.** A user's PR still has to pass `CONTRIBUTING.md` and
  `CONTRIBUTING-WITH-AI.md`: research summary on the issue first, both flavours built, tests,
  no protocol changes without the compatibility section. The studio should *show* that
  checklist before the ship step, not hide it — otherwise this feature becomes a firehose of
  unmergeable AI PRs pointed at the maintainers. Consider making the default target the
  user's **own fork** and requiring an explicit second step to aim at `vitorpamplona/amethyst`.

## Test plan

- `commons` unit tests for the new studio state folding (reuse the
  `WorkflowRunAggregatorTest` / `BuzzJobAggregatorTest` shape) — pure, no relay.
- A headless end-to-end script under `cli/tests/buzz/` in the style of `workflow-loop.sh`:
  phone-role client triggers, stubbed agent commits, approver grants, wrapper pushes to a
  local bare repo, result carries the URL — asserting `main` never moves. `agent-exec.sh`'s
  existing 19-case harness already covers the wrapper's failure paths.
- Pairing: a round-trip test for the 24134 handshake including a replayed/expired secret.
- Manual sheet in `amethyst/plans/` once P0 lands, covering the benchmark (R8-minified)
  build — the transcript viewer and aggregators are reflection-adjacent enough to deserve it.

## Open questions

1. **Default target: fork or upstream?** Fork-first is kinder to maintainers; upstream-first
   is what users will expect. Recommend fork-first with a one-tap "open against upstream".
2. **Who hosts the runner for users who have no box?** A "bring your own VPS" doc is honest;
   a maintainer-hosted runner would be a central dependency and is out of character for the
   project. Possibly a paid third-party runner discovered via a NIP-89 handler, with cost
   metered by the existing 44200 telemetry.
3. **Do we ship the jobs path at all here,** or workflows only? Workflows only, for the gate.
4. **Is `amy` a prerequisite users will accept?** It is one command
   (`amy buzz agent up wss://… --repo … --approver npub…`) but it does assume a JVM. A
   packaged one-liner installer would carry most of the onboarding cost.
