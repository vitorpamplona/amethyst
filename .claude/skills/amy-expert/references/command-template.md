# Command-file template

Copy this shape for every new Amy verb. Resist the urge to deviate —
the uniform shape is what makes commands easy to audit and test.

## Single-verb command

```kotlin
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output

object NotePublishCommand {
    suspend fun run(dataDir: DataDir, rest: Array<String>): Int {
        val args = Args(rest)
        val text = args.positional(0, "text")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val event = com.vitorpamplona.amethyst.commons.note
                .buildTextNote(ctx.signer, text)
            val ack = ctx.publish(event, ctx.outboxRelays())

            Output.emit(mapOf(
                "event_id"      to event.id,
                "kind"          to event.kind,
                "published_to"  to ack.filterValues { it }.keys.map { it.url },
                "rejected_by"   to ack.filterValues { !it }.keys.map { it.url },
            ))
            return 0
        }
    }
}
```

`Context` is `AutoCloseable`; wrap it in `use { }` so it's closed
(RunState flushed, relays disconnected) on every exit path — never a
hand-rolled `try { } finally { ctx.close() }`.

`Output.emit(...)` handles the text-vs-JSON mode automatically. The
result map IS the `--json` shape; the human-readable text default is
derived from the same map by `Output.kt`'s renderer.

## Multi-verb group

When a feature has several verbs (`note publish`, `note show`,
`note react`), group them:

```kotlin
object NoteCommands {
    suspend fun dispatch(dataDir: DataDir, tail: Array<String>): Int =
        route("note", tail, "note <publish|show|react>", mapOf(
            "publish" to { rest -> NotePublishCommand.run(dataDir, rest) },
            "show"    to { rest -> NoteShowCommand.run(dataDir, rest) },
            "react"   to { rest -> NoteReactCommand.run(dataDir, rest) },
        ))
}
```

The shared `route(name, tail, usage, routes)` helper (`Router.kt`)
handles the empty-input and unknown-verb `bad_args` branches, so the
`dispatch` body is just the verb→handler map. Each verb gets its own
file. Once a single file crosses ~200 lines, split it — see
`GroupCommands.kt` and its siblings as the reference.

## Wire-up checklist

For every new command:

1. File under `cli/commands/`.
2. Branch in `Main.kt`'s top-level `dispatch`, calling the command
   object directly:
   ```kotlin
   "note" -> NoteCommands.dispatch(dataDir, tail)
   ```
3. Line in `printUsage()` explaining the verb.
4. Row in `cli/README.md`'s command table.
5. Status flip in `cli/ROADMAP.md` (🆕 / 📦 → ✅).

## What not to do

- No `runBlocking` in a command body — `main()` already does it.
- No `println` / `print` for command output — use
  `Output.emit(...)` / `Output.error(...)`. `System.err.println(...)`
  is fine for progress logs (they're already disposable).
- No swallowing errors — let exceptions bubble; `main()` translates
  them to `error: …` (text mode) / `{"error":…}` (JSON mode) plus the
  right exit code.
- No holding a connection open across invocations — every run opens
  a fresh `Context` inside `use { }` so it closes on every exit path.
- No blocking reads for user input — take a flag.
- No global flags that collide with subcommand flags. `--name` is
  reserved for subcommand use (group/profile name); the global
  account selector is `--account`.

## Output-shape rules

See `output-conventions.md`.
