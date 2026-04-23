---
name: amy-expert
description: Patterns for extending `amy`, the Amethyst CLI in `cli/`. Use when adding an `amy <verb>` command, touching files under `cli/src/main/kotlin/…/cli/`, wiring a new subcommand into `Main.kt`, writing an interop test script that drives Amy, or extracting logic out of `amethyst/` into `commons/` so a CLI command can call it. Enforces the thin-assembly-layer rule (no Nostr protocol or business logic inside `cli/`), the JSON-output contract (single-line object on stdout, exit codes 0/1/2/124), and the extract-from-Android recipe. Complements `nostr-expert` (protocol in Quartz), `kotlin-multiplatform` (expect/actual for extraction), and `feed-patterns` / `account-state` / `relay-client` (where the business logic should end up). NOT for general Nostr or Kotlin work — those have their own skills.
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

### Rule 2 — stdout JSON, stderr humans

- Every success: one line, one JSON object on stdout, via
  `Json.writeLine(mapOf(...))`.
- Every failure: `Json.error("code", "detail")` → single-line JSON
  on stderr, non-zero exit.
- Exit codes: `0` success · `1` runtime · `2` bad args · `124` await
  timeout.
- Keys are stable, snake_case. Adding a key is safe; renaming or
  removing one is a breaking change and needs the commit message to
  say so.

See `references/output-conventions.md`.

### Rule 3 — Non-interactive, ever

No `readLine()`, no TTY prompts, no hidden interactive behaviour.
Passwords, names, keys, anything — all flags. Any network wait is
an explicit `await` verb with `--timeout`.

### Rule 4 — Data-dir is the whole world

State is reloaded from `--data-dir PATH` on every invocation. No
singletons, no in-process caches that survive across runs. This is
what lets 100 parallel interop scenarios share a harness safely.

Files live in well-known locations — see
`cli/README.md § Data-dir layout`. Don't add unstructured state; if
you need new persisted state, add it to `Config.kt` or
`stores/FileStores.kt` with a named JSON schema.

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
prepare, call into commons/quartz, publish or drain, emit one JSON
line. The template is in `references/command-template.md`; copy it
rather than re-deriving it.

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
   for MLS flows, `cli/tests/dm/` for NIP-17, or a new sibling suite
   if it's neither.

If you change output shape: note it in the commit message, bump the
example in `README.md`, update any interop fixtures under
`cli/tests/`.

## Where things live

```
cli/
├── README.md              # user-facing: commands, JSON contract, quick start
├── DEVELOPMENT.md         # touch-the-code: architecture, conventions, testing
├── ROADMAP.md             # parity matrix + ordered milestones
├── plans/                 # dated design docs (use for new subsystems)
├── tests/                 # end-to-end shell harnesses against a local relay
│   ├── lib.sh             # shared logging + result tracking
│   ├── headless/          # shared amy wrappers + assertions
│   ├── marmot/            # MLS group-messaging interop (vs whitenoise-rs)
│   └── dm/                # NIP-17 DM interop (two amy clients)
└── src/main/kotlin/…/cli/
    ├── Main.kt            # argv dispatch
    ├── Args.kt            # flag parser
    ├── Json.kt            # stdout/stderr JSON
    ├── Config.kt          # Identity, RelayConfig, RunState, DataDir
    ├── Context.kt         # per-run wiring — the backbone
    ├── stores/            # file-backed persistence
    └── commands/          # one file per top-level verb group
```

Shared logic consumed by Amy lives in `commons/`:
- `commons/account/` — account bootstrap
- `commons/marmot/` — MLS / group state
- `commons/defaults/` — default relays, kinds
- Consult `commons/plans/` for cross-cutting design work in flight.

## Common mistakes to refuse

- **Adding protocol logic to `cli/`.** Push back, offer to extract.
- **Silently changing a JSON key.** Flag as breaking.
- **Using `println` or `print`.** Use `Json.writeLine` / `Json.error`.
  Plain `System.err.println` is fine for progress logs but never for
  user-consumable output.
- **`runBlocking` inside a command** — the top-level `main` already
  does that. Commands are `suspend fun`.
- **Depending on `:amethyst` or `:desktopApp`.** Never. If you need
  something from there, Rule 5.
- **Re-inventing identifier parsing.** Use `Context.requireUserHex`
  or `resolveUserHexOrNull` in `quartz/nip05DnsIdentifiers/`.
- **Re-inventing publish-and-confirm.** Use `Context.publish`.
- **Re-inventing one-shot subscription.** Use `Context.drain`.

## Plans & design docs

Cross-cutting design work goes in dated plan docs, in the module
that owns the code being created — not in `docs/plans/`, which is
frozen.

- `cli/plans/` — Amy-specific subsystems.
- `commons/plans/` — shared code Amy consumes (e.g.
  `2026-04-21-event-renderer.md`).

## Cross-references

- [`cli/README.md`](../../../cli/README.md)
- [`cli/DEVELOPMENT.md`](../../../cli/DEVELOPMENT.md)
- [`cli/ROADMAP.md`](../../../cli/ROADMAP.md)
- `references/command-template.md`
- `references/extraction-recipe.md`
- `references/output-conventions.md`
