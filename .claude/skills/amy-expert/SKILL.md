---
name: amy-expert
description: amy CLI in cli/: adding an `amy <verb>` command, Main.kt dispatch, Output.emit/--json contract, exit codes 0/1/2/124, ~/.amy layout, cli/tests harnesses. Use when touching cli/. Enforces the thin-assembly rule: logic goes to commons/quartz.
---

# amy CLI

`cli/` is a non-interactive JVM client over `quartz` + `commons`. Its `--json` output and exit
codes are a public API that the interop harnesses depend on. The long-form contract is
`cli/DEVELOPMENT.md` ("Public contract", "How to add a command", "Output-shape conventions",
"Testing", "Full on-disk layout"); this skill is the short version plus the traps.

## Rules

1. **Thin assembly layer.** No protocol, filter assembly, state machines or encryption in `cli/`.
   Protocol goes to `quartz/`; business logic to `commons/`, extracted from `amethyst/` first in its
   own commit if needed (`references/extraction-recipe.md`). A `commands/*.kt` file over ~200 lines
   is a smell. `cli` may depend on `:geode` (for `amy serve`), never on `:commonsUI`, `:amethyst` or
   `:desktopApp`.
2. **Output goes through `Output`** (`cli/src/main/kotlin/…/cli/Output.kt`).
   - `Output.emit(mapOf(...))`: the map **is** the `--json` shape (one line, one object, stable
     snake_case keys); the default text render comes from the same map and has no contract.
   - `return Output.error("code", "detail")`: the return value is the exit code (`bad_args` 2,
     `timeout` 124, anything else 1). Errors go to stderr (`error: code: detail`, or a JSON object
     under `--json`).
   - Exit codes `0` ok, `1` runtime, `2` bad args, `124` await timeout. `runCli` in `Main.kt` also
     maps a thrown `IllegalArgumentException` to 2 and `AwaitTimeout` to 124.
   - Never `println` results; `System.err.println` is for progress logs only.
   - Adding a `--json` key is safe. Renaming or removing one is breaking: say so in the commit, and
     update `cli/README.md` and the fixtures under `cli/tests/`.
   - Publishes: `RawEventSupport.publishGuard(ack, id)` then `+ RawEventSupport.ackFields(ack)`
     (`published_to` + `rejected_by: [{relay, reason}]`). Don't hand-roll the projection.
   - Reuse an error code from the canonical list in `cli/DEVELOPMENT.md` before minting one.
3. **Non-interactive.** Everything is a flag; a network wait is an explicit `await` verb with
   `--timeout`. The only TTY fallback is `PassphraseProvider` (after `--passphrase-file` and
   `$AMY_PASSPHRASE`); don't add more.
4. **`~/.amy/` is the whole state**, reloaded on every run; no in-process caches across runs.
   `~/.amy/shared/events-store/` (one event store for all accounts), `~/.amy/<account>/`
   (`identity.json`, `state.json`, `aliases.json`, `marmot/`), `~/.amy/current` (written by
   `amy use NAME`). Account selection is the global `--account NAME`; `--name` stays free for
   subcommands. New persisted state goes through `Config.kt` / `stores/FileStores.kt` / a named
   helper like `Aliases.kt`.
5. **`$HOME`, not `user.home`.** `DataDir.DEFAULT_ROOT` (`Config.kt`) reads `$HOME`; JDK 21's
   `user.home` comes from `getpwuid` and ignores it, which would break harness isolation
   (`HOME=$(mktemp -d) amy --account alice init`). JVM tests use the `amy.home` system property
   seam instead.

## Adding a command

Copy the shape of a real one, e.g. `commands/FollowCommand.kt`: a `USAGE` constant, parse with
`Args` (`positional`, `flag`, `longFlag`, …, then `args.rejectUnknown()`),
`Context.open(dataDir).use { ctx -> ctx.prepare(); … }`, call into commons/quartz, publish with
`ctx.publish` or read with `ctx.drain`, emit one map. Commands are `suspend fun`; `main` already
runs `runBlocking`. Multi-verb groups dispatch through `route(...)` in `commands/Router.kt`.

Wire-up: a branch in `Main.kt`'s `dispatch` (marmot sub-verbs go in `marmotDispatch`), lines in
`printUsage()` and `printVerbList()`, a row in `cli/README.md`, the status in `cli/ROADMAP.md`, and
a harness case under `cli/tests/` when wire behaviour changes.

Don't re-invent what exists: user identifiers via `ctx.requireUserHex` (or quartz
`nip05DnsIdentifiers/resolveUserHexOrNull`); rendering via commons `rendering/`
(`EventRendererRegistry`, `json/JsonEventFormatter`); event lookup and loading via commons
`relayClient/oneshot/` (`OneShotNoteCache`, `EventLocator`, `ThreadLoader`, `NotificationsLoader`,
over `OneShotRelayAccess`; amy's adapter is `NoteSupport.access(ctx)`); builders in commons
`actions/` and `model/`.

## Where things live

- `cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/`: `Main.kt` (dispatch, global flags,
  usage), `Args.kt`, `Output.kt`, `Config.kt` (`DataDir`, identity, state), `Context.kt` (per-run
  wiring), `stores/`, `secrets/`, and `commands/` (~110 files, one per verb or group, plus
  `cashu/`, `graperank/`, `trust/`). Run `ls` on `commands/` rather than trusting a list.
- `cli/README.md` (user tour), `cli/DEVELOPMENT.md` (contract), `cli/ROADMAP.md`, `cli/plans/`.
- Build: `./gradlew :cli:installDist` → `cli/build/install/amy/bin/amy`. JVM contract tests:
  `./gradlew :cli:test` (`ArgsTest`, `ExitCodeContractTest`, `JsonContractTest`, harness in
  `CliResult.kt`).
- `cli/tests/`: shell harnesses (`lib.sh`, `headless/helpers.sh`, one directory per suite: `dm/`,
  `marmot/`, `notes/`, `cache/`, `git/`, …), most against an embedded `amy serve` relay with a fresh
  `$HOME`. See `cli/tests/README.md`.
