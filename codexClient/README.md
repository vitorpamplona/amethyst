# codexClient

An **unofficial** Kotlin/JVM client for OpenAI's Codex CLI. It is not an OpenAI product and bundles
nothing from OpenAI: it starts the `codex` program the user installed as `codex app-server` and speaks
its JSON-RPC protocol over stdin/stdout. It shares only `agentCore` with `claudeCodeClient`, and has no
Amethyst or Nostr dependencies.

| Need | Where |
|---|---|
| JSON-RPC: requests and answers, notifications, Codex's own requests (approvals) | `CodexRpc` |
| One thread: handshake, start / resume / fork (`lastTurnId`), turns, approvals, models, rename | `CodexConnection` |
| Thread items as the chat's content blocks; a thread's turns as history | `CodexItems` |
| Find `codex`, list / read / rename / delete threads, start a connection | `CodexBackend` |

## The protocol

`codex app-server` speaks JSON-RPC 2.0, one object per line (its answers leave out `"jsonrpc"`). The
schema comes from the CLI itself: `codex app-server generate-json-schema --out DIR` (v2). The client
uses: `initialize` + `initialized`, `model/list`, `thread/start|resume|fork|read|list|delete`,
`thread/name/set`, `turn/start|interrupt`; notifications `turn/started|completed`, `item/started|completed`,
`item/agentMessage/delta`, `thread/tokenUsage/updated`, `serverRequest/resolved`; and answers
`item/commandExecution/requestApproval` and `item/fileChange/requestApproval` with `accept`,
`acceptForSession`, `decline` or `cancel`. Codex's other requests are refused at once.

The chat's permission modes become an approval policy and a sandbox: ask (`untrusted`,
`workspace-write`), accept edits (`on-request`, `workspace-write`), plan (`on-request`, `read-only`).

## Tests

```bash
./gradlew :codexClient:test
# against the real CLI (thread start/rename/list/delete need no account; the turn does):
CODEX_LIVE=1 ./gradlew :codexClient:test --tests '*liveCodex*'
```
