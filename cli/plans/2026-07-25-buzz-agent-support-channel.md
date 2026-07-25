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

1. **One `buzz-relay`** (Block's Rust relay — geode does NOT implement Buzz server
   semantics: kind accept-list, `h`-scope, NIP-OA fallback, relay-signed metadata) = the
   "Amethyst workspace" tenant. Team npubs enrolled as members; a maintainer is owner.
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

## Mobile app gaps (prioritized)

The quartz layer + LocalCache ingest are complete for every kind; the app has **zero
create/interact surface** for the two kinds that define the workflow. Priorities:

**P0 — the human-in-the-loop loop**
- **P0-1 Approvals inbox** — render 46010, publish 46030/46031 grant/deny (token-hash
  correlation; 46010 is NIP-PL push-urgent). Build on `AgentConsoleViewModel` fetch pattern
  + `ApprovalGrantEvent.build`/`ApprovalDenyEvent.build`. Size **M**.
- **P0-2 Jobs board** — create 43001, watch 43002-43006, see result, grouped by `e`. Reuse
  the new `BuzzJobAggregator`; jobs already render as inline system rows + are subscribed in
  `RelayGroupFilterBuilders`. Size **L**.
- **P0-3 Agent picker** — choose a target agent when filing a job/approval, from stored
  30177/10100 + the fleet list. Size **S**.

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

## Follow-ups

1. Reconcile 43001-43006 with Buzz upstream once it defines the job protocol.
2. Wire the P0 mobile screens (approvals inbox + jobs board) on top of `BuzzJobAggregator`.
3. A reference `--exec` wrapper that runs Claude Code, opens a PR with a PR-only token, and
   returns the PR URL — plus a documented branch-protection + token-scope checklist.
4. Consider promoting the approval gate (46010) into the responder for irreversible steps.
