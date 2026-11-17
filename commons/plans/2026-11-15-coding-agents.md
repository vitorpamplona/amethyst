# Coding agents: Claude Code and Codex behind one chat

**Date:** 2026-11-15
**Status:** steps 1–4 done (Codex turns not yet seen succeed: no API credit where it was built)
**Owning modules:** `commons` (agent interface, chats), `claudeCodeClient` (Claude backend), a new
`codexClient` (Codex backend), `commonsUI` (screens)

## Goal

The Claude Code screens (sessions, chat, changes, propose, settings assistant) work with OpenAI's
Codex too, without asking users to install Node or an adapter. Each agent is the CLI the user
already installed, driven over its own protocol.

## Decision: no ACP

ACP's Claude and Codex adapters are Node programs (`@agentclientprotocol/claude-agent-acp`,
`@agentclientprotocol/codex-acp`), and writing our own ACP layer would put a translation between
two pieces of the same app. Both CLIs already speak a structured protocol on stdio; the app speaks
each directly and turns both into one model of its own. A native-ACP backend (Goose, `acp-jvm`)
can be added later on the same interface if wanted.

## The two protocols

| | Claude Code | Codex |
|---|---|---|
| Process | `claude --print --input-format stream-json --output-format stream-json …` | `codex app-server` (JSON-RPC 2.0 lines on stdio) |
| Contract | Undocumented (the Agent SDK's wire format) | Versioned; schema from `codex app-server generate-json-schema` (v2) |
| Install without Node | Native installer, Homebrew | Native binary (Homebrew, GitHub releases); the npm package is a launcher around it |
| License | Proprietary CLI, not bundled | Apache-2.0, not bundled either |
| Start / resume / fork | `--resume`, `--fork-session` | `thread/start`, `thread/resume`, `thread/fork` |
| Fork before a message | `--resume-session-at <parent uuid>` | `thread/fork` with `lastTurnId` (inclusive) |
| Send | user line (text and image blocks) | `turn/start` with `input` (text, image url/file) |
| Stop | `interrupt` | `turn/interrupt` |
| Approvals | `can_use_tool` control request | `item/commandExecution/requestApproval`, `item/fileChange/requestApproval`, `item/permissions/requestApproval` |
| Models | `initialize` answer, `set_model` | `model/list`, `model` on `turn/start` |
| Commands / skills | `initialize` answer (`commands`) | `skills/list` |
| Rename | `rename_session` | `thread/name/set` |
| List / read history | our reader of `~/.claude/projects` | `thread/list` (cwd, searchTerm), `thread/read` with turns |
| Streaming | `stream_event` deltas | `item/agentMessage/delta`, `item/*/outputDelta` |
| Tool calls | `tool_use` blocks by tool name | items: `commandExecution`, `fileChange`, `mcpToolCall`, `webSearch`, … |
| In-app tools | in-process MCP over the pipe (`mcp_message`) | `item/tool/call` server request (dynamic tools) |
| System prompt | `--append-system-prompt` | `developerInstructions` |
| Subagents | `parent_tool_use_id`, `subagents/` files | not shown yet (`collabAgentToolCall` items show as an Agent call) |
| Login | the user's own `claude` login | `account/read`, `account/login/start`; `requiresOpenaiAuth` |

Checked so far: `codex app-server` 0.169.0 answers `initialize`, `model/list`, `skills/list` and
`thread/list` with no account; turns need one.

## Shape

```
commons/agents/
  CodingAgent         which agent: find its program, list and read sessions, start a connection
  AgentConnection     send(text, images), interrupt, respond(approval), setModel, setMode, rename
  AgentEvent          SessionStarted, TextDelta, Message, ToolStarted, ToolOutput, ToolFinished,
                      ApprovalAsked, TurnEnded, Activity, SubagentEvent
  AgentCapabilities   editFromMessage, subagents, inAppTools, modes, rename, images
  AgentTool           what a tool call is, agent-neutral: Command, FileChange, Read, Search, Web,
                      Agent, Raw (the ToolView kinds the screens already draw)
claudeCodeClient      the Claude backend (today's code, emitting AgentEvents)
codexClient           the Codex backend (JSON-RPC client, item → AgentEvent)
```

`ClaudeChat` becomes `AgentChat` over an `AgentConnection`; `ChatTranscript`, the rows, approval
card, changes, propose and the NIP-34 flow stay as they are, reading `AgentEvent`s. Screens hide what
a backend's capabilities leave out. Strings say "the agent" or the agent's name, not "Claude".
The session list groups both agents' sessions by folder, with the agent on each row; New session
picks the agent (only installed ones show).

## Steps

1. Agent interface in `commons`; Claude behind it; all Claude tests still pass, the app unchanged.
2. `codexClient`: JSON-RPC client, typed messages for the subset used, fixtures recorded from a
   real `codex app-server`; tests like `claudeCodeClient`'s.
3. Codex backend: sessions, chat, approvals, models, images, rename, fork from a message.
4. Screens: agent on rows, agent picker, capability gating, wording.
5. Settings assistant on Codex (`item/tool/call`), if wanted.

## Testing

Each client replays recorded output in unit tests. Live tests are gated by env vars
(`CLAUDE_CODE_LIVE=1`, `CODEX_LIVE=1`) and spend a little of the user's account. Codex turns need
an OpenAI login or API key in the environment that records the fixtures.

## As built

- `agentCore` holds the shared vocabulary and `AgentBackend` / `AgentConnection` / `AgentEvent`;
  `claudeCodeClient` and `codexClient` each implement them and depend on nothing else of the app.
- The service lists every agent's sessions (Claude's from its files even without `claude`; Codex's
  through a short-lived `codex app-server`), rows carry the agent, New session and the nostr start
  screen pick one, and a chat names its agent ("Message Codex", "Codex wants to run a command").
- Codex: approvals are `accept` / `acceptForSession` / `decline` / `cancel`; file changes show as
  `ApplyPatch` diffs; fork before a message is `thread/fork` through the turn before it; slash
  commands, subagents, live-session detection and the settings assistant stay Claude-only.
- Checked in the app: a Codex chat started, its model list showed, a message went out and the
  turn's failure (no credit) showed in Codex's words; the list showed it as Codex. Claude chats
  unchanged.

## Workspaces: many sessions, one group of repositories

For running many sessions (15+) on the same GitHub repositories at once, each one is a **workstream**
with clones of its own (see "Workstreams" below for how one starts).

- `AgentWorkspaces` keeps, under `~/.amethyst/agents`: `mirrors/` (a bare mirror per repository,
  fetched before each new workspace, one fetch at a time per mirror), `workstreams/<name>/<repo>/`
  (each session's clones) and `meta/` (what each workspace holds, outside the agent's folder).
- A clone from the local mirror hardlinks the mirror's packs (seconds, almost no disk) yet is a
  whole repository: config, hooks, refs and stashes of its own, unlike a git worktree, whose
  `.git` is shared and whose branches can't be checked out twice. Its `origin` is the real
  remote; each repository starts a new `agent/<time>` branch from its default branch.
- The agent can't push or open pull requests (`git push`, `gh pr create`, `gh pr merge` are blocked
  commands, and pushes from its process tree are disabled through git's settings). The user opens
  them on the session's Pull requests screen: per repository, what the branch has to propose, a
  title and description from its commits, "Push and open pull request" (the user's git push and
  their `gh` login), then the PR's state and link. "Delete workspace" removes the clones; the
  mirrors stay.
- Checked in the app with local bare repositories standing in for GitHub: the workspace cloned
  both, the agent committed and was refused the push, the user's push reached the remote, `gh`'s
  refusal (not a GitHub remote here) showed in its own words, and deleting left the mirrors.
- The session list has a Workspaces section: each row shows its repositories (`owner/name`) and
  a pull request badge ("2 PRs open", "Merged") read from the workspace's record. The record
  learns a pull request's state when the Pull requests screen loads, and every ten minutes in the
  background for branches that reached the remote. A row's menu deletes the workspace. A
  workspace whose first message was never sent is still listed, and opens a chat in it with the
  agent it was made for.
- A mirror is fetched with git's automatic maintenance off and repacked in step, under its lock,
  and clones are made under the same lock: a detached maintenance run would otherwise rewrite the
  objects a parallel session is cloning (seen as a failed clone in the concurrency test).
- New session remembers the last agent; repositories are rows (owner/name plus remove), with the
  last-used list and named groups as chips. Each repository card on the Pull requests screen has
  its own Changes view; questions asked from it name the file by its path inside the workspace.

### Picking repositories, proposals and forks

- **Picker:** New workstream has one searchable list with checkboxes (`RepoCatalog`), from:
  - the mirrors on this computer, most cloned first (`usage.json` counts each clone and outlives
    the workspace);
  - the user's GitHub repositories (`gh repo list`);
  - the NIP-34 repositories in `LocalCache`, through a clone URL git can fetch alone (`https` first;
    never `nostr://` or `htree://`, which need a helper);
  - GitHub's search (`gh search repos`), last, since it is the widest.

  Enter adds a pasted URL, or checks the first match of a word.
- **Proposals:** a cloned URL that a cached NIP-34 announcement lists gets its branch linked to
  that repository (`GitWorkspace.link`, as the repository screen's checkouts do). Its card offers
  "Send proposal", the Propose screen for that folder of the workspace.
- **Forks:** before a pull request, `gh` says whether the user may push to the repository
  (`viewerPermission`). Without write access the app runs `gh repo fork`, pushes the branch to a
  `fork` remote, and opens the PR with `--repo upstream --head user:branch`. The PR's state is
  looked up on the upstream by branch, so it works whether or not a fork was used.

### Workstreams: pick the repositories, name it, go

Starting work had too many choices (agent, folder or clones, worktree, permission mode). The
default is now a workstream, and New workstream asks for two things:

- **Repositories**, the picker above, with the last workstream's set already chosen.
- **A name.** It names the folder (`workstreams/<name>`) and every repository's branch (`agent/<name>`).
  Focus starts there, so a repeat workstream is a name and Enter. A name already taken, or once used by a
  removed workstream (`meta/<name>.gone`), gets `-2`: Claude files conversations by folder, so a reused
  folder would show a removed workstream's archived conversations. A branch the repository already has
  gets `-2` too, so the user's push never meets an old one.

Go opens the chat at once. The clones are made behind it ("Cloning 2 repositories…"), a message
sent meanwhile shows and goes out once they are in, and a failed clone ends the chat with git's
words and leaves no folder. The agent runs hands-off (`workstreamModeOf`): Claude in Auto; Codex, which
has no Auto, in accept-edits, free inside its sandbox and asking only to leave it. The chat's mode
menu still switches one workstream to asking first. The agent is told the workstream's layout (its folder holds
one clone per repository and is not a repository itself, each clone's branch, and that pushes and
pull requests are the user's), since otherwise its first `git` runs in the workstream's folder and fails.

Everything else sits behind a faint tune icon in the dialog's corner (Advanced): fresh clones or
a folder already on the computer (with its worktree switch), the agent, and the permission mode.
The agent picked there is remembered for the next simple workstream.

Checked in the app (1600×1000 and 480×900): a workstream on a local repository opened at once in Auto,
listed under Workstreams and scrolled into view; the agent answered `git branch --show-current` with
`agent/<name>` from inside the clone, without asking.

### Disk: archive, clean up, tidy

A conversation is small (a JSONL file, a Codex thread); a workspace is whole clones. So space comes
back by removing workspaces, and conversations are archived, not deleted.

- **Archive:** a row's menu archives and unarchives a session. Codex's own archive is used
  (`thread/archive`, `thread/unarchive`; the list asks `thread/list` for both); Claude keeps none,
  so the app keeps the archived ids in `preferences.json`. Archived rows sit with the ones whose
  folder is gone, under Archived.
- **Finished:** a workspace is finished when every repository's pull request is merged or closed;
  the record keeps when each first closed (`closedAt`), so later checks don't move the date.
- **Sizes:** each workspace row shows what its clones take. Files hardlinked with the mirror are
  left out (removing the workspace frees none of them); where the file system can't tell
  (Windows), all count. Measured in the background, at most every ten minutes.
- **Storage dialog** (the list's top bar): totals for workspaces and mirrors; the finished
  workspaces with their sizes and "Clean up N · frees X"; "Tidy mirrors". A workspace with files
  not committed or commits on no remote is never removed, by hand or on its own, and says why.
- **On its own** (on by default, a toggle in the dialog): a day after a workspace is finished its
  clones go, and mirrors no workspace used for 60 days go too. At most once an hour.
- **Tidy mirrors:** `git gc --prune=now` on each mirror under its lock, and removes unused ones.
  After a repack the clones' old packs are no longer shared, so their sizes grow to what they
  really hold.
- Checked in the app (1600×1000 and 480×900): a workspace finished three days ago was removed at
  start; one with unpushed commits stayed, marked "Kept: work not pushed"; Clean up removed a
  finished one and said what it freed; Tidy ran; a Codex thread archived and unarchived through
  Codex, a Claude session through the app.

## Open

- A pull request opened outside the app (the user's own `gh pr create`) shows once its branch is
  on the remote; one opened from a branch renamed by hand does not.

- How Codex shows subagents, and whether its approvals carry enough to show a diff before applying.
- Whether to offer Codex's ChatGPT login in the app or leave login to `codex login` (as with Claude).
