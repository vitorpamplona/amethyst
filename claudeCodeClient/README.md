# claudeCodeClient

An **unofficial** Kotlin/JVM client for the Claude Code CLI. It is not an Anthropic product and
bundles nothing from Anthropic: it starts the `claude` program the user installed and talks to it
over stdin/stdout, and it reads the session files that program writes.

It has no Amethyst or Nostr dependencies (kotlinx.coroutines and kotlinx.serialization only), so
it can move to its own repository once something outside Amethyst needs it.

## What it does

| Need | Where |
|---|---|
| Start a session or continue one (`--resume`, `--fork-session`), pick model and permission mode | `process/ClaudeCodeLauncher` |
| Send prompts, read every message in order, answer permission prompts, interrupt, change mode or model, rename | `process/ClaudeCodeConnection` |
| The messages and content blocks, parsed leniently (unknown types kept as raw JSON) | `protocol/` |
| List saved sessions with titles, folder and branch; read a session's history; rename; delete | `session/ClaudeSessionStore` |
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

Sessions live in `~/.claude/projects/<folder>/<session-id>.jsonl` (`CLAUDE_CONFIG_DIR` moves
`~/.claude`). Each line has a `type`; messages are linked by `uuid`/`parentUuid`, so a rewound
session keeps its old branch in the file and the history is read back from the newest message.
A rename appends `{"type":"custom-title","customTitle":…}`.

None of this is a published contract. The tests replay real output of Claude Code 2.1.323
(`src/test/resources/fixtures/`, stripped of local paths and account details); when a release
changes the format, record new fixtures and fix the parser, which already ignores what it does
not know.

## Tests

```bash
./gradlew :claudeCodeClient:test
# against the real CLI (spends a few cents of Haiku):
CLAUDE_CODE_LIVE=1 ./gradlew :claudeCodeClient:test --tests '*LiveClaudeCodeTest*'
```
