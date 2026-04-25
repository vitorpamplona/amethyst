# Developing Amy

How to touch the `cli/` module without breaking its public contract.

- What Amy is and the public contract: [README.md](./README.md).
- How to use it: [USAGE.md](./USAGE.md).
- What to build next and in what order: [ROADMAP.md](./ROADMAP.md).
- Plans for cross-cutting work: see this module's `plans/` folder
  and `commons/plans/` for shared-code work consumed by Amy.

The rule this doc defends: **`cli/` is a thin assembly layer**. If
you're writing Nostr protocol logic, filter building, state machines,
or encryption in here, stop — that code belongs in `quartz/` or
`commons/`.

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
├── Main.kt                    # argv → subcommand dispatch
├── Args.kt                    # tiny flag parser (no framework)
├── Output.kt                  # text/json mode emitter (--json flag)
├── Aliases.kt                 # per-account aliases.json read/write
├── Config.kt                  # Identity, RunState, DataDir (~/.amy layout)
├── Context.kt                 # per-run wiring: signer + NostrClient +
│                              #   MarmotManager + publish/drain/sync helpers
├── SecureFileIO.kt            # 0600/0700 atomic writes, perm tighten
├── stores/FileStores.kt       # File-backed MLS / KP / message stores
├── secrets/                   # SecretStore backends (keychain / ncryptsec / plaintext)
└── commands/
    ├── Commands.kt            # dispatcher tables
    ├── UseCommand.kt          # `amy use NAME` — pin active account
    ├── InitCommands.kt        # init, whoami
    ├── CreateCommand.kt       # full bootstrap (→ commons/account/)
    ├── LoginCommand.kt        # nsec/ncryptsec/mnemonic/npub/nprofile/hex/nip05
    ├── RelayCommands.kt       # add/list/publish-lists
    ├── ProfileCommands.kt     # profile show / edit (kind:0)
    ├── NotesCommands.kt + PostCommand.kt + FeedCommand.kt   # kind:1
    ├── DmCommands.kt          # NIP-17 dm send / send-file / list / await
    ├── KeyPackageCommands.kt  # marmot key-package publish / check
    ├── GroupCommands.kt + GroupCreateCommand.kt
    │   GroupReadCommands.kt + GroupAddMemberCommand.kt
    │   GroupMembershipCommands.kt + GroupMetadataCommands.kt
    ├── MessageCommands.kt     # marmot message send / list / react / delete
    ├── MarmotResetCommand.kt  # destructive wipe of MLS state
    ├── AwaitCommands.kt       # poll-until-condition helpers
    └── StoreCommands.kt       # store stat / sweep-expired / scrub / compact
```

**Dependencies:** `:quartz` + `:commons` + kotlinx-coroutines + OkHttp
+ Jackson. **No Android, no Compose.** Amy compiles on any JDK 21
host. Never add a Gradle dependency on `:amethyst` or `:desktopApp`.

**`Context.kt` is the backbone.** Most commands follow this template:

```kotlin
val ctx = Context.open(dataDir)
try {
    ctx.prepare()               // restore MLS state + connect relays
    ctx.syncIncoming()          // pull new gift-wraps + group events
    // ...call into commons/ or quartz/ to build an event...
    val ack = ctx.publish(event, targets)
    Output.emit(mapOf(...))
} finally {
    ctx.close()                 // flush RunState, disconnect
}
```

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
    suspend fun dispatch(dataDir: DataDir, tail: Array<String>): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "note <publish|read|…>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "publish" -> publish(dataDir, rest)
            else -> Output.error("bad_args", "note ${tail[0]}")
        }
    }

    private suspend fun publish(dataDir: DataDir, rest: Array<String>): Int {
        val args = Args(rest)
        val text = args.positional(0, "text")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val event = com.vitorpamplona.amethyst.commons.note.buildTextNote(ctx.signer, text)
            val ack = ctx.publish(event, ctx.outboxRelays())
            Output.emit(mapOf(
                "event_id" to event.id,
                "kind" to event.kind,
                "published_to" to ack.filterValues { it }.keys.map { it.url },
            ))
            return 0
        } finally { ctx.close() }
    }
}
```

Wire it into `Commands.kt`, add a top-level branch in `Main.kt`'s
`dispatch`, and extend `printUsage()`. Keep the command tour in
[USAGE.md](./USAGE.md) and the parity matrix in
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
  code, free-form detail.

---

## Testing

Most of what Amy does is already exercised by tests in `quartz` and
`commons` — the protocol, the builders, the state machines. The thin
Amy-specific layer still needs its own coverage:

| Layer | Test approach |
|---|---|
| Argument parsing (`Args`, flag forms, `--account=…` vs `--account …`) | Plain JVM unit tests in `cli/src/test/kotlin/`. |
| Error / exit-code contract (bad args → 2, await timeout → 124, runtime → 1) | Table-driven tests invoking `main(argv)` with captured stdout/stderr. |
| JSON output shape (each command's keys and types under `--json`) | Snapshot tests: run a command with `--json` against a throwaway `$HOME` (`HOME=$(mktemp -d) amy --account X …`), assert the JSON matches a golden file. The default text render has no shape contract and shouldn't be snapshotted. |
| File layout on disk (`identity.json`, `events-store/…`, `marmot/groups/*.mls`, `marmot/keypackages.bundle`) | Structural assertions after a command sequence. |
| Round-trip between two accounts on a local relay | End-to-end shell harnesses under `cli/tests/`: each spins up a local `nostr-rs-relay` and a fresh `$HOME=$STATE_DIR` so amy sees a virgin `~/.amy/`, then bootstraps multiple accounts (`--account A`, `--account D`, …) sharing one `~/.amy/shared/events-store/` and drives a scenario through them. Today: `cli/tests/dm/` (NIP-17 DMs between two amy accounts) and `cli/tests/marmot/` (MLS scenarios vs whitenoise-rs `wn`/`wnd`). |

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

## Housekeeping

- Run `./gradlew spotlessApply` before every commit.
- Keep three things in sync: `printUsage()` in `Main.kt`, the command
  tour in [USAGE.md](./USAGE.md), and the parity matrix in
  [ROADMAP.md](./ROADMAP.md). They drift fast.
- Never add a Gradle dependency on `:amethyst` or `:desktopApp`. If
  you need something from there, move it to `commons/` first.
- Never introduce a blocking prompt (`readLine()`, interactive
  password input). Take it as a flag.
- Keep each command file small. Past ~200 lines, split — the Marmot
  `group` verbs are already a cautionary tale.
