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
import com.vitorpamplona.amethyst.cli.secrets.SecretStore
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
 *   1 — runtime error
 *   2 — invalid arguments
 *   124 — await timeout
 *
 * Default output is human-readable text on stdout. Pass `--json` to
 * switch to the machine contract: a single JSON object on stdout per
 * successful command, JSON `{"error": "...", "detail": "..."}` on stderr
 * for failures. The JSON shape is amy's stable public API; the text
 * shape is not. Diagnostic logs always go to stderr.
 */
fun main(argv: Array<String>) {
    // Set output mode before dispatch so even argument-parsing errors
    // honour --json.
    if (argv.any { it == "--json" || it == "--json=true" }) {
        Output.mode = Output.Mode.JSON
    }
    val code =
        try {
            runBlocking { dispatch(argv) }
        } catch (e: IllegalArgumentException) {
            Output.error("bad_args", e.message)
            2
        } catch (e: AwaitTimeout) {
            Output.error("timeout", e.message)
            124
        } catch (e: Exception) {
            Output.error("runtime", "${e::class.simpleName}: ${e.message}")
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

    // Pull global flags out of argv before subcommand parsing so subcommands see
    // only their own args.
    val filteredArgs = mutableListOf<String>()
    var dataDirFlag: String? = null
    var nameFlag: String? = null
    var secretBackendFlag: String? = null
    var passphraseFileFlag: String? = null
    var i = 0
    while (i < argv.size) {
        val a = argv[i]
        val (matched, consumed) = extractGlobalFlag(a, argv, i)
        when (matched) {
            GlobalFlag.DATA_DIR -> dataDirFlag = consumed.value
            GlobalFlag.NAME -> nameFlag = consumed.value
            GlobalFlag.SECRET_BACKEND -> secretBackendFlag = consumed.value
            GlobalFlag.PASSPHRASE_FILE -> passphraseFileFlag = consumed.value
            GlobalFlag.JSON -> Output.mode = Output.Mode.JSON
            null -> filteredArgs.add(a)
        }
        i += consumed.tokensConsumed
    }
    if (filteredArgs.isEmpty()) {
        printUsage()
        return 2
    }

    val head = filteredArgs[0]
    val tail = filteredArgs.drop(1).toTypedArray()

    // `use` operates on `<root>/current` directly and must work even
    // when account auto-pick would fail (the whole point of `use` is to
    // resolve "multiple accounts, ambiguous" cases) — so it skips
    // DataDir.resolve. Other commands fall through to the normal path.
    if (head == "use") {
        return com.vitorpamplona.amethyst.cli.commands.UseCommand
            .run(tail)
    }

    val secrets = SecretStore.from(backendFlag = secretBackendFlag, passphraseFile = passphraseFileFlag)
    val dataDir = DataDir.resolve(dataDirFlag = dataDirFlag, nameFlag = nameFlag, secrets = secrets)

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

        "profile" -> {
            Commands.profile(dataDir, tail)
        }

        "notes" -> {
            Commands.notes(dataDir, tail)
        }

        "store" -> {
            Commands.store(dataDir, tail)
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

private enum class GlobalFlag(
    val long: String,
    val takesValue: Boolean = true,
) {
    DATA_DIR("--data-dir"),
    NAME("--name"),
    SECRET_BACKEND("--secret-backend"),
    PASSPHRASE_FILE("--passphrase-file"),
    JSON("--json", takesValue = false),
}

private data class ConsumedFlag(
    val value: String?,
    val tokensConsumed: Int,
)

/**
 * Match a single argv token against the global-flag whitelist. Returns
 * `(matchedFlag, parsed)` — when [matchedFlag] is null the token is a
 * subcommand/positional that the caller should forward untouched.
 */
private fun extractGlobalFlag(
    token: String,
    argv: Array<String>,
    idx: Int,
): Pair<GlobalFlag?, ConsumedFlag> {
    for (flag in GlobalFlag.values()) {
        if (token == flag.long) {
            return if (flag.takesValue) {
                flag to ConsumedFlag(argv.getOrNull(idx + 1), 2)
            } else {
                flag to ConsumedFlag(null, 1)
            }
        }
        val prefix = "${flag.long}="
        if (token.startsWith(prefix)) {
            return flag to ConsumedFlag(token.removePrefix(prefix), 1)
        }
    }
    return null to ConsumedFlag(null, 1)
}

private fun printUsage() {
    System.err.println(
        """
        |amy — Amethyst command-line interface
        |
        |Usage:
        |  amy [--name ACCOUNT]                  (canonical: per-account dir under ~/.amy/)
        |      [--data-dir PATH]                 (escape hatch: self-contained dir at PATH)
        |      [--secret-backend auto|keychain|ncryptsec|plaintext]
        |      [--passphrase-file PATH]
        |      [--json]
        |      <cmd> [args...]
        |
        |Account selection:
        |  Default layout lives at ~/.amy/, with shared/events-store/ holding
        |  every observed Nostr event and ~/.amy/<account>/ holding identity,
        |  cursors, MLS state, and aliases. ACCOUNT must match
        |  [a-zA-Z0-9_-]{1,64} (no spaces, no slashes).
        |
        |  Resolution order when --data-dir is not set:
        |    1. --name X if given.
        |    2. ~/.amy/current marker (set by `amy use X`).
        |    3. Sole subdirectory of ~/.amy/ other than shared/.
        |    4. Error — disambiguate with --name or `amy use`.
        |
        |  use NAME                                  pin NAME as the active account
        |  use --clear                                remove the pin
        |  use                                        print current pin + available accounts
        |
        |Output:
        |  Default: human-readable text on stdout.
        |  --json:  one JSON object per success on stdout, JSON
        |           {"error":...,"detail":...} on stderr for failures
        |           (the stable machine-readable contract — exit codes
        |           0 success / 1 error / 2 bad args / 124 timeout).
        |
        |Private-key storage:
        |  Default (`auto`) uses the OS keychain when one is available
        |  (macOS `security`, or Linux `secret-tool` on a session D-Bus)
        |  and falls back to a NIP-49 ncryptsec blob otherwise. For the
        |  ncryptsec backend the passphrase is taken from --passphrase-file,
        |  then ${'$'}AMY_PASSPHRASE, then a TTY prompt. `plaintext` writes the
        |  private key directly into identity.json (still 0600) — dev only.
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
        |Profile (NIP-01 kind:0):
        |  profile show [USER] [--timeout SECS]       fetch latest kind:0 metadata
        |                                              (USER: npub|nprofile|hex|name@domain)
        |  profile edit [--name NAME]                  patch kind:0; unset flags keep prior values,
        |               [--display-name N]             blank values delete the field
        |               [--about TEXT]
        |               [--picture URL] [--banner URL]
        |               [--website URL] [--nip05 ID]
        |               [--lud16 X] [--lud06 X]
        |               [--pronouns P]
        |               [--twitter H] [--mastodon H] [--github H]
        |               [--timeout SECS]
        |
        |Notes (NIP-10 kind:1):
        |  notes post TEXT [--relay URL]               publish a kind:1 short text note
        |                                              (--relay accepts comma-separated extras)
        |  notes feed [--author USER]                  fetch kind:1 notes
        |             [--following]                    (default: own; --author: one user;
        |             [--limit N]                       --following: every contact-list pubkey)
        |             [--since TS] [--until TS]
        |             [--timeout SECS]
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
        |
        |Local event store (`<data-dir>/events-store/`):
        |  store stat                                 event count, kind histogram, disk usage
        |  store sweep-expired                        delete events past their NIP-40 expiration
        |  store scrub                                rebuild idx/ from canonical events (after edits / crashes)
        |  store compact                              drop dangling idx entries (canonical gone)
        """.trimMargin(),
    )
}
