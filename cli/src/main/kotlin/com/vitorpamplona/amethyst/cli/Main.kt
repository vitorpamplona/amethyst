/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.Commands
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * amy — non-interactive command-line interface to Amethyst.
 *
 * Today this covers the Marmot/MLS surface (`amy marmot …`) plus identity
 * and relay configuration at the root level. The layout is intentionally
 * extensible — future verbs (`amy dm`, `amy feed`, `amy profile`) slot in
 * as new top-level subcommands.
 *
 * Usage: amy --data-dir PATH SUBCOMMAND ARGS
 *
 * Exit codes:
 *   0 — success
 *   1 — runtime error (printed as JSON on stderr: {"error": "...", "detail": "..."})
 *   2 — invalid arguments
 *   124 — await timeout
 *
 * Every command that succeeds prints exactly one JSON object to stdout.
 * Diagnostic logs go to stderr and are safe to discard.
 */
fun main(argv: Array<String>) {
    val code =
        try {
            runBlocking { dispatch(argv) }
        } catch (e: IllegalArgumentException) {
            Json.error("bad_args", e.message)
            2
        } catch (e: AwaitTimeout) {
            Json.error("timeout", e.message)
            124
        } catch (e: Exception) {
            Json.error("runtime", "${e::class.simpleName}: ${e.message}")
            1
        }
    exitProcess(code)
}

class AwaitTimeout(
    message: String,
) : RuntimeException(message)

private suspend fun dispatch(argv: Array<String>): Int {
    if (argv.isEmpty() || argv[0] == "--help" || argv[0] == "-h") {
        printUsage()
        return 0
    }

    // Pull --data-dir out of argv before subcommand parsing so subcommands see
    // only their own args.
    val filteredArgs = mutableListOf<String>()
    var dataDirFlag: String? = null
    var i = 0
    while (i < argv.size) {
        when (val a = argv[i]) {
            "--data-dir" -> {
                dataDirFlag = argv.getOrNull(i + 1)
                i += 2
            }

            else -> {
                if (a.startsWith("--data-dir=")) {
                    dataDirFlag = a.removePrefix("--data-dir=")
                    i++
                } else {
                    filteredArgs.add(a)
                    i++
                }
            }
        }
    }
    if (filteredArgs.isEmpty()) {
        printUsage()
        return 2
    }

    val dataDir = DataDir.resolve(dataDirFlag)
    val head = filteredArgs[0]
    val tail = filteredArgs.drop(1).toTypedArray()

    return when (head) {
        "init" -> {
            Commands.init(dataDir, Args(tail))
        }

        "create" -> {
            Commands.create(dataDir, tail)
        }

        "login" -> {
            Commands.login(dataDir, tail)
        }

        "whoami" -> {
            Commands.whoami(dataDir)
        }

        "relay" -> {
            Commands.relay(dataDir, tail)
        }

        "marmot" -> {
            marmotDispatch(dataDir, tail)
        }

        "dm" -> {
            Commands.dm(dataDir, tail)
        }

        else -> {
            System.err.println("unknown subcommand: $head")
            printUsage()
            2
        }
    }
}

private suspend fun marmotDispatch(
    dataDir: DataDir,
    tail: Array<String>,
): Int {
    if (tail.isEmpty()) {
        printUsage()
        return 2
    }
    val head = tail[0]
    val rest = tail.drop(1).toTypedArray()
    return when (head) {
        "key-package" -> {
            Commands.keyPackage(dataDir, rest)
        }

        "group" -> {
            Commands.group(dataDir, rest)
        }

        "message" -> {
            Commands.message(dataDir, rest)
        }

        "await" -> {
            Commands.await(dataDir, rest)
        }

        "reset" -> {
            Commands.reset(dataDir, rest)
        }

        else -> {
            System.err.println("unknown marmot subcommand: $head")
            printUsage()
            2
        }
    }
}

private fun printUsage() {
    System.err.println(
        """
        |amy — Amethyst command-line interface
        |
        |Usage:
        |  amy [--data-dir PATH] <cmd> [args...]
        |
        |Identity:
        |  init [--nsec NSEC]           create or import a bare identity (no defaults published)
        |  create [--name NAME]         provision a full Amethyst-style account + publish bootstrap events
        |  login KEY [--password X]     import (nsec|ncryptsec|mnemonic|npub|nprofile|hex|nip05)
        |  whoami                       print current identity
        |
        |Relays:
        |  relay add URL [--type T]      T=nip65|inbox|key_package|all (default all)
        |  relay list                    print configured relays
        |  relay publish-lists           publish kind:10002 + kind:10050
        |
        |Direct messages (NIP-17):
        |  dm send RECIPIENT TEXT                     send a gift-wrapped DM
        |    [--allow-fallback]                       (default: only deliver to recipient's kind:10050)
        |  dm send-file RECIPIENT --file PATH         encrypt + upload to Blossom + publish kind:15
        |    --server URL [--mime-type M]
        |  dm send-file RECIPIENT URL --key HEX       reference-mode: file already uploaded
        |    --nonce HEX [--mime-type M] [--hash H]
        |    [--size N] [--dim WxH] [--blurhash S]
        |  dm list [--peer NPUB] [--since TS]         list decrypted DMs (kind:14 text +
        |          [--limit N] [--timeout SECS]       kind:15 file with `type` discriminator)
        |  dm await --peer NPUB --match TEXT          wait for a matching DM
        |           [--timeout SECS]                  (default 30s, exit 124 on timeout)
        |
        |Marmot (MLS group messaging):
        |  marmot key-package publish                 publish a fresh KeyPackage
        |  marmot key-package check NPUB              fetch NPUB's KeyPackage from relays
        |
        |  marmot group create [--name NAME]          create an empty group (self-only)
        |  marmot group list                          list joined groups
        |  marmot group show GID                      print full group details
        |  marmot group members GID                   print members
        |  marmot group admins GID                    print admins
        |  marmot group add GID NPUB [NPUB...]        fetch KPs and invite
        |  marmot group rename GID NAME               commit a rename
        |  marmot group promote GID NPUB              add admin
        |  marmot group demote GID NPUB               remove admin
        |  marmot group remove GID NPUB               remove member
        |  marmot group leave GID                     self-remove
        |
        |  marmot message send GID TEXT               publish kind:9 inner event into the group
        |  marmot message list GID [--limit N]        dump decrypted inner events
        |  marmot message react GID EVENT_ID EMOJI    publish kind:7 reaction targeting an inner event
        |  marmot message delete GID EVENT_ID…        publish kind:5 deletion targeting inner events
        |
        |  marmot await key-package NPUB              (all await verbs take --timeout SECS, default 30;
        |  marmot await group --name NAME              exit 124 on timeout)
        |  marmot await member GID NPUB
        |  marmot await admin GID NPUB
        |  marmot await message GID --match TEXT
        |  marmot await rename GID --name NAME
        |  marmot await epoch GID --min N
        |
        |  marmot reset [--yes]                       wipe all local MLS/KeyPackage state (destructive)
        """.trimMargin(),
    )
}
