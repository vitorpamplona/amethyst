# Coding agents: Claude Code and Codex behind one chat

**Date:** 2026-11-15
**Status:** design, not started
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
| Subagents | `parent_tool_use_id`, `subagents/` files | to check |
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

## Open

- How Codex shows subagents, and whether its approvals carry enough to show a diff before applying.
- Whether to offer Codex's ChatGPT login in the app or leave login to `codex login` (as with Claude).
