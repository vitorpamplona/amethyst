# Amethyst assistant

## Goal

The settings assistant becomes the **Amethyst assistant**: a chat that can do what the user can
do in the app, not only change settings. Anything a person does on an Amethyst screen (post,
reply, upload, sync, zap, pay) should, in time, be a tool the assistant can call, with the same
code path the screen uses.

It runs on the user's own agent:

1. **Claude Code on the desktop** (`ClaudeBackend`), when the `claude` CLI is installed: the tools
   are in-process MCP tools, as the settings tools already were.
2. **The user's own AI key otherwise** (NIP-FA kind 12000, `Account.agentKeys`): an in-app
   chat-completions loop (`ApiAgentBackend`) against the preferred key's OpenAI-compatible
   endpoint, with the same tools, the same approval cards and its own saved sessions. That is
   how the assistant works with no CLI installed, and it is the path to Android later.

## Survey

| Piece | Status |
|---|---|
| `ClaudeCodeService`, `ClaudeChat`, chat screen, approval cards | reused as is |
| `SettingsToolServer` | reused; its tools stay |
| Posting: `Account.signAndComputeBroadcast`, `ReplyActions`, `messageTags` | reused (the composer's path) |
| Upload: `UploadOrchestrator` + `BaseAccountViewModel.mediaUploader`, default media server | reused |
| Zaps: `BaseAccountViewModel.zap` (`ZapPaymentHandler`: splits, V4V, NWC or wallet app) | reused, wrapped to await the outcome |
| Invoices: `AccountZapActions.sendZapPaymentRequestFor` (NWC `pay_invoice`) | reused |
| Relay sync: `BaseAccountViewModel.eventSync` (`EventSync`) | reused |
| Agent keys: `AgentKeyState.preferred()`, auth/URL rules of `FrontPageNetwork` | reused |
| A tool-calling chat-completions client | **new** (the Observer's writer streams text, no tools) |
| `AccountToolServer`, `BoundToolServer`, `ApiAgentBackend` | **new** |

## Approval policy

- **Reads and navigation run without asking**: searching settings, reading a note, wallet and
  sync status, opening a screen.
- **Every write and every payment asks, every time.** A host tool can say it `alwaysAsks`; the
  chat then drops "don't ask again" from the card and makes No the default key. The card shows
  exactly what goes out: the post's text, the note replied to, the file and server, the sats and
  who receives them, the invoice's amount.
- Zaps and payments are in, behind that card. Keys and the signer are not tools.

## Sessions

One conversation, resumed after a restart (see the fix of 2026-11-24 in
`ClaudeCodeService.settingsAssistant`). Tools are bound late: the chat holds a `BoundToolServer`
slot that whichever screen shows the chat points at the current account and navigation, so a
resumed session never acts on a stale account. Unbound, a tool answers that it is out of reach.

## First slice (this change)

`post_note`, `reply_to_note`, `get_note`, `upload_file`, `zap_note`, `pay_invoice`,
`wallet_status`, `start_relay_sync`, `relay_sync_status`, plus the settings tools; the online
backend; the rename.

## Next

- Zap a person (profile zap), reactions, reposts, follows, bookmarks, DMs (NIP-17), lists.
- Reading feeds and notifications as tools ("what did I miss?").
- A tool registry like `AmethystSettings`, so each screen's actions are declared once and the
  assistant's tools are generated from it.
- Streaming replies on the online backend; model choice from the key's `/models`.
- Android: the online backend only needs `HostToolServer` (agentCore, JVM) on Android.
