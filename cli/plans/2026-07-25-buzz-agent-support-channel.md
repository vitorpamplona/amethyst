# Buzz-driven agent support channel for Amethyst

**Date:** 2026-07-25
**Status:** prototype landing (CLI) · mobile gaps scoped
**Owning module:** `cli/` (with a shared aggregator in `commons/`)

## Goal

Give the Amethyst team a **shared feature-request channel** where anyone can drive work: the
team debates and files requests, an AI coding agent — Claude Code running as *this* Anthropic
account — **manages the backlog by itself and works items in parallel**, over a self-hosted
[`block/buzz`](https://github.com/block/buzz) workspace. Every request, upvote, and result is
a signed, audited Nostr event the whole room sees. This is **not** a 1:1 chat with the bot.

Interaction model (decided):
- **Anyone in the channel can drive** a work stream — no propose-and-confirm gate; a member's
  job request is auto-accepted and scheduled (**full auto from intake**).
- The bot **owns a stack**: it orders the backlog by the group's upvotes and runs up to N in
  parallel, each isolated in its own git worktree/branch.
- **The only human gate is the merge, and it happens on GitHub** (branch protection + review) —
  never inside Amy or the channel. The agent opens PRs; it can never merge or damage `main`.

### Can this live in Amy? Yes — Amy is the scheduler, the coding agent is `--exec`.

A clean three-way split, no separate project needed for the team-on-a-box case:
- **Amy** owns the Buzz side: watch the backlog, order by upvotes, dispatch up to `--parallel N`,
  isolate each job in a worktree/branch, report status as job events. Reuses everything already
  built (relay client, job models, `BuzzJobAggregator`, the responder, subprocess spawning, the
  long-running `serve` pattern). Decision logic lives in `commons` (pure/testable); git +
  process I/O lives in the `cli` command — so Amy stays a thin assembly layer.
- **`--exec`** is the coding agent (Claude Code via buzz-acp / Goose / a script) Amy spawns per
  job. Not a new project — an existing tool. It runs inside the job's worktree (`BUZZ_BRANCH`,
  `BUZZ_WORKTREE` exported), commits, pushes the branch, opens the PR; its stdout is the result.
- **GitHub** owns review + merge, entirely outside the loop.

Graduate to a separate service only if you outgrow one host (hosted, multi-tenant, a web
dashboard, a cross-machine worker fleet) — and even then Amy/`quartz`/`commons` stay the library
underneath.

## Why Buzz is the right substrate (and what it is NOT)

Buzz is a self-hosted Nostr relay that acts as a workspace where humans and agents share
rooms; Amethyst already models ~78 of its kinds (`quartz/.../buzz/`) plus client UI (agent
console, workspaces, DMs, attestations — shipped in v1.13.0). Upstream, Buzz ships
`buzz-acp`, an ACP harness that already plugs **Claude Code** (and Goose/Codex) in as the
agent runner, and produces code as **NIP-34 patches / git diffs / PRs** — the same flow
this repo's `claude/*` branches already use.

What already exists in-repo to build on:

| Layer | Status |
|---|---|
| Workspace = a relay you own; channels/threads/canvas | app + `amy buzz post/read` |
| DMs to an agent key (open/hide/add-member/list) | app + `amy buzz dm …` |
| Agent authorization — NIP-OA owner attestation (virtual membership) | `AgentAttestationScreen` + `amy buzz attest` |
| Agent config — personas (30175), managed agents (30177), agent profiles (10100) | quartz models + persona editor |
| Cost/activity telemetry — turn metrics (44200), observer (24200) | Agent Console + `amy buzz console` |
| Code changes in the room — diff (40008), NIP-34 patches | rendered in chat |
| Human-in-the-loop gate — workflow approval (46010/46030/46031) | quartz models only, no UI |
| Structured jobs — 43001-43006 | quartz models + EventFactory dispatch; **no client surface (this plan)** |

### The permission reality — the crux

Buzz authorizes **by identity, not by capability flags**. Its entire vocabulary is coarse:
membership + `owner`/`admin`/`member` roles, NIP-OA conditions limited to a single `kind`
and `created_at` before/after bounds, and per-agent `respond_to` / `channel_add_policy`
gates. **There is no way in Buzz to express "may push but not merge" or "only this repo."**
So the constraints the task asks for live in **three layers**, and Buzz is only one:

| Requirement | Enforced by | How |
|---|---|---|
| Can't merge/destroy `main` | **GitHub branch protection** (load-bearing) | Protect `main` (PR + review + green CI, no direct/force push, no branch delete). The agent runner's git credential can only open PRs on feature branches — never merge. |
| Can't use the agent to code other things | **Agent runtime + Buzz intake** | `--exec` checked out in `amethyst` only, scoped tools; persona system-prompt scopes the task; `--accept-from` allowlist = team npubs only. |
| Only the team can drive it | **Buzz** | Team npubs = relay members / the `--accept-from` allowlist. |
| Everything accountable | **Buzz** | Every request/progress/result is a signed event in the tenant's hash-chained audit log. |
| Human sign-off before risky actions | **Buzz workflow gate** | 46010 pause → 46030/46031 grant/deny by a designated approver key (two-signer; a run can't self-approve). |

Honest blast radius: a Buzz-authorized agent key has member-level reach *on the relay*
only. Its reach into **code** is bounded entirely by the git credential handed to `--exec`.
Keep that credential minimal; branch protection is what actually stops a bad merge.

## Architecture (MVP)

1. **The workspace relay.** For the agent job channel you have two options:
   - **`amy serve --buzz --members <npubs>`** (recommended to start) — a private, agent-authorized
     workspace on a single JVM process via **`BuzzMembershipPolicy`** (quartz): NIP-42 required,
     only members + NIP-OA-attested agents may read/write. No Rust, no Postgres/Redis/MinIO. The
     job board + scheduler run on this today. It does NOT emit relay-signed NIP-29 metadata
     (39000-39003) or run workflows — the job channel doesn't need them.
   - **Block's Rust `buzz-relay`** — only if you want the full in-app Buzz *workspace/DM* UI
     (relay-signed rosters, relay-assigned DM UUIDs) or server-run workflows. Heavier stack.
2. **One agent identity** = its own nostr key, authorized by a NIP-OA attestation the owner
   issues (`amy buzz attest` / `AgentAttestationScreen`). On GitHub it authenticates with a
   PR-only token; `main` is branch-protected.
3. **Intake:** a team member files a job in the `#build` channel (or DMs the agent). The
   responder picks it up, runs a coding agent in an `amethyst` checkout, streams progress,
   posts the result, and opens a PR on a `claude/*` branch. **Merge stays human.**
4. Optional **approval gate** (46010/46030/46031) for irreversible mid-run steps.

## CLI prototype (this change)

Thin assembly over quartz job models + a shared aggregator; no protocol logic in `cli/`.

- **`commons/.../model/buzz/BuzzJobs.kt`** — `BuzzJobAggregator`, a pure, tested
  (`BuzzJobAggregatorTest`, 9 cases) folder that correlates 43001-43006 events (by the
  reply `e` → request id) into `JobView` records with a `JobState` machine
  (REQUESTED→ACCEPTED→IN_PROGRESS→COMPLETED/FAILED/CANCELLED; newest terminal wins). Shared
  so a future mobile Jobs board reuses one correlation path.
- **`amy buzz job request|list|show|cancel`** (`BuzzJobCommands.kt`) — the requester side:
  file a 43001 (optional `--agent`, `--channel`), list/fold jobs (`--mine`/`--assigned`),
  show one job's lifecycle, cancel (43005).
- **`amy buzz agent serve RELAY --exec CMD`** (`BuzzAgentCommands.kt`) — the **backlog
  scheduler**. Watches a channel's REQUESTED jobs, orders them by `BuzzJobAggregator.byPriority`
  (upvotes desc, oldest-first tiebreak), and runs up to `--parallel N` at once — each in its own
  `git worktree` + branch (`--worktree REPODIR`, off `--base-ref`, named `<branch-prefix><jobid>`)
  so concurrent runs never collide (`--parallel > 1` requires `--worktree`; worktree add/remove
  is mutex-serialized, the agent work runs concurrently). Per job: 43002 accept → 43003 progress
  → `sh -c CMD` inside the worktree (task text on stdin; `BUZZ_JOB_ID/REQUESTER/CHANNEL/RELAY/
  AGENT/UPVOTES/BRANCH/WORKTREE/BASE_REF` in env) → 43004 result or 43006 error. Intake gate:
  `--accept-from` (explicit npubs) and/or `--accept-from-channel` (the channel's kind-39002
  member roster — "anyone in the channel drives"). `--dry-run`, `--once`, `--claim-untargeted`,
  `--exec-timeout` for testing/ops. This is where Claude Code plugs in: `--exec` runs the agent,
  which opens the PR and echoes the URL as the result.
- **Upvote priority** (`BuzzJobs.kt`): `BuzzJobAggregator` folds kind-7 likes (distinct reactors,
  dislikes excluded) targeting a job into `JobView.upvotes`; `byPriority` orders the backlog. The
  group reprioritizes the stack just by reacting.

Guardrails restated in the command's KDoc: `--accept-from` / `--accept-from-channel` is the
Buzz-layer intake gate; repo blast radius is the `--exec` credential (PR-only) + branch
protection, not Buzz. Merge is never done here — only on GitHub.

### Schema caveat

Kinds 43001-43006 are *reserved* in Buzz with no upstream builder; the tag layout
(`e`/`h`/`p`/`status`) is Quartz's best-effort model and must be reconciled once Buzz
implements the job protocol. The prototype is deliberately isolated so that reconciliation
touches only the quartz models + this aggregator.

## Mobile app — placement evaluation

The existing agent screens are **owner-global concepts entered per-relay, and buried**:
`AgentConsole(relayUrl)` (Costs/Personas/Observer, read-only telemetry) is only reachable via
a footer in the channel list or a bot-member tap; Costs/Personas/Observer are really the
owner's whole fleet, not one relay's. That's a discoverability + scoping smell, but the Console
is a coherent *owner telemetry* surface and should stay that — just get a better entry later.

The **shared work surface is a different thing and belongs at the channel level.** A Buzz job is
`h`-scoped to a channel, so the backlog is *per-channel* — exactly like the Canvas (40100) and
Forum, which launch from `RelayGroupTopBar` gated by `BuzzRelayDialect.isBuzz`. So the Jobs
board sits there too (a `Checklist` action → `Route.BuzzJobBoard(channelId, relayUrl)`), NOT
inside the owner Console. Keeping "owner fleet telemetry" and "this channel's shared backlog"
as separate surfaces is the right call.

Note the model change also **deprioritizes the workflow-approval inbox (46010/46030/46031)**: with
full-auto intake and merge-on-GitHub, the human gate moved to the PR — so the approvals inbox is
now P1/optional, not P0. The true P0 is the shared board.

## Mobile app gaps (prioritized)

The quartz layer + LocalCache ingest are complete for every kind; the app has **zero
create/interact surface** for the two kinds that define the workflow. Priorities:

**P0 — the shared work surface**
- **P0-2 Jobs board — ✅ LANDED.** `JobBoardScreen` + `JobBoardViewModel` (per-channel,
  `Route.BuzzJobBoard(channelId, relayUrl)`, entered from `RelayGroupTopBar` on Buzz relays).
  Reads job kinds + kind-7 upvotes scoped to the channel `h`, folds via `BuzzJobAggregator`,
  groups by state (In progress / Queued-by-upvotes / Done / Closed), live via `subscribeAsFlow`.
  Three write actions through new `Account` helpers: **file** a task (43001, FAB → dialog),
  **upvote** (kind-7 `+` with `h`), **cancel** own job (43005). Merge stays on GitHub.
- **P0-1 Approvals inbox** — deprioritized to P1 by the full-auto/merge-on-GitHub model (the
  human gate is now the PR, not a 46010 gate). Still worth it if a workflow-gate flow returns:
  render 46010, publish 46030/46031, token-hash correlation, push-urgent.
- **P0-3 Agent picker** — the board files **untargeted** jobs (any channel agent claims them),
  so a picker isn't needed for the shared-channel model; revisit only for directed jobs.

**P1 — a credible agent-driving client**
- **P1-1 Diff/PR review surface** — upgrade read-only 40008 (`RenderBuzzDiff`) into a
  full-screen per-file review whose approve action emits 46030/46031. Size **M**.
- **P1-2 Managed-agent (30177) editor** — clone `AgentPersonaEditScreen`. Size **M**.
- **P1-3 Persona `respond_to`/allowlist editing** — the safety gate for pointing a persona
  at a support channel. Size **S–M**.
- **P1-4 Attestation persistence** — `BuzzHeldAttestations` is in-memory; survive restart.
  Size **S–M**.

**P2 — completeness**: agent-profile (10100) viewer; a stable "Agents" hub;
workflow-run timeline (46020 family, all stored, unrendered); turn-metric → job attribution.

Key files: routes `amethyst/.../navigation/routes/Routes.kt`; render dispatch
`.../chats/feed/ChatMessageCompose.kt`; renderers `.../chats/feed/types/RenderBuzzNotes.kt`;
ingest `model/LocalCache.kt` (~L4780-4855); subscription
`.../relayGroup/datasource/RelayGroupFilterBuilders.kt`.

## Pivot — jobs → workflows (2026-07-26)

The 43001-43006 job prototype above proved the *shape* (drive an agent from a shared channel,
worktree-isolate, PR-only, merge-on-GitHub), but those kinds are **reserved/speculative** with no
upstream builder. Buzz's **real, source-confirmed** structured-work primitive is the **workflow**
family — the command kinds are pinned against buzz-relay's Rust `command_executor.rs`:

- **30620** workflow definition, **46020** trigger, **46001-46007** run/step lifecycle,
- **46010** approval-requested gate, **46030 / 46031** grant / deny.

So the driving surface switched to workflows. What that buys over jobs: a **first-class
human-approval gate** (46010 → 46030/46031) baked into the protocol — the exact "anyone in the
channel can drive, but a human gates the merge" model the goal asks for — rather than relying on
GitHub branch-protection alone.

**Divergence (documented):** on a real Buzz relay the *relay* parses the workflow YAML and executes
it, signing the lifecycle + approval events. Self-hosted on geode there is no workflow engine, so
**`amy` is the runner** (`amy buzz workflow run`) and emits the lifecycle events itself. The command
events (30620/46020/46030/46031) stay faithful to Buzz; only the lifecycle *content* shape is
Amethyst's (Buzz leaves it relay-defined).

**Correlation:** the **run id is the trigger's event id and doubles as the approval token**, so a
grant's `d` tag equals the run id — no separate token bookkeeping. Two store realities shaped the
wire handling, both verified against geode:
- quartz's `SQLiteEventStore` routes every `#d` filter to the addressable `d_tag` column (NULL for a
  regular kind like 46030), so **decisions are fetched by author** — every 46010 gate names its
  approver in a `p` tag — and matched to their run by the token the aggregator reads off the event.
- The runner is **restart-safe**: runs still at the gate (AWAITING_APPROVAL / APPROVED / DENIED) are
  rebuilt into the in-flight map from the run id on startup (worktree path + branch are
  deterministic), so a decision arriving in a later poll — or a fresh `--once` process — still
  resolves. The relay is the source of truth, not the in-memory map.

**Landed (CLI + commons):**
- `commons/.../model/buzz/WorkflowRuns.kt` — `WorkflowRunAggregator` folds trigger + lifecycle +
  grant/deny into per-run state (`WorkflowRunAggregatorTest`, 8 cases).
- `cli/.../commands/BuzzWorkflowCommands.kt` — `trigger` / `list` / `show` / `approve` / `deny` and
  the **`run`** runner (agent work → 46010 gate → on grant runs `--on-approve` → 46005 completed; a
  deny discards the worktree, run is DENIED). Wired into `amy buzz workflow`.
- `cli/tests/buzz/workflow-loop.sh` — end-to-end headless harness (alice triggers, bot runs,
  carol approves/denies) through embedded geode; 14/14 green, including the deny path and
  worktree cleanup.

**Landed (Android app):**
- `WorkflowRunBoardScreen` + `WorkflowRunBoardViewModel` (per channel, `Route.BuzzWorkflowBoard`,
  entered from `RelayGroupTopBar` on Buzz relays). Folds the workflow kinds via
  `WorkflowRunAggregator`, groups runs by state with **"Needs your approval" pinned first**, and the
  named approver grants/denies a paused run inline (46030/46031). Merge stays on GitHub.
- `Account.triggerBuzzWorkflow` / `approveBuzzWorkflowRun` / `denyBuzzWorkflowRun` (same
  sign → local-echo → publish-to-group-relay contract as the job helpers).
- `RelayGroupFilterBuilders` subscribes the `#h`-scoped workflow kinds; the board fetches the
  `d`-only grant/deny decisions **by author** (the CLI's approach).
- `NotificationFeedFilter` — a 46010 gate addressed to me notifies and is **push-eligible** (added
  to `NOTIFICATION_KINDS` + an `acceptableEvent` early-return gating on `approver() == me`).
- Backbone reused as-is: quartz `EventFactory` already registers the 46xxx kinds and `LocalCache`
  already ingests them (store-only), so no protocol/ingest changes were needed.

So the workflow **run board + approval gate is the P0-1 approvals surface** the mobile section below
anticipated. The jobs board/code stays for now, but the workflow path is the one matching Buzz
upstream.

## Follow-ups

1. Reconcile 43001-43006 with Buzz upstream once it defines the job protocol (or retire the job
   path in favor of workflows).
2. Wire the P0 mobile screens (approvals inbox + jobs board) on top of `BuzzJobAggregator` /
   `WorkflowRunAggregator`.
3. ✅ **Done** — a reference `--exec` wrapper (`tools/buzz-agent/agent-exec.sh` + README) runs
   the coding agent in the job worktree, commits, pushes the feature branch, opens a PR with a
   PR-only token, and prints the URL as the job result — with the branch-protection + token-scope
   checklist documented. Verified end-to-end against a stubbed `gh`/agent.
4. Consider promoting the approval gate (46010) into the responder for irreversible steps.
