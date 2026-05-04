---
name: amy-expert
description: Patterns for extending `amy`, the Amethyst CLI in `cli/`. Use when adding an `amy <verb>` command, touching files under `cli/src/main/kotlin/…/cli/`, wiring a new subcommand into `Main.kt`, writing an interop test script that drives Amy, or extracting logic out of `amethyst/` into `commons/` so a CLI command can call it. Enforces the thin-assembly-layer rule (no Nostr protocol or business logic inside `cli/`), the dual-output contract (text by default, single-line JSON object on stdout under `--json`, exit codes 0/1/2/124), and the extract-from-Android recipe. Complements `nostr-expert` (protocol in Quartz), `kotlin-multiplatform` (expect/actual for extraction), and `feed-patterns` / `account-state` / `relay-client` (where the business logic should end up). NOT for general Nostr or Kotlin work — those have their own skills.
---

# Amy CLI Expert

Practical patterns for touching the `cli/` module without breaking
its public contract.

## When to use this skill

- Adding a new `amy <verb>` subcommand.
- Editing anything under `cli/src/main/kotlin/…/cli/`.
- Writing a shell script or test harness that drives Amy.
- Extracting code out of `amethyst/` so the CLI can call it (this is
  the single most common reason an Amy feature request stalls).
- Deciding whether a piece of logic belongs in `cli/` vs `commons/`
  vs `quartz/` (answer: almost never `cli/`).

**Not for:** general Nostr protocol work (`nostr-expert`), general
Kotlin (`kotlin-expert`), Compose UI (`compose-expert`), Android-only
flows (`android-expert`), gradle/build (`gradle-expert`).

## The rules that matter

Amy has a small number of hard rules. Any change that breaks them is
a breaking change to the CLI's public API, and breaks the interop-
test harnesses that depend on it.

### Rule 1 — `cli/` is a thin assembly layer

No new Nostr protocol, filter assembly, state machines, or encryption
lives in `cli/`. Ever. If you need logic that doesn't exist yet:

- Protocol piece (event kind, tags, signing)? Add it to `quartz/`.
- Business logic (state, defaults, ordering, filter assembly)?
  Add it to `commons/` — extract from `amethyst/` first if needed
  (see Rule 5).

A `commands/*.kt` file longer than ~200 lines is a code smell.
Either the command is doing too many things, or the logic has
leaked in from where it should have lived.

### Rule 2 — text by default, `--json` is the machine contract

amy ships a dual-output contract:

- **Default stdout is human-readable text.** A YAML-ish render of the
  result map. No shape promise — the renderer can change between
  releases.
- **`--json` switches stdout to one JSON object, one line.** Stable
  snake_case keys; this shape is the public API.
- **stderr is for humans.** Progress logs, warnings, per-relay ACK
  traces. Errors go here too — `error: <code>: <detail>` by default,
  JSON `{"error":"…","detail":"…"}` under `--json`.
- **Exit codes:** `0` success · `1` runtime · `2` bad args · `124`
  await timeout.
- Adding a `--json` key is safe; renaming or removing one is a
  breaking change and needs the commit message to say so.

Commands emit results via `Output.emit(mapOf(...))` and errors via
`Output.error("code", "detail")`. The `Output` object (in
`cli/src/main/kotlin/…/cli/Output.kt`) handles the text-vs-JSON
branching automatically. Never `println(...)` user-facing output
directly — `System.err.println(...)` is fine for progress logs only.

See `references/output-conventions.md`.

### Rule 3 — Non-interactive, ever

No `readLine()`, no TTY prompts, no hidden interactive behaviour.
Passwords, names, keys, anything — all flags. Any network wait is
an explicit `await` verb with `--timeout`.

### Rule 4 — `~/.amy/` is the whole world

State is reloaded from `~/.amy/` on every invocation. No singletons,
no in-process caches that survive across runs. This is what lets 100
parallel interop scenarios share a harness safely.

The layout:

- `~/.amy/shared/events-store/` — one file-backed Nostr event store
  per machine, shared across every account.
- `~/.amy/<account>/` — per-account dir: `identity.json`,
  `state.json`, `aliases.json`, `marmot/`.
- `~/.amy/current` — marker file written by `amy use NAME` to pin
  the active account.

Account selection is via the global `--account NAME` flag (required
when more than one account exists; auto-picked when exactly one
does). `--account` cannot collide with subcommand flags, so commands
like `marmot group create --name "Group"` or `profile edit --name "Alice"`
keep their own `--name` parameter.

Tests isolate by overriding `$HOME` for the amy subprocess
(`HOME=$(mktemp -d) amy --account alice init`). amy reads `$HOME`
directly (not `user.home`, which JDK 21 derives from `getpwuid` and
ignores `$HOME`), so the same convention `git`/`gpg`/`npm`/`ssh`
follow Just Works.

If you need new persisted state, add it to `Config.kt`,
`stores/FileStores.kt`, or a new helper (e.g. `Aliases.kt`) with a
named JSON schema. Don't smuggle state into `~/.amy/` outside the
documented files.

### Rule 5 — Extract before adding

If the command you're about to add needs logic from `amethyst/`,
land the extraction first, in its own commit:

1. Identify the class in `amethyst/src/main/java/…/`.
2. List its Android-only dependencies (`Context`, `SharedPreferences`,
   `WorkManager`, `Log`, `Bitmap`, `Uri`, …).
3. For each, choose: inline, platform-abstract via expect/actual, or
   take-as-constructor-arg.
4. Move the file to `commons/commonMain/…`.
5. Update the Android caller to use the new location. Add a JVM test.
6. **Then** add the `cli/commands/…` file.

Full checklist: `references/extraction-recipe.md`.

## Standard command shape

Every new command follows the same shape — parse args, open Context,
prepare, call into commons/quartz, publish or drain, emit one result
via `Output.emit`. The template is in `references/command-template.md`;
copy it rather than re-deriving it.

Wire-up checklist:
1. New file in `cli/commands/` with the `object` pattern.
2. Add a branch in `Commands.kt`.
3. Add a branch in `Main.kt`'s `dispatch` (or under `marmotDispatch`
   / a new group dispatcher).
4. Extend `printUsage()` in `Main.kt`.
5. Add the row to `cli/README.md`'s command table.
6. Update `cli/ROADMAP.md` — move the row from 🆕 / 📦 to ✅.
7. If the verb changes observable wire behaviour (a new event kind,
   a new relay-routing rule, a new JSON discriminator), add a case
   in the appropriate harness under `cli/tests/` — `cli/tests/marmot/`
   for MLS flows, `cli/tests/dm/` for NIP-17, `cli/tests/cache/` for
   event-store behaviour, or a new sibling suite if it's none.

If you change `--json` output shape: note it in the commit message,
bump the example in `cli/README.md`, update any interop fixtures
under `cli/tests/`.

## Where things live

```
cli/
├── README.md              # user-facing tour: install, examples, command tables
├── DEVELOPMENT.md         # public contract, architecture, design rules,
│                          #   event-store, relay-routing, full on-disk layout
├── ROADMAP.md             # parity matrix + ordered milestones
├── plans/                 # dated design docs (use for new subsystems)
├── tests/                 # end-to-end shell harnesses against a local relay
│   ├── lib.sh             # shared logging + result tracking
│   ├── headless/          # shared amy wrappers + assertions
│   ├── marmot/            # MLS group-messaging interop (vs whitenoise-rs)
│   ├── dm/                # NIP-17 DM interop (two amy clients)
│   └── cache/             # FsEventStore behaviour vs the cache helpers
└── src/main/kotlin/…/cli/
    ├── Main.kt            # argv dispatch, global flags
    ├── Args.kt            # flag parser
    ├── Output.kt          # text/json mode emitter + colour
    ├── Aliases.kt         # per-account aliases.json read/write
    ├── Config.kt          # Identity, RunState, DataDir (~/.amy layout)
    ├── Context.kt         # per-run wiring — the backbone
    ├── SecureFileIO.kt    # 0600/0700 atomic writes, perm tighten
    ├── stores/            # file-backed MLS / KP / message stores
    ├── secrets/           # SecretStore backends (keychain / ncryptsec / plaintext)
    └── commands/          # one file (or group) per top-level verb
        ├── UseCommand.kt          # `amy use NAME`
        ├── InitCommands.kt        # init, whoami
        ├── CreateCommand.kt + LoginCommand.kt
        ├── RelayCommands.kt
        ├── ProfileCommands.kt
        ├── NotesCommands.kt + PostCommand.kt + FeedCommand.kt
        ├── DmCommands.kt
        ├── KeyPackageCommands.kt
        ├── GroupCommands.kt + GroupCreateCommand.kt + GroupReadCommands.kt
        │   GroupAddMemberCommand.kt + GroupMembershipCommands.kt
        │   GroupMetadataCommands.kt
        ├── MessageCommands.kt
        ├── MarmotResetCommand.kt
        ├── AwaitCommands.kt
        └── StoreCommands.kt
```

Shared logic consumed by Amy lives in `commons/`:
- `commons/account/` — account bootstrap
- `commons/marmot/` — MLS / group state
- `commons/defaults/` — default relays, kinds
- Consult `commons/plans/` for cross-cutting design work in flight.

## Common mistakes to refuse

- **Adding protocol logic to `cli/`.** Push back, offer to extract.
- **Silently changing a `--json` key.** Flag as breaking.
- **Using `println` or `print` for command output.** Use
  `Output.emit(...)` / `Output.error(...)`. Plain
  `System.err.println` is fine for progress logs but never for
  user-consumable output.
- **`runBlocking` inside a command** — the top-level `main` already
  does that. Commands are `suspend fun`.
- **Depending on `:amethyst` or `:desktopApp`.** Never. If you need
  something from there, Rule 5.
- **Re-inventing identifier parsing.** Use `Context.requireUserHex`
  or `resolveUserHexOrNull` in `quartz/nip05DnsIdentifiers/`.
- **Re-inventing publish-and-confirm.** Use `Context.publish`.
- **Re-inventing one-shot subscription.** Use `Context.drain`.
- **Reading `user.home` directly.** Use `DataDir.DEFAULT_ROOT`, which
  reads `$HOME` (the convention `git`/`gpg`/`npm` follow); JDK 21's
  `user.home` is derived from `getpwuid` and ignores `$HOME`, which
  silently breaks the test-isolation pattern.
- **Adding a global flag that collides with subcommand flags.**
  `--name` is reserved for subcommand use (group/profile names).
  Account selection is `--account`.

## Plans & design docs

Cross-cutting design work goes in dated plan docs, in the module
that owns the code being created — not in `docs/plans/`, which is
frozen.

- `cli/plans/` — Amy-specific subsystems.
- `commons/plans/` — shared code Amy consumes (e.g.
  `2026-04-21-event-renderer.md`).

## Cross-references

- [`cli/README.md`](../../../cli/README.md) — user-facing tour
- [`cli/DEVELOPMENT.md`](../../../cli/DEVELOPMENT.md) — public
  contract, architecture, on-disk layout
- [`cli/ROADMAP.md`](../../../cli/ROADMAP.md) — parity matrix
- `references/command-template.md`
- `references/extraction-recipe.md`
- `references/output-conventions.md`
