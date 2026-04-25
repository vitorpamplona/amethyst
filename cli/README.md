# Amy — Amethyst CLI

`amy` is the non-interactive command-line face of Amethyst. It speaks the
same Nostr protocol as the Android and Desktop apps, shares the same
`quartz` and `commons` code, and aims to eventually expose every feature
the GUI offers as a command you can script.

Amy exists for three audiences at once:

1. **Humans** using Amethyst from a terminal or remote shell.
2. **Agents / LLMs** driving a Nostr account through a deterministic,
   JSON-typed interface — no interactive prompts, no screen scraping.
3. **Interop test harnesses** that put Amethyst side-by-side with the
   other ~100 Nostr clients publishing and consuming the same events.
   Any flow that is tested in the Amethyst app should be reproducible
   through `amy` — that's the bar.

This file documents the **public contract** and the on-disk layout. For
hands-on instructions and worked examples, see
[USAGE.md](./USAGE.md). To extend amy, see
[DEVELOPMENT.md](./DEVELOPMENT.md). For what's coming, see
[ROADMAP.md](./ROADMAP.md).

---

## Output contract

What every caller — user, script, agent, CI — can rely on:

- **Default stdout is human-readable text.** A YAML-ish render of the
  underlying result map. Friendly at a terminal; no shape promises.
- **`--json` is the machine contract. One line. One object.** Stable
  snake_case keys. Pipe it into `jq`, parse it from Python, hand it to
  an agent. Pass `--json` anywhere before the subcommand.
- **stderr is for humans.** Progress, warnings, per-relay ACK traces.
  Safe to discard. Errors land here too: `error: <code>: <detail>` by
  default, or JSON `{"error":"…","detail":"…"}` under `--json`.
- **Exit codes are the real signal.**
  - `0` — success
  - `1` — runtime error
  - `2` — bad arguments
  - `124` — `await` timed out
- **No interactive prompts, ever.** Passwords, names, keys — all flags.
- **`~/.amy/` is the whole world.** Per-account dirs hold identity,
  cursors, MLS state, and aliases at `~/.amy/<account>/`; every observed
  Nostr event lands in `~/.amy/shared/events-store/`. Delete to reset;
  copy to move. Tests isolate by overriding `$HOME` for the amy
  subprocess (`HOME=/tmp/run.123 amy --account alice …`) — same
  convention `git`, `gpg`, and `npm` use.

Only the `--json` shape and the exit codes are public API. The default
text format is allowed to change between releases. The rationale lives
in [DEVELOPMENT.md](./DEVELOPMENT.md).

---

## Local event store — the source of truth

Every Nostr event amy observes is verified (NIP-01 id + signature
check) and persisted to a file-backed store at
`~/.amy/shared/events-store/` (one store per machine, shared across
every account in `~/.amy/`). That includes:

- events received from any relay subscription (`amy notes feed`,
  `amy dm list`, `amy marmot key-package publish`, group sync, …),
- events amy generates and publishes itself,
- inner events unwrapped from NIP-59 gift wraps.

Malformed events are dropped before reaching command code. Persistence
is best-effort — if the store fails (full disk, permissions), the relay
subscription still works, but the event is not cached.

The store is the authoritative cache of everything amy has seen:
profile metadata, relay lists (NIP-65 and NIP-02), gift wraps, group
events, follow lists, etc. Commands that need any of these read from
the store first and only fall back to a relay fetch on miss. Three
convenience helpers exist on `Context`:

```kotlin
ctx.profileOf(pubKey)            // latest kind:0    (NIP-01)
ctx.relaysOf(pubKey)             // latest kind:10002 (NIP-65)
ctx.contactsOf(pubKey)           // latest kind:3    (NIP-02)
ctx.dmInboxOf(pubKey)            // latest kind:10050 (NIP-17 DM inbox)
ctx.keyPackageRelaysOf(pubKey)   // latest kind:10051 (MIP-00 KP relays)
ctx.cachedRelayListsOf(pubKey)   // RecipientRelayFetcher.Lists from cache
```

The store implements every feature of the Quartz SQLite store —
NIP-01 replaceable / addressable uniqueness, NIP-09 deletion
tombstones, NIP-40 expiration, NIP-50 search, NIP-62 right-to-vanish,
NIP-91 multi-tag AND. See
[`cli/plans/2026-04-24-file-event-store-*.md`](./plans/) for the design
and `quartz/.../store/fs/FsEventStore.kt` for the implementation. The
on-disk layout is plain JSON files under shard directories,
intentionally inspectable with `ls`, `cat`, `jq`, `grep`, `find`,
`rsync`, and `git`. Deleting an event file is treated as a deliberate
"I never saw this" by amy; dangling indexes are skipped at query time
and can be cleaned up with `amy store scrub` / `amy store compact`.

---

## Relay routing

amy follows the Marmot protocol's per-event routing rules so two users
with completely disjoint relay configurations can still marmot each
other. No event ever ships blindly to "our configured relays" — amy
looks up the right relay set per event per recipient.

| Event | Publish to | Fetch from |
|---|---|---|
| kind:30443 (our own KeyPackage) | `key_package` bucket → NIP-65 outbox → any configured | — |
| kind:30443 (someone else's KeyPackage) | — | Their kind:10051 → their kind:10002 write → our bootstrap pool |
| kind:10051 / 10050 / 10002 (our own lists) | All configured relays (broadcast) | — |
| kind:10051 / 10050 / 10002 (someone else's) | — | Our bootstrap pool = configured relays ∪ Amethyst defaults |
| kind:1059 Welcome gift wrap (kind:444 inside) | Recipient's kind:10050 → their kind:10002 read → `DefaultDMRelayList` → our outbox | — |
| kind:1059 gift wraps addressed to us | — | Our kind:10050 |
| kind:445 Group Event (Commit / Proposal / chat) | Group's MIP-01 `relays` field | Same |

**Bootstrap pool**: when amy needs to discover a user it's never talked
to, it queries `configured relays ∪ Amethyst's default NIP-65 set ∪
Amethyst's default DM-inbox set`. These defaults come from
`commons.defaults.AmethystDefaults` and match what the Android/Desktop
UI publishes to on first run, so any fresh Amethyst account is
reachable via the bootstrap pool even before amy has seen any of their
events.

---

## On-disk layout

```
~/.amy/                                  ← root, follows $HOME
├── current                              # marker file written by `amy use NAME`
├── shared/
│   └── events-store/                    # FsEventStore — every observed Nostr event
│       ├── events/<aa>/<bb>/…           # canonical kind:0 / 3 / 10002 / 10050 / 10051 / 1 / 5 / 1059 / …
│       ├── replaceable/<k>/…            # one slot per (kind, pubkey) for kind:0/3/10000-19999
│       ├── addressable/…                # one slot per (kind, pubkey, d-tag) for kind:30000-39999
│       ├── idx/                         # hardlink indexes (kind / author / owner / tag / fts / expires_at)
│       └── tombstones/                  # NIP-09 / NIP-62 enforcement
├── alice/                               # one dir per account (e.g. created by `amy --account alice init`)
│   ├── identity.json                    # nsec/npub/hex — the account
│   ├── state.json                       # sync cursors (giftWrapSince, groupSince)
│   ├── aliases.json                     # local name → npub map (init writes a self-entry)
│   └── marmot/
│       ├── keypackages.bundle           # MLS KeyPackage bundles (NostrSignerInternal)
│       └── groups/
│           ├── <gid>.mls                # MLS group state per group
│           └── <gid>.log                # decrypted inner events (one JSON per line)
└── bob/ ...                             # additional accounts sit alongside
```

All files are plain JSON or framed binary — human-inspectable, easy to
diff across two accounts. Two accounts on the same machine share
`~/.amy/shared/events-store/`, so a public event observed once doesn't
get re-stored per account.

The local relay configuration (kind:10002 / 10050 / 10051) is **not** a
separate file — it lives in the shared `events-store/` as signed events
owned by the account that wrote them. `amy relay add` builds + signs +
ingests a new relay-list event; `amy relay list` reads URLs straight
out of the latest event for each kind; `amy relay publish-lists`
broadcasts those events to upstream relays. There is no `relays.json`.

---

## Where to go next

- **[USAGE.md](./USAGE.md)** — install, quick start, worked examples,
  the command reference, troubleshooting.
- **[DEVELOPMENT.md](./DEVELOPMENT.md)** — design principles,
  architecture, how to add a command without breaking the contract.
- **[ROADMAP.md](./ROADMAP.md)** — north-star goal and the parity
  matrix tracking what's left to extract from the Android app.
- **[`plans/`](./plans/)** — design docs for cross-cutting work
  (CLI distribution, file-backed event store, NIP-17 DMs, …).
