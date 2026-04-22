# Output conventions

Amy's JSON contract is its public API. Follow these rules.

## Channels

| Stream | What goes here |
|---|---|
| **stdout** | Exactly one JSON object per successful invocation. Nothing else. |
| **stderr** | Human progress logs, warnings, per-relay ACK traces, stack traces, `printUsage()` output. Safe to discard. Not machine-consumed. |

If a command needs to emit structured data for machines, it goes on
stdout. If it needs to explain what it's doing to a human watching,
stderr.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. Stdout has a JSON object. |
| `1` | Runtime error. Stderr has `{"error":"...","detail":"..."}`. |
| `2` | Bad arguments. Stderr has a JSON error object and/or usage. |
| `124` | `await` timed out. |

Throw the right exception type in commands:

- `IllegalArgumentException` → exit 2 automatically.
- `AwaitTimeout` → exit 124 automatically.
- Anything else → exit 1.

The top-level `main()` in `Main.kt` handles the translation. Don't
try-catch at the command level unless you're converting a third-party
exception into one of the above.

## Object shape

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
| Timestamps | Unix seconds, integer. Key names end in `_at`. |
| Group ID (Marmot) | Hex string. Key: `group_id`. |

### Collections

- Pluralise: `messages`, `members`, `admins`, `events`.
- Always an array (possibly empty), never `null`.
- Order: oldest-first unless there's a good reason otherwise — state
  it in the key name (`messages_newest_first`) if you flip it.

### Booleans

- Use `true`/`false`, not `0`/`1`, not `"yes"`.
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

```json
{ "error": "code", "detail": "free-form explanation" }
```

- `error` is a short, stable, lower_snake code. Agents can branch on
  it.
- `detail` is free text — OK to change between versions.
- Common codes today: `bad_args`, `no_identity`, `exists`, `bad_key`,
  `not_member`, `timeout`, `runtime`. Reuse before inventing.

## Never

- `println(...)` of anything except `Json.writeLine(...)`.
- Multi-line JSON (pretty-printed). One line, always.
- Mixing stdout lines — one command invocation emits one stdout line.
  If you need progress updates, they go on stderr.
- Machine output to stderr. The whole point is clean separation.
- Silent fallbacks — if a relay rejects your publish, say so in the
  JSON.
