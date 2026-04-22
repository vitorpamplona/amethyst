# Command-file template

Copy this shape for every new Amy verb. Resist the urge to deviate —
the uniform shape is what makes commands easy to audit and test.

## Single-verb command

```kotlin
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json

object NotePublishCommand {
    suspend fun run(dataDir: DataDir, rest: Array<String>): Int {
        val args = Args(rest)
        val text = args.positional(0, "text")

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()

            val event = com.vitorpamplona.amethyst.commons.note
                .buildTextNote(ctx.signer, text)
            val ack = ctx.publish(event, ctx.outboxRelays())

            Json.writeLine(mapOf(
                "event_id"      to event.id,
                "kind"          to event.kind,
                "published_to"  to ack.filterValues { it }.keys.map { it.url },
                "rejected_by"   to ack.filterValues { !it }.keys.map { it.url },
            ))
            return 0
        } finally {
            ctx.close()
        }
    }
}
```

## Multi-verb group

When a feature has several verbs (`note publish`, `note show`,
`note react`), group them:

```kotlin
object NoteCommands {
    suspend fun dispatch(dataDir: DataDir, tail: Array<String>): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "note <publish|show|react>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "publish" -> NotePublishCommand.run(dataDir, rest)
            "show"    -> NoteShowCommand.run(dataDir, rest)
            "react"   -> NoteReactCommand.run(dataDir, rest)
            else      -> Json.error("bad_args", "note ${tail[0]}")
        }
    }
}
```

Each verb gets its own file. Once a single file crosses ~200 lines,
split it — see `GroupCommands.kt` and its siblings as the reference.

## Wire-up checklist

For every new command:

1. File under `cli/commands/`.
2. Branch in `Commands.kt`:
   ```kotlin
   suspend fun note(dataDir: DataDir, tail: Array<String>): Int =
       NoteCommands.dispatch(dataDir, tail)
   ```
3. Branch in `Main.kt`'s top-level `dispatch`:
   ```kotlin
   "note" -> Commands.note(dataDir, tail)
   ```
4. Line in `printUsage()` explaining the verb.
5. Row in `cli/README.md`'s command table.
6. Status flip in `cli/ROADMAP.md` (🆕 / 📦 → ✅).

## What not to do

- No `runBlocking` in a command body — `main()` already does it.
- No `println` / `print` for command output — use
  `Json.writeLine(...)`. `System.err.println(...)` is fine for
  progress logs (they're already disposable).
- No swallowing errors — let exceptions bubble; `main()` translates
  them to `{"error":...}` + exit code.
- No holding a connection open across invocations — every run opens
  a fresh `Context` and closes it in `finally`.
- No blocking reads for user input — take a flag.

## Output-shape rules

See `output-conventions.md`.
