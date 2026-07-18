# Developing Amy

How to touch the `cli/` module without breaking its public contract.

- What Amy is + how to use it: [README.md](./README.md).
- What to build next and in what order: [ROADMAP.md](./ROADMAP.md).
- Plans for cross-cutting work: see this module's `plans/` folder
  and `commons/plans/` for shared-code work consumed by Amy.

The rule this doc defends: **`cli/` is a thin assembly layer**. If
you're writing Nostr protocol logic, filter building, state machines,
or encryption in here, stop — that code belongs in `quartz/` or
`commons/`.

---

## Public contract

What every caller — user, script, agent, CI — can rely on:

- **Default stdout is human-readable text.** A YAML-ish render of the
  underlying result map. Friendly at a terminal; no shape promises.
- **`--json` is the machine contract. One line. One object.** Stable
  snake_case keys. Pipe it into `jq`, parse it from Python, hand it to
  an agent. Pass `--json` anywhere before the subcommand.
- **stderr is for humans.** Progress, warnings, per-relay ACK traces.
  Safe to discard. Errors land here too: `error: <code>: <detail>` by
  default, or JSON `{"error":"…","detail":"…"}` under `--json`.
- **Exit codes are the real signal.** The exit code is *derived from the
  error-code string* — `Output.error(code, …)` returns the exit code, so
  `return Output.error(…)` always honours the contract with no per-site
  bookkeeping:
  - `0` — success
  - `2` — the code is `bad_args` (every bad argument, including unknown
    flags and malformed numeric/relay/`--author`/`--id` values)
  - `124` — the code is `timeout` (every timeout: `await` verbs,
    `pow mine`, offer/debit round-trips)
  - `1` — every other code (runtime errors, `rejected`, `not_member`, …)
- **No interactive prompts** — passwords, names, keys are all flags — with
  exactly two deliberate, opt-in exceptions: the ncryptsec secret-backend
  passphrase falls back to a TTY prompt when neither `--passphrase-file`
  nor `$AMY_PASSPHRASE` is set, and `bunker --interactive` prompts y/N per
  signing request (it errors with `no_tty` without a terminal). Neither
  can trigger in a correctly-configured script.
- **`~/.amy/` is the whole world.** Per-account dirs hold identity,
  cursors, MLS state, and aliases at `~/.amy/<account>/`; every observed
  Nostr event lands in the shared store under `~/.amy/shared/` (a SQLite
  `events.db` by default; the `events-store/` tree when `AMY_STORE=fs`).
  Delete to reset; copy to move. Shell tests isolate by overriding `$HOME`
  for the amy subprocess (`HOME=/tmp/run.123 amy --account alice …`) —
  same convention `git`, `gpg`, and `npm` use; the in-process JVM tests
  use the `amy.home` system-property seam instead.
- **An account is only required to _sign_.** Read-only verbs (relay
  queries, the shared `store`, `offer`/`debit info`, and the stateless
  primitives) run against an empty `~/.amy/` — `DataDir.resolveOptional`
  hands them an accountless dir (its `hasAccount = false`) pointing only at
  the shared event store, and `Context.openOrAnonymous` gives them an
  ephemeral key-less identity (they read fine, they just can't
  authenticate). Signing verbs go through `Context.open`, which re-asserts
  the account requirement — `init`/`create`/`login`/`logoff`/`whoami`
  resolve strictly, since they operate on the account dir itself.

Only the `--json` shape and the exit codes are public API. The default
text format is allowed to change between releases. The five design
principles below are how we keep that promise.

---

## Design principles

1. **Non-interactive.** One verb = one result on stdout = one exit
   code. No REPL, no daemon, no prompts. Any network wait is an
   explicit `await` verb with a `--timeout`.
2. **Thin command layer.** Each file in `commands/` parses args,
   calls into `commons/` or `quartz/`, and emits a result map via
   `Output.emit(...)`. A file longer than ~200 lines is a code smell
   — the logic is living in the wrong module.
3. **Everything persistent is on-disk.** No in-memory caches that
   survive between invocations. Every run reloads cursors, MLS state,
   identity, and relay config. This is what makes Amy safe to run
   from CI and from 100 parallel interop scenarios.
4. **Shared defaults.** When Amethyst picks a default relay, kind, or
   tag — Amy calls the same helper. No hand-rolled duplicates. If the
   helper doesn't exist yet, extract it to `commons/` first.
5. **The `--json` output shape is the public API.** Default stdout is
   human-readable text (a YAML-ish render of the result map by way of
   `Output.kt`'s default formatter) — that text shape can change
   freely without warning. The `--json` shape cannot: changes to
   keys, types, or nesting are breaking changes. Version them
   explicitly in commit messages; update interop fixtures.

---

## Architecture

```
cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/
├── Main.kt                    # argv → subcommand dispatch (runCli is the
│                              #   testable seam; main() adds exitProcess)
├── Args.kt                    # tiny flag parser: --key value / --key=value,
│                              #   `--` ends flag parsing, rejectUnknown()
├── Output.kt                  # text/json emitter; error(code) → exit code
├── Aliases.kt                 # per-account aliases.json read/write
├── Config.kt                  # Identity, RunState, DataDir (~/.amy layout,
│                              #   `amy.home` system-property override)
├── Context.kt                 # per-run wiring: signer + NostrClient +
│                              #   MarmotManager + publish/drain helpers;
│                              #   syncIncoming delegates to the shared
│                              #   commons/marmot/MarmotSyncPolicy
├── StoreFactory.kt            # AMY_STORE backend pick: sqlite | fs
├── StoreStats.kt              # shared store-size reporting (status, store stat)
├── OperatorKeys.kt            # ~/.amy/operator/ GrapeRank operator keys
├── RelayDiagnostics.kt        # per-relay OK/REJECT stderr traces
├── SecureFileIO.kt            # 0600/0700 atomic writes, perm tighten
├── stores/                    # File-backed MLS/KP/message stores, ConcordStore,
│                              #   cashu keyset counters
├── secrets/                   # SecretStore backends (keychain / ncryptsec / plaintext)
└── commands/                  # ~70 command files + commands/cashu/ (8) — one
    ├── Router.kt              #   file per verb family, named <Verb>Command(s).kt.
    └── …                      # Router.kt's `route(name, tail, usage, routes,
                               #   help=USAGE)` is the shared sub-verb dispatcher;
                               #   most families expose a `val USAGE` that both
                               #   `--help` and the README are kept in sync with.
```

**Dependencies:** `:quartz` + `:commons` (+ `:geode` for `amy serve`) +
kotlinx-coroutines + OkHttp + Jackson. **No Android, no Compose.** Amy
compiles on any JDK 21 host. Never add a Gradle dependency on `:amethyst`
or `:desktopApp`.

**`Context.kt` is the backbone.** Most commands follow this template:

```kotlin
Context.open(dataDir).use { ctx ->   // .use closes the Context on exit
    ctx.prepare()               // restore MLS state + connect relays
    ctx.syncIncoming()          // pull new gift-wraps + group events
    // ...call into commons/ or quartz/ to build an event...
    val ack = ctx.publish(event, targets)
    Output.emit(mapOf(...))
    return 0
}                               // close() flushes RunState + disconnects
```

`Context` is `AutoCloseable`, so wrap it in `use { }` rather than a
hand-rolled `try { } finally { ctx.close() }`.

---

## How to add a command

Rule of thumb: **no new logic in `cli/`**. Every command is an
assembly of things that already work elsewhere.

### 1. Audit (mandatory)

Before writing anything, answer three questions:

1. Is the Nostr-protocol piece (event kind, tags, encryption)
   already in `quartz/`? If not, add it there first.
2. Is the business logic (state, default values, ordering, filter
   assembly) already in `commons/`? If not, extract it from
   `amethyst/` into `commons/` in a preceding commit. See the
   [extraction recipe](#extract-from-android) below.
3. What is the smallest signed event or query this command has to
   produce? That shape is the JSON your command will echo.

### 2. Extract from Android

The single most important recurring task for Amy's growth. Most
Amethyst features today live in `amethyst/src/main/java/…/model/` or
`…/service/` with Android-only imports (`Context`, `SharedPreferences`,
`WorkManager`, `Log`, `Bitmap`). Amy cannot call those directly — they
have to move.

1. Identify the class in `amethyst/` (e.g. `ReactionPost.kt`).
2. List its Android dependencies.
3. For each dependency, choose:
   - **Inline-able** (one call, trivial): delete.
   - **Platform-abstractable**: add `expect`/`actual` in
     `commons/commonMain/…` + `commons/androidMain/…` +
     `commons/jvmMain/…`. (See `kotlin-multiplatform` skill.)
   - **Inversion-of-control**: take it as a constructor arg. Amy
     supplies a JVM flavour.
4. Move the file to `commons/commonMain/…`.
5. Update the Android caller to use the new location. Add a JVM test.
6. Only now, add the `cli/commands/…` file that calls it.

**What to keep in `amethyst/`:** screens, navigation, Android-specific
side-effects (notifications, background services, camera, Intents).
Everything else is a candidate to move.

### 3. Command file template

```kotlin
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output

object NoteCommands {
    // The per-group usage text. `amy note --help` prints it; keep it in
    // lock-step with printUsage() in Main.kt and the README table.
    val USAGE: String =
        """
        |Notes:
        |  note publish TEXT [--relay URL[,URL…]]   publish a kind:1 note
        """.trimMargin()

    suspend fun dispatch(dataDir: DataDir, tail: Array<String>): Int =
        route("note", tail, "note <publish|read|…>", help = USAGE, routes = mapOf(
            "publish" to { rest -> publish(dataDir, rest) },
        ))

    private suspend fun publish(dataDir: DataDir, rest: Array<String>): Int {
        val args = Args(rest)
        val text = args.positional(0, "text")
        val relays = RawEventSupport.relayFlag(args)   // malformed URL → bad_args
        args.rejectUnknown()                           // --typo → bad_args, exit 2
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val event = com.vitorpamplona.amethyst.commons.note.buildTextNote(ctx.signer, text)
            val ack = ctx.publish(event, relays.ifEmpty { ctx.outboxRelays() })
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }  // all-rejected → `rejected`, exit 1
            Output.emit(mapOf(
                "event_id" to event.id,
                "kind" to event.kind,
                "published_to" to ack.filterValues { it }.keys.map { it.url },
                "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
            ))
            return 0
        }
    }
}
```

The shared `route(name, tail, usage, routes, help)` helper (in `Router.kt`)
handles the empty-input and unknown-verb `bad_args` cases (an unknown verb
echoes the expected verb list) and answers `--help` / `-h` / `help` with the
group's `USAGE` text — so each `dispatch` is just the verb→handler map plus
a `USAGE` constant. This is the pattern for every new command group.

Inside a handler, the `Args` contract does the policing for you:

- Read every supported flag through an accessor (`flag`, `bool`, `intFlag`,
  `longFlag`, `requireFlag`), then call **`args.rejectUnknown()`** — any
  leftover `--typo` becomes `bad_args` (exit 2) instead of a silent no-op.
- `intFlag`/`longFlag` throw `bad_args` on non-numeric values; use
  `RawEventSupport.relayFlag` / `Output.invalidRelayUrl` for relay URLs so
  malformed inputs fail the same way everywhere.
- After a publish, call **`RawEventSupport.publishGuard(ack, id)`** — it
  returns the `rejected` (exit 1) error when *every* targeted relay refused
  the event, and `null` otherwise.

Add a top-level branch in `Main.kt`'s `dispatch` and extend both
`printUsage()` and `printVerbList()`. Keep the command tour in
[README.md](./README.md) and the parity matrix in
[ROADMAP.md](./ROADMAP.md) in sync.

### 4. Output-shape conventions

The result map you pass to `Output.emit(...)` IS the `--json` shape —
treat its keys and types as the public API. The default text render
is derived from the same map by `Output.kt` and intentionally has no
contract.

- Top-level object always.
- Stable snake_case keys.
- Event IDs as hex strings (not npub-style).
- Pubkeys as hex (`"pubkey":…`) **and** bech32 when the pubkey is the
  primary subject (`"npub":…`).
- Relay URLs as strings, normalized, never objects.
- Lists of events under a plural key (`"messages"`, `"members"`).
- Errors via `Output.error("code","detail")` — single lower_snake
  code, free-form detail. The return value **is** the exit code
  (`bad_args` → 2, `timeout` → 124, else 1), so handlers
  `return Output.error(…)`.
- Publish results use `published_to` (+ `rejected_by`) everywhere, and
  freshly published/minted events report `event_id`. Don't invent
  `accepted_by`-style variants.

**Error codes — the canonical set.** The code string is part of the
`--json` contract; reuse an existing code before minting a new one:

- **Contract-wide:** `bad_args` (exit 2), `timeout` (exit 124),
  `rejected` (every targeted relay refused a publish; payload carries
  `event_id` + `rejected_by`), `runtime` (uncaught exception),
  `invalid_event` (event/template fails id or signature checks),
  `http_error`, `bad_response`, `not_found`, `exists`, `read_only`,
  `no_identity`, `bad_account`, `signer_error`, `decrypt_failed`.
- **Relay routing:** `no_relays`, `no_dm_relays`, `no_inbox_relays`,
  `no_servers`, `servers_unreachable`, `relay_error`, `sync_error`.
- **Groups:** `not_member` (you aren't in the group / group unknown),
  `target_not_member` (the *other* user isn't).
- **Cashu:** `no_wallet`, `no_mint`, `insufficient_funds`,
  `mint_unreachable`, `mint_http_<status>`, `mint_proofs_spent`,
  `mint_quote_gone`, `nutzap_locked_to_wrong_key`, `invoice_failed`.
- **CLINK / zaps:** `bad_pointer`, `offer_error`, `debit_error`,
  `no_lightning`.
- **Blossom / nsite:** `upload_failed`, `hash_mismatch`,
  `aggregate_mismatch`.
- **Misc:** `no_follows` (fof), `invalid_identifier` (namecoin),
  `no_tty` (`bunker --interactive` without a terminal).

Old spellings are gone — `bad_event`/`bad_template` collapsed into
`invalid_event`, `fetch_failed` into `http_error`, `not_in_group` into
`target_not_member`, and `pow_timeout` into `timeout`.

**Command-family schemas:**

- **Cashu** (`amy cashu …`, NIP-60/61): the full per-verb `--json` key table
  and the `cashu.json` NUT-13 counter layout are pinned in
  [`plans/2026-05-28-cashu-cli.md`](./plans/2026-05-28-cashu-cli.md). Common
  keys: `wallet_event_id`, `mint_url`, `amount_sats` (Long), `proofs_count`,
  `token_event_id`, `history_event_id`, `quote_id`, `p2pk_pubkey`. Error codes
  include `no_wallet`, `no_mint`, `insufficient_funds`, `mint_unreachable`,
  `mint_http_<status>`, `mint_proofs_spent`, `mint_quote_gone`.
- **Admin** (`amy admin …`, NIP-86): `{relay, method, result}` where `result`
  is the relay's raw JSON-RPC result (list/boolean/null). Relay-side failures
  surface as `error: relay_error`.
- **Serve** (`amy serve`): a single startup object `{listening, host, port,
  path, persistent, admin_pubkeys[]}`, then the process blocks (it embeds
  geode; teardown is on SIGINT).

---

## Testing

Most of what Amy does is already exercised by tests in `quartz` and
`commons` — the protocol, the builders, the state machines. The thin
Amy-specific layer still needs its own coverage:

| Layer | Test approach |
|---|---|
| Argument parsing (`Args`, flag forms, `--` terminator, unknown-flag rejection) | `ArgsTest` in `cli/src/test/kotlin/` — plain JVM unit tests. |
| Error / exit-code contract (bad args → 2, timeout → 124, `rejected` → 1) | `ExitCodeContractTest` — table-driven tests invoking `runCli(argv)` with captured stdout/stderr. |
| JSON output shape (keys and types under `--json`) | `JsonContractTest` — runs commands under `--json` and asserts on the parsed object. The default text render has no shape contract and isn't asserted on. |
| File layout on disk (`identity.json`, `shared/events.db`, `marmot/groups/*.mls`, …) | Structural assertions after a command sequence. |
| Round-trip between two accounts on a local relay | End-to-end shell harnesses under `cli/tests/`: each spins up a local `nostr-rs-relay` and a fresh `$HOME=$STATE_DIR` so amy sees a virgin `~/.amy/`, then bootstraps multiple accounts sharing one store and drives a scenario through them. Nine suites today — see [`cli/tests/README.md`](./tests/README.md). |

The JVM suite drives `runCli` **in-process** through the shared
`amy(vararg argv)` harness in `CliResult.kt`: it captures stdout/stderr,
resets the global `Output.mode` around every run, and isolates `~/.amy`
via the **`amy.home` system-property seam** in `DataDir` (a temp dir per
invocation, deleted afterwards) — no subprocess, no `$HOME` games, so the
contract tests run in milliseconds with plain `./gradlew :cli:test`.
`runCli` exists precisely for this: it is `main()` minus `exitProcess`.

**What not to test here:** event signing, filter assembly, MLS
correctness, NIP-44 encryption. Those belong in `quartz`/`commons`.
If an Amy bug can only be caught here, it's a contract violation
(wrong key name, wrong exit code), not a protocol bug.

**Interop-test script template:**

The canonical examples live under `cli/tests/` — read
[`cli/tests/README.md`](./tests/README.md) for the layout, then
crib from `cli/tests/dm/tests-dm.sh` or `cli/tests/marmot/tests-create.sh`.
At the byte-banging level, a minimal round-trip looks like:

```bash
set -euo pipefail
export HOME=$(mktemp -d)   # virgin ~/.amy/ for the duration of this script

amy --account alice create
amy --account bob   create

# ... the scenario under test ...

amy --account bob marmot await message "$GID" --match "hello" --timeout 60
```

If an Amethyst scenario cannot be scripted through Amy yet, that's
a gap — add it to [ROADMAP.md](./ROADMAP.md).

---

## Local event store — the source of truth

Every Nostr event amy observes is verified (NIP-01 id + signature
check) and persisted to a shared store under `~/.amy/shared/` (one
store per machine, shared across every account in `~/.amy/`). That
includes:

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

**Two backends, one interface.** `StoreFactory.kt` picks the backend from
the `AMY_STORE` environment variable; both implement quartz's
`IEventStore`, so every command works unchanged either way:

- **`sqlite` (the default)** — quartz's SQLite store
  (`quartz/.../store/sqlite/EventStore.kt`) in a single database file at
  `~/.amy/shared/events.db`. Index postings live in shared B-tree pages,
  so an event's kind/author/tag indexes cost a handful of rows — for
  crawl-scale corpora (hundreds of thousands of follow lists) this is
  several times smaller on disk than the FS tree.
- **`fs` (`AMY_STORE=fs`)** — the file tree at `~/.amy/shared/events-store/`
  (`quartz/.../store/fs/FsEventStore.kt`): one pretty-printed JSON file per
  event plus one file per index posting, under shard directories.
  Intentionally inspectable with `ls`, `cat`, `jq`, `grep`, `find`,
  `rsync`, and `git` — but every posting rounds up to a filesystem block,
  so a large corpus balloons. Deleting an event file is treated as a
  deliberate "I never saw this"; dangling indexes are skipped at query
  time and cleaned up with `amy store scrub` / `amy store compact`
  (on sqlite those verbs are a no-op / `VACUUM` respectively).

Both backends implement the full feature set — NIP-01 replaceable /
addressable uniqueness, NIP-09 deletion tombstones, NIP-40 expiration,
NIP-50 full-text search (`amy store reindex-fts` rebuilds the index),
NIP-62 right-to-vanish, NIP-91 multi-tag AND. See
[`cli/plans/2026-04-24-file-event-store-*.md`](./plans/) for the FS
design.

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

## Full on-disk layout

```
~/.amy/                                  ← root, follows $HOME (or the amy.home property)
├── current                              # marker file written by `amy use NAME`
├── operator/                            # machine-level GrapeRank operator keys
│                                        #   (independent of any account; OperatorKeys.kt)
├── shared/
│   ├── events.db                        # SQLite event store — the DEFAULT backend
│   ├── dns-cache.bin                    # NIP-05 DNS lookup cache
│   └── events-store/                    # FsEventStore — only when AMY_STORE=fs
│       ├── events/<aa>/<bb>/…           # canonical kind:0 / 3 / 10002 / 10050 / 10051 / 1 / 5 / 1059 / …
│       ├── replaceable/<k>/…            # one slot per (kind, pubkey) for kind:0/3/10000-19999
│       ├── addressable/…                # one slot per (kind, pubkey, d-tag) for kind:30000-39999
│       ├── idx/                         # hardlink indexes (kind / author / owner / tag / fts / expires_at)
│       └── tombstones/                  # NIP-09 / NIP-62 enforcement
├── alice/                               # one dir per account (`amy --account alice init`)
│   ├── identity.json                    # nsec/npub/hex — the account
│   ├── state.json                       # sync cursors (giftWrapSince, groupSince)
│   ├── aliases.json                     # local name → npub map (init writes a self-entry)
│   ├── cashu.json                       # NIP-60 NUT-13 counters: {"keyset_counters":{"<id>":<long>}}
│   ├── concord.json                     # Concord community secrets (ConcordStore)
│   └── marmot/
│       ├── keypackages.bundle           # MLS KeyPackage bundles (NostrSignerInternal)
│       └── groups/
│           ├── <gid>.mls                # MLS group state per group
│           └── <gid>.log                # decrypted inner events (one JSON per line)
└── bob/ ...                             # additional accounts sit alongside
```

Per-account files are plain JSON or framed binary — human-inspectable,
easy to diff across two accounts. Every account on the machine shares the
one store under `~/.amy/shared/`, so a public event observed once doesn't
get re-stored per account. `operator/` sits *beside* the account dirs and
is excluded from account auto-selection (it is not an account).

The local relay configuration (kind:10002 / 10050 / 10051) is **not** a
separate file — it lives in the shared `events-store/` as signed events
owned by the account that wrote them. `amy relay add` builds + signs +
ingests a new relay-list event; `amy relay list` reads URLs straight
out of the latest event for each kind; `amy relay publish-lists`
broadcasts those events to upstream relays. There is no `relays.json`.

---

## Housekeeping

- Run `./gradlew spotlessApply` before every commit.
- Keep four things in sync: `printUsage()` in `Main.kt`, the per-group
  `USAGE` constants (`amy <cmd> --help`), the command tour in
  [README.md](./README.md), and the parity matrix in
  [ROADMAP.md](./ROADMAP.md). They drift fast.
- Never add a Gradle dependency on `:amethyst` or `:desktopApp`. If
  you need something from there, move it to `commons/` first.
- Never introduce a blocking prompt (`readLine()`, interactive
  password input). Take it as a flag. The only sanctioned exceptions
  are the two opt-in TTY paths listed under the public contract
  (ncryptsec passphrase fallback, `bunker --interactive`).
- Keep each command file small. Past ~200 lines, split — the Marmot
  `group` verbs are already a cautionary tale.
