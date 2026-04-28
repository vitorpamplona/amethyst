# Output conventions

amy ships a dual-output contract. Default stdout is human-readable
text (a YAML-ish render of the underlying result map); `--json` flips
stdout to a single JSON object per success. The text shape can drift;
the `--json` shape is the public API.

Commands always emit via `Output.emit(mapOf(...))`. The map IS the
JSON shape — the renderer in `Output.kt` derives the text from the
same map. Don't write two render paths; write one map and let
`Output` pick.

## Channels

| Stream | Default mode | `--json` mode |
|---|---|---|
| **stdout** | YAML-ish text from `Output.emit(...)` | Exactly one JSON object per successful invocation |
| **stderr** | Human progress logs, warnings, per-relay ACK traces, stack traces, `printUsage()` output, errors as `error: <code>: <detail>` | Same logs, plus errors as `{"error":...,"detail":...}` |

If a command needs to emit structured data for machines, it goes on
stdout and is automatically JSON under `--json`. If it needs to
explain what it's doing to a human watching, stderr.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | Runtime error. |
| `2` | Bad arguments. |
| `124` | `await` timed out. |

Throw the right exception type in commands:

- `IllegalArgumentException` → exit 2 automatically.
- `AwaitTimeout` → exit 124 automatically.
- Anything else → exit 1.

The top-level `main()` in `Main.kt` handles the translation. Don't
try-catch at the command level unless you're converting a third-party
exception into one of the above.

## `--json` object shape

### Top-level

Always an object. Never an array, never a primitive, never a
newline-delimited stream.

```json
{ "event_id": "...", "kind": 1, "published_to": [...] }
```

### Keys

- Stable snake_case.
- Additive evolution is safe; renaming or removing a key is a
  breaking change.
- Don't nest unnecessarily. `{"data":{...}}` is noise.

### Identifiers

| Thing | Form |
|---|---|
| Event ID | 64-char lowercase hex string. Key name: `event_id`. |
| Pubkey (primary subject) | hex **and** bech32. Keys: `pubkey` + `npub`. |
| Pubkey (secondary reference) | hex only. Key: `pubkey`. |
| Relay URL | Normalized string (`wss://…`). Never an object. |
| Timestamps | Unix seconds, integer. Key names end in `_at`. The text renderer auto-formats these as `2026-04-25 13:42:11Z (8m ago)`. |
| Group ID (Marmot) | Hex string. Key: `group_id`. |
| Byte counts | Integer. Key names end in `_bytes`. The text renderer auto-formats these as `8.7 KiB`. |

### Collections

- Pluralise: `messages`, `members`, `admins`, `events`.
- Always an array (possibly empty), never `null`.
- Order: oldest-first unless there's a good reason otherwise — state
  it in the key name (`messages_newest_first`) if you flip it.

### Booleans

- Use `true`/`false` in the result map. The text renderer prints them
  as `yes`/`no` (green/red); `--json` keeps the literal booleans.
- Name keys so `true` is the expected/successful state:
  `is_member`, `published`, `accepted`.

### Publish results

When a command publishes an event, the canonical output shape is:

```json
{
  "event_id": "<hex>",
  "kind": 1,
  "published_to": ["wss://relay.a/", "wss://relay.b/"],
  "rejected_by": ["wss://relay.c/"]
}
```

`published_to` is relays that ACK'd `true`. `rejected_by` is relays
that ACK'd `false`. Relays that didn't answer before the timeout
appear in neither — add `timed_out_on` if you need to surface them.

### Error shape

Default mode (text):

```text
error: not_member: <gid>
```

Under `--json`:

```json
{ "error": "not_member", "detail": "<gid>" }
```

- `error` is a short, stable, lower_snake code. Agents can branch on
  it.
- `detail` is free text — OK to change between versions.
- Common codes today: `bad_args`, `no_identity`, `no_account`,
  `exists`, `bad_key`, `not_member`, `no_dm_relays`, `timeout`,
  `runtime`. Reuse before inventing.

Use `Output.error("code", "detail")` from commands; it picks the
right channel and format based on the active mode.

## Never

- `println(...)` of anything except `Output.emit(...)`.
- `Json.writeLine` / `Json.error` — that helper is gone; use the
  `Output` object instead.
- Multi-line JSON (pretty-printed) under `--json`. One line, always.
- Mixing stdout lines — one command invocation emits one stdout line
  in `--json` mode. If you need progress updates, they go on stderr.
- Machine output to stderr. The whole point is clean separation.
- Silent fallbacks — if a relay rejects your publish, say so in the
  result map.
- Building text rendering by hand. Trust the `Output.kt` renderer:
  it handles alignment, colour, byte/timestamp formatting, nested
  maps and lists. If you need a bespoke render for one command,
  pass a custom render lambda — don't go around `Output`.
