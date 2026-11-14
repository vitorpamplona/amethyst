# claudeCodeClient

An **unofficial** Kotlin/JVM client for the Claude Code CLI. It is not an Anthropic product and
bundles nothing from Anthropic: it starts the `claude` program the user installed and talks to it
over stdin/stdout, and it reads the session files that program writes.

It has no Amethyst or Nostr dependencies (kotlinx.coroutines and kotlinx.serialization only), so
it can move to its own repository once something outside Amethyst needs it.

## What it does

| Need | Where |
|---|---|
| Start a session or continue one (`--resume`, `--fork-session`, cut at a message with `--resume-session-at`), pick model and permission mode, limit tools, add a system prompt | `process/ClaudeCodeLauncher` |
| Send prompts (with images), read every message in order, answer permission prompts, interrupt, change mode or model, rename | `process/ClaudeCodeConnection` |
| The slash commands and models the CLI offers (its answer to `initialize`) | `protocol/CliInfo` |
| Read an image for sending: format by its bytes, shrunk when the API would refuse it | `protocol/ImageInput` |
| Tools the host runs itself, offered to the session over its own pipe (in-process MCP) | `mcp/SdkMcpServer` |
| The messages and content blocks, parsed leniently (unknown types kept as raw JSON) | `protocol/` |
| List saved sessions with titles, folder and branch; read a session's history and its subagents'; rename; delete | `session/ClaudeSessionStore` |
| Which sessions another Claude Code (a terminal) has open now | `ClaudeSessionStore.liveSessions()` |
| Find `claude` when a desktop app's PATH is short | `process/ClaudeExecutable` |

## The protocol, as observed

Claude Code runs as `claude --print --input-format stream-json --output-format stream-json
--verbose --permission-prompt-tool stdio`. Each line both ways is one JSON object.

- Host to CLI: `{"type":"user","message":{"role":"user","content":"…"}}` for a prompt, and
  `{"type":"control_request","request_id":…,"request":{"subtype":…}}` for `initialize`,
  `interrupt`, `set_permission_mode`, `set_model`, `rename_session`.
- CLI to host: `system` (`init` first), `assistant`, `user` (tool results), `stream_event` (with
  `--include-partial-messages`), `result` at the end of each turn, `control_response` to the
  host's requests, and `control_request` of subtype `can_use_tool` when a tool call needs the
  user. The host answers that with a `control_response` whose `response` is
  `{"behavior":"allow","updatedInput":…}` or `{"behavior":"deny","message":…}`.
- Without `--permission-prompt-tool stdio` the CLI never asks: anything that would prompt is
  denied.
- A prompt with images sends `content` as blocks: `{"type":"image","source":{"type":"base64",
  "media_type":…,"data":…}}` first, then `{"type":"text",…}`. A prompt that starts with `/` runs
  that slash command; its output comes back as an ordinary reply.
- The `initialize` answer lists `commands` (name, description, argumentHint, aliases) and
  `models` (value, displayName, description, resolvedModel). Starting the CLI and sending only
  `initialize` saves no session, so a host can ask before the first message.
- In-process tools: list the servers in `initialize` (`sdkMcpServers`) and in
  `--mcp-config` (`{"type":"sdk"}`); the CLI then sends `control_request` `mcp_message` with a
  JSON-RPC message, answered with `{"mcp_response": …}`. Tools are named `mcp__<server>__<tool>`.
- A subagent's messages carry `parent_tool_use_id`, the id of the Agent call that started it.
  Subagents run in the background: the call returns at once, `system` messages `task_started`,
  `task_progress` and `task_notification` (with `tool_use_id`) follow, and the CLI starts a new
  turn on its own (a new `init`) when one reports back.

Sessions live in `~/.claude/projects/<folder>/<session-id>.jsonl` (`CLAUDE_CONFIG_DIR` moves
`~/.claude`). Each line has a `type`; messages are linked by `uuid`/`parentUuid`, so a rewound
session keeps its old branch in the file and the history is read back from the newest message.
A rename appends `{"type":"custom-title","customTitle":…}`. A local command's output (`/context`)
is a `system` line with subtype `local_command`, and the next prompt hangs off it.
`--resume <id> --resume-session-at <uuid> --fork-session` copies a session up to and including
that line (any kind), leaving the source whole. Each subagent is in
`<session-id>/subagents/agent-<id>.jsonl`, every line on the side chain (`isSidechain`), with an
`agent-<id>.meta.json` beside it whose `toolUseId` names the Agent call. An image the user sent is
also copied by the CLI to a file, noted in a hidden (`isMeta`) user line.

None of this is a published contract. The tests replay real output of Claude Code 2.1.323 to 2.1.325
(`src/test/resources/fixtures/`, stripped of local paths and account details); when a release
changes the format, record new fixtures and fix the parser, which already ignores what it does
not know.

## Tests

```bash
./gradlew :claudeCodeClient:test
# against the real CLI (spends a few cents of Haiku):
CLAUDE_CODE_LIVE=1 ./gradlew :claudeCodeClient:test --tests '*LiveClaudeCodeTest*'
```
