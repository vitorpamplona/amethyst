# Developing Amy

This doc is the north-star plan for growing `amy` from a Marmot
test-bed into a full command-line mirror of Amethyst. It is meant to
be picked up by any future contributor (human or agent) who needs to
add a feature, extract logic out of `amethyst/`, or close an interop
gap.

User-facing reference lives in [README.md](./README.md). This file
covers **why** things are shaped the way they are and **what to build
next**.

---

## 1. North-star goal

> For every feature of the Amethyst Android app, there is a way to
> exercise it through `amy`, with byte-identical on-relay behaviour.

This matters because:

- Interop tests against the ~100 other Nostr clients need a reproducible
  harness that does not require running Android.
- Agents and LLMs can script real Amethyst flows without touching a GUI.
- Regressions in shared logic (signing, encryption, filter building,
  event parsing) become shell-scriptable.
- Power users get a command-line Amethyst for free.

The non-goal: Amy is **not** a second Nostr client implementation. It
is a thin assembly layer over `quartz` + `commons`. If you find yourself
writing protocol or business logic inside `cli/`, stop — that code
belongs in `commons/` or `quartz/`.

---

## 2. Design principles

1. **Non-interactive.** One verb = one JSON object on stdout = one exit
   code. No REPL, no daemon, no prompts.
2. **Thin command layer.** Each file in `cli/src/.../commands/` is glue:
   parse args → call into `commons/` or `quartz/` → print JSON. A file
   longer than ~200 lines is a code smell; the logic is probably living
   in the wrong module.
3. **Everything persistent is on-disk.** No in-memory caches that
   survive between invocations. Cursors, MLS state, identity, relay
   config — all reload on every run. This is what makes `amy` safe to
   run from CI and from 100 parallel interop scenarios.
4. **Shared defaults.** When the Android app sets a default relay,
   picks a kind, derives a tag — Amy calls the same helper. No hand-
   rolled duplicates. If such a helper doesn't exist yet, the fix is to
   extract it to `commons/`, not to copy its body into `cli/`.
5. **JSON is the public API.** Output shape changes are breaking
   changes. Treat them as such, version them explicitly in commit
   messages, and update interop fixtures.

---

## 3. Current architecture

```
cli/
└── src/main/kotlin/com/vitorpamplona/amethyst/cli/
    ├── Main.kt                    # argv → subcommand dispatch
    ├── Args.kt                    # tiny flag parser (no framework)
    ├── Json.kt                    # single-line stdout + error printer
    ├── Config.kt                  # Identity, RelayConfig, RunState, DataDir
    ├── Context.kt                 # per-run wiring (signer + NostrClient +
    │                              #   MarmotManager + publish/drain/sync helpers)
    ├── stores/FileStores.kt       # File-backed MLS / KP / message stores
    └── commands/
        ├── Commands.kt            # dispatcher
        ├── InitCommands.kt        # init, whoami
        ├── CreateCommand.kt       # full bootstrap (→ commons/account/)
        ├── LoginCommand.kt        # nsec/ncryptsec/mnemonic/npub/nprofile/hex/nip05
        ├── RelayCommands.kt       # add/list/publish-lists
        ├── KeyPackageCommands.kt  # marmot key-package publish / check
        ├── GroupCommands.kt       # marmot group create/list/show/…
        ├── GroupCreateCommand.kt
        ├── GroupReadCommands.kt
        ├── GroupAddMemberCommand.kt
        ├── GroupMembershipCommands.kt
        ├── GroupMetadataCommands.kt
        ├── MessageCommands.kt     # marmot message send / list
        └── AwaitCommands.kt       # poll-until-condition helpers
```

**Dependencies:** `:quartz` + `:commons` + kotlinx-coroutines + OkHttp +
Jackson. No Android, no Compose. This is intentional — `amy` compiles
on any JDK 21 target, including CI runners and headless interop hosts.

**Context.kt is the backbone.** Most commands follow this template:

```kotlin
val ctx = Context.open(dataDir)
try {
    ctx.prepare()               // restore MLS state + connect relays
    ctx.syncIncoming()          // pull new gift-wraps + group events
    // ...call into commons/ or quartz/ to build an event...
    val ack = ctx.publish(event, targets)
    Json.writeLine(mapOf(...))
} finally {
    ctx.close()                 // flush RunState, disconnect
}
```

Keep new commands in that shape.

---

## 4. Current surface vs. Amethyst surface

What Amy can drive today (✅), what's clearly gap (🆕), and where the
logic already lives in `commons/` ready to be called (📦).

| Area | Status | Notes |
|---|---|---|
| Identity create / import (`nsec`, `ncryptsec`, mnemonic, `npub`, `nprofile`, hex, NIP-05) | ✅ | `LoginCommand` + Quartz NIP-05 / NIP-06 / NIP-49 |
| Account bootstrap (nine events) | ✅ | `commons/account/AccountBootstrapEvents.kt` |
| Relay config + NIP-65 / NIP-10050 publish | ✅ | `RelayCommands` |
| MLS KeyPackage publish + fetch | ✅ | `commons/marmot/MarmotManager` |
| Marmot group create / add / rename / promote / demote / remove / leave | ✅ | `commons/marmot/` |
| Marmot message send / list | ✅ | `commons/marmot/` |
| `await` polling for KP / group / member / admin / message / rename / epoch | ✅ | `AwaitCommands` |
| NIP-01 note publish (`amy note publish TEXT`) | 🆕 | Needs a `commons/` builder wrapper. |
| NIP-01 feed read (`amy feed home`, `amy feed hashtag #X`, `amy feed profile NPUB`) | 🆕 | Extract `FeedFilter` usage from `amethyst/ui/dal/` into `commons/` entry points. |
| NIP-02 follow list add / remove / list | 🆕 | Logic in `amethyst/model/nip02FollowLists/`. |
| NIP-09 event deletion | 🆕 | Builder exists in quartz. |
| NIP-17 DMs send / list / read | 🆕 | Gift-wrap path is already in `commons/` via Marmot — generalise. |
| NIP-18 reposts / quotes | 🆕 | |
| NIP-25 reactions | 🆕 | |
| NIP-51 lists (bookmarks, mute, follow sets) | 🆕 | `amethyst/model/nip51Lists/` |
| NIP-57 zaps (send + verify) | 🆕 | Needs LN-URL plumbing; `amethyst/service/lnurl/`. |
| NIP-65 outbox model queries | 🆕 | |
| NIP-72 communities | 🆕 | |
| NIP-78 app-specific data (settings sync) | 🆕 | |
| Long-form (NIP-23) publish / read | 🆕 | |
| Live activities / chess (NIP-53 / NIP-64) | 🆕 | |
| Blossom uploads (NIP-B7) | 🆕 | |
| NIP-47 Wallet Connect | 🆕 | |
| NIP-46 bunker signer | 🆕 | Would need a `signers` abstraction in Amy. |
| Profile view (`amy profile show NPUB`) | 🆕 | Renderer work — see §6. |
| Thread view (`amy thread show EVENT_ID`) | 🆕 | |
| Notifications feed | 🆕 | |
| Search (NIP-50) | 🆕 | |

Treat this as a live checklist — update it in the same PR that closes
a gap.

---

## 5. How to add a command

Rule of thumb: **no new logic in `cli/`**. Every command is an
assembly of things that already work elsewhere. Follow these steps.

### 5.1. Audit (mandatory)

Before you write anything, answer three questions:

1. Is the Nostr-protocol piece (event kind, tags, encryption) already
   in `quartz/`? If not, add it there first.
2. Is the business logic (state, default values, ordering, filter
   assembly) already in `commons/`? If not, **extract it from
   `amethyst/` into `commons/`** in a preceding commit.
3. What is the smallest signed event or query this command has to
   produce? Write that down — it is the JSON your command will echo.

If `commons/` doesn't have the helper you need, see §5.2 before
touching `cli/`.

### 5.2. Extract-from-Android checklist

This is the single most important recurring task for Amy's growth.
Most Amethyst features today live in `amethyst/src/main/java/…/model/`
or `…/service/` with Android-only imports (Context, SharedPreferences,
WorkManager). Amy cannot call those directly — they have to move.

Extraction recipe:

1. Identify the class in `amethyst/` (e.g. `ReactionPost.kt`).
2. List its Android dependencies. The usual offenders: `Context`,
   `SharedPreferences`, `WorkManager`, `Log`, `Bitmap`.
3. For each one, choose:
   - **Inline-able** (one call, trivial): delete it.
   - **Platform-abstractable**: add an `expect`/`actual` in
     `commons/commonMain/…` + `commons/androidMain/…` + `commons/jvmMain/…`.
     (See `kotlin-multiplatform` skill.)
   - **Inversion-of-control**: take it as a constructor arg. Amy
     supplies a JVM flavour.
4. Move the file to `commons/commonMain/…`.
5. Update the Android caller to use the new location. Add a JVM test.
6. Only now, add the `cli/commands/…` file that calls it.

**What to keep in `amethyst/`:** screens, navigation, Android-specific
side-effects (notifications, background services, camera, Intents).
Everything else is a candidate to move.

### 5.3. Command file template

```kotlin
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json

object NoteCommands {
    suspend fun dispatch(dataDir: DataDir, tail: Array<String>): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "note <publish|read|…>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "publish" -> publish(dataDir, rest)
            else -> Json.error("bad_args", "note ${tail[0]}")
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
            Json.writeLine(mapOf(
                "event_id" to event.id,
                "kind" to event.kind,
                "published_to" to ack.filterValues { it }.keys.map { it.url },
            ))
            return 0
        } finally { ctx.close() }
    }
}
```

Then wire it in `Commands.kt` and add a top-level branch in `Main.kt`'s
`dispatch`. Extend `printUsage()`.

### 5.4. Output-shape conventions

- Top-level object always.
- Stable snake_case keys.
- Event IDs as hex strings (not npub-style).
- Pubkeys as hex (`"pubkey":…`) **and** bech32 when it's the primary
  subject of the output (`"npub":…`).
- Relay URLs as strings, normalized, never objects.
- Lists of events are under a plural key (`"messages"`, `"members"`).
- Errors via `Json.error("code","detail")` — single lower_snake code,
  free-form detail.

---

## 6. Rendering Nostr events

Amy has to render every Nostr kind Amethyst understands, in a way that:

- a human reading a terminal can parse at a glance,
- an agent can parse mechanically,
- an interop test can diff against a fixture.

**Plan:**

1. Build an `EventRenderer` interface in
   `commons/commonMain/.../rendering/` with one `render(event, ctx)`
   call returning a structured `RenderedEvent` (title, summary, body,
   mentions, media refs, etc) — not a string.
2. Register a renderer per kind, keyed by `event.kind`. Default
   renderer dumps raw tags + content. Specialised renderers cover the
   kinds Amethyst displays specially (kind:0 metadata, kind:1 notes,
   kind:6 reposts, kind:7 reactions, kind:9/445 Marmot, kind:1059
   gift-wraps once unwrapped, kind:10002, kind:30023, kind:30043,
   kind:30311 live activities, etc).
3. Reuse these renderers from the Desktop app too — the `RenderedEvent`
   structure is the input to Compose views and to Amy's JSON, via two
   different formatters.
4. Amy's formatter serialises `RenderedEvent` to JSON with a stable
   schema. A second formatter produces a human pretty-print (`amy note
   show EID --format text`).

This is the single biggest cross-cutting work item. It benefits the
Desktop app and Amy simultaneously, and it unlocks a huge chunk of the
"🆕" rows in §4 cheaply, because once the renderer exists, a feed
command is just "query, render each, emit list".

---

## 7. Testing Amy

Most of what Amy does is exercised by tests in `quartz` and `commons`
already — the protocol, the builders, the state machines. The thin
Amy-specific layer still needs its own coverage:

| Layer | Test approach |
|---|---|
| Argument parsing (`Args`, flag forms, `--data-dir=…` vs `--data-dir …`) | Plain JVM unit tests in `cli/src/test/kotlin/`. |
| Error / exit-code contract (bad args → 2, await timeout → 124, runtime → 1) | Table-driven unit tests invoking `main(argv)` with a captured stdout/stderr. |
| JSON output shape (each command's keys and types) | Snapshot tests: run a command against a throwaway data-dir, assert the emitted JSON matches a golden file. |
| File layout on disk (`identity.json`, `relays.json`, `groups/*.mls`, `keypackages.bundle`) | Structural assertions after running a sequence of commands. |
| Round-trip between two data-dirs on a local ephemeral relay | End-to-end shell tests under `cli/src/test/resources/scripts/`. Spin up a local relay (e.g. `nostr-rs-relay`), run Alice + Bob, assert await verbs resolve. |
| Interop with other clients | A separate harness repo consumes `amy` as a binary; out of scope for this module's own test suite but the JSON contract is what keeps it stable. |

**What not to test here:** event signing, filter assembly, MLS
correctness, NIP-44 encryption. Those belong in `quartz`/`commons`.
If an Amy bug can only be caught by a test here, it's likely a
contract violation (wrong key name, wrong exit code) rather than a
protocol bug.

**Setup hooks for a test suite:**
- Add a `testImplementation` block to `cli/build.gradle.kts`
  (`kotlin("test")`, `junit5`, `jackson`).
- Launch the CLI via `main(argv)` in-process for fast tests; launch
  the built `installDist` launcher for end-to-end tests.
- Use `@TempDir` for the data-dir so tests can run in parallel.

---

## 8. Distribution

Today Amy runs via `./gradlew :cli:run` or `installDist`. That is fine
for dogfooding but not for the interop-test audience, which needs a
single-binary install on every OS. Target matrix:

| OS / channel | Strategy | Notes |
|---|---|---|
| macOS (arm64 + x64) | `brew install amy-nostr` | Homebrew formula pointing at a tarball of the `installDist` tree + a native `jlink` runtime. Mirrors `amethyst-nostr` cask. |
| Windows | `winget install VitorPamplona.Amy` + `scoop install amy` | `.zip` with `amy.bat` launcher + jlink runtime. |
| Debian / Ubuntu | `.deb` via `jpackage --type deb` | Depends on libc only; jlink runtime bundled. |
| Fedora / RHEL / openSUSE | `.rpm` via `jpackage --type rpm` | Same. |
| Arch | AUR `amy-nostr-bin` | Wrap the `.tar.gz`. |
| Any Linux | `.tar.gz` + AppImage | AppImage for users without a package manager. |
| Nix / NixOS | `nixpkgs` entry | Straightforward wrapper around `installDist`. |
| Zapstore | `zapstore.yaml` entry (already exists for Amethyst) | Signed by the same Nostr key as the Android app. |
| GitHub Release | Every version ships the above as release assets | Use `scripts/asset-name.sh` for consistent naming. |

**Shortest path:** extend the existing desktop `jpackage` flow in
`desktopApp/` to also produce an `amy-<version>-<os>-<arch>` artefact.
No native image yet — GraalVM `native-image` is tempting for startup
time but loses FFI to `secp256k1-kmp-jni-*`; revisit once Quartz has a
pure-Kotlin signer fallback.

**Auto-update:** out of scope for v1. Package managers handle it.

**Size budget:** target < 80 MB installed with a jlink'd runtime. If
we cross that, audit transitive deps — Amy should not pull in Compose
or Android libs.

---

## 9. Roadmap (order of operations)

A rough proposed sequencing. Each step is a PR.

1. **Event rendering core** (`commons/commonMain/.../rendering/`) with
   renderers for kind:0 / 1 / 3 / 6 / 7 / 10002 / 10050. Unblocks
   everything in §4 marked 🆕.
2. **`amy note publish` / `amy note show` / `amy note react`.** Smallest
   possible end-to-end write+read loop outside of Marmot.
3. **`amy feed home|profile|hashtag|thread`** reading through the
   renderer.
4. **`amy follow add|remove|list`** (NIP-02) — proves extraction of
   list-building logic from `amethyst/model/`.
5. **`amy dm send|list`** (NIP-17) — reuses the gift-wrap path already
   exercised by Marmot.
6. **`amy list bookmarks|mute|pin …`** (NIP-51).
7. **`amy zap send|verify`** (NIP-57).
8. **Distribution** — Homebrew + Scoop + `.deb` in the same release
   pipeline as desktop.
9. **Test suite** end-to-end against a local relay.
10. **Everything else** in §4.

Each step should extract at least one file from `amethyst/` into
`commons/`. If a step doesn't move anything, it's probably duplicating
logic — re-audit.

---

## 10. House-keeping

- Run `./gradlew spotlessApply` before every commit.
- Keep `printUsage()` in `Main.kt` in sync with the command table in
  [README.md](./README.md) and §4 above — the three drift apart fast.
- Never add a Gradle dependency on `amethyst/` or `desktopApp/`. If you
  need something from there, move it to `commons/`.
- Never introduce a blocking prompt (`readLine()`, interactive
  password input). Take it as a flag.
- Keep each command file small. When one grows past ~200 lines,
  split it — the Marmot `group` verbs are already a cautionary tale.
