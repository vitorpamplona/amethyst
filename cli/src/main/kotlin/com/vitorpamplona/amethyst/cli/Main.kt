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

import com.vitorpamplona.amethyst.cli.commands.AdminCommand
import com.vitorpamplona.amethyst.cli.commands.AwaitCommands
import com.vitorpamplona.amethyst.cli.commands.BlossomCommands
import com.vitorpamplona.amethyst.cli.commands.BunkerCommand
import com.vitorpamplona.amethyst.cli.commands.CountCommand
import com.vitorpamplona.amethyst.cli.commands.CreateCommand
import com.vitorpamplona.amethyst.cli.commands.DebitCommands
import com.vitorpamplona.amethyst.cli.commands.DecodeCommand
import com.vitorpamplona.amethyst.cli.commands.DecryptCommand
import com.vitorpamplona.amethyst.cli.commands.DmCommands
import com.vitorpamplona.amethyst.cli.commands.EncodeCommand
import com.vitorpamplona.amethyst.cli.commands.EncryptCommand
import com.vitorpamplona.amethyst.cli.commands.EventCommand
import com.vitorpamplona.amethyst.cli.commands.FetchCommand
import com.vitorpamplona.amethyst.cli.commands.FilterCommand
import com.vitorpamplona.amethyst.cli.commands.FollowCommand
import com.vitorpamplona.amethyst.cli.commands.GiftCommands
import com.vitorpamplona.amethyst.cli.commands.GitCommands
import com.vitorpamplona.amethyst.cli.commands.GrapeRankCommand
import com.vitorpamplona.amethyst.cli.commands.GroupCommands
import com.vitorpamplona.amethyst.cli.commands.InitCommands
import com.vitorpamplona.amethyst.cli.commands.KeyCommands
import com.vitorpamplona.amethyst.cli.commands.KeyPackageCommands
import com.vitorpamplona.amethyst.cli.commands.KindCommand
import com.vitorpamplona.amethyst.cli.commands.LoginCommand
import com.vitorpamplona.amethyst.cli.commands.MarmotResetCommand
import com.vitorpamplona.amethyst.cli.commands.MessageCommands
import com.vitorpamplona.amethyst.cli.commands.NamecoinCommand
import com.vitorpamplona.amethyst.cli.commands.NappletCommands
import com.vitorpamplona.amethyst.cli.commands.NipCommand
import com.vitorpamplona.amethyst.cli.commands.NotesCommands
import com.vitorpamplona.amethyst.cli.commands.NsiteCommands
import com.vitorpamplona.amethyst.cli.commands.OfferCommands
import com.vitorpamplona.amethyst.cli.commands.OutboxCommand
import com.vitorpamplona.amethyst.cli.commands.Podcast20Commands
import com.vitorpamplona.amethyst.cli.commands.PodcastCommands
import com.vitorpamplona.amethyst.cli.commands.ProfileCommands
import com.vitorpamplona.amethyst.cli.commands.PublishCommand
import com.vitorpamplona.amethyst.cli.commands.RelayCommands
import com.vitorpamplona.amethyst.cli.commands.SearchCommand
import com.vitorpamplona.amethyst.cli.commands.ServeCommand
import com.vitorpamplona.amethyst.cli.commands.StoreCommands
import com.vitorpamplona.amethyst.cli.commands.SubscribeCommand
import com.vitorpamplona.amethyst.cli.commands.SyncCommand
import com.vitorpamplona.amethyst.cli.commands.UseCommand
import com.vitorpamplona.amethyst.cli.commands.VerifyCommand
import com.vitorpamplona.amethyst.cli.commands.ZapCommand
import com.vitorpamplona.amethyst.cli.commands.cashu.CashuCommands
import com.vitorpamplona.amethyst.cli.commands.cashu.CashuMintCommands
import com.vitorpamplona.amethyst.cli.commands.route
import com.vitorpamplona.amethyst.cli.secrets.SecretStore
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * amy — non-interactive command-line interface to Amethyst.
 *
 * Covers Amethyst's account/social/Marmot surface plus a set of
 * nak-style army-knife primitives (`decode`, `encode`, `event`, `fetch`,
 * `subscribe`, …). The layout is intentionally extensible — new verbs
 * slot in as top-level subcommands.
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
    // Force AWT headless before any class load that might touch ImageIO,
    // Toolkit, or Graphics2D (image upload pulls in BufferedImage via
    // commons MediaMetadataReader / ImageReencoder). The Gradle launcher
    // also sets this via applicationDefaultJvmArgs; this is a belt-and-
    // braces guard for invocations that bypass the launcher scripts.
    System.setProperty("java.awt.headless", "true")

    // Quiet quartz's internal DEBUG chatter (relay auth, MLS restore, URL
    // rejection, throttle notices) by default so it doesn't drown a command's
    // own output; --verbose / -v restores full DEBUG. Set before dispatch so
    // even startup logging is gated.
    Log.minLevel = if (argv.any { it == "--verbose" || it == "-v" }) LogLevel.DEBUG else LogLevel.WARN

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
    var accountFlag: String? = null
    var secretBackendFlag: String? = null
    var passphraseFileFlag: String? = null
    var i = 0
    while (i < argv.size) {
        val a = argv[i]
        val (matched, consumed) = extractGlobalFlag(a, argv, i)
        when (matched) {
            GlobalFlag.ACCOUNT -> accountFlag = consumed.value
            GlobalFlag.SECRET_BACKEND -> secretBackendFlag = consumed.value
            GlobalFlag.PASSPHRASE_FILE -> passphraseFileFlag = consumed.value
            GlobalFlag.JSON -> Output.mode = Output.Mode.JSON
            GlobalFlag.VERBOSE -> Unit // level already applied in main(); just strip it here
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
        return UseCommand.run(tail)
    }

    // Stateless local primitives (nak-style army-knife verbs). They operate
    // purely on their arguments — no identity, no relays, no `~/.amy/` — so
    // they dispatch before account resolution and work with zero state.
    when (head) {
        "decode" -> return DecodeCommand.run(tail)
        "encode" -> return EncodeCommand.run(tail)
        "verify" -> return VerifyCommand.run(tail)
        "key" -> return KeyCommands.dispatch(tail)
        "filter" -> return FilterCommand.run(tail)
        "nip" -> return NipCommand.run(tail)
        "kind" -> return KindCommand.run(tail)
        "namecoin" -> return NamecoinCommand.dispatch(tail)
    }

    // `relay info URL` is a stateless NIP-11 fetch — no account needed. The
    // rest of `relay …` (add/list/publish-lists) operates on the account and
    // falls through to the normal path below.
    if (head == "relay" && tail.firstOrNull() == "info") {
        return RelayCommands.info(tail.drop(1).toTypedArray())
    }

    // `cashu mint ping|info URL` is a stateless NIP-60 /v1/info probe — no
    // account, no relays. The rest of `cashu …` operates on the account.
    if (head == "cashu" && tail.firstOrNull() == "mint") {
        return CashuMintCommands.dispatch(tail.drop(1).toTypedArray())
    }

    val secrets = SecretStore.from(backendFlag = secretBackendFlag, passphraseFile = passphraseFileFlag)
    val dataDir = DataDir.resolve(accountFlag = accountFlag, secrets = secrets)

    return when (head) {
        "init" -> InitCommands.init(dataDir, Args(tail))
        "create" -> CreateCommand.run(dataDir, tail)
        "login" -> LoginCommand.run(dataDir, tail)
        "whoami" -> InitCommands.whoami(dataDir)
        "relay" -> RelayCommands.dispatch(dataDir, tail)
        "marmot" -> marmotDispatch(dataDir, tail)
        "dm" -> DmCommands.dispatch(dataDir, tail)
        "profile" -> ProfileCommands.dispatch(dataDir, tail)
        "notes" -> NotesCommands.dispatch(dataDir, tail)
        "nsite" -> NsiteCommands.dispatch(dataDir, tail)
        "napplet" -> NappletCommands.dispatch(dataDir, tail)
        "store" -> StoreCommands.dispatch(dataDir, tail)
        "follow" -> FollowCommand.follow(dataDir, tail)
        "unfollow" -> FollowCommand.unfollow(dataDir, tail)
        "graperank" -> GrapeRankCommand.dispatch(dataDir, tail)
        "search" -> SearchCommand.dispatch(dataDir, tail)
        "zap" -> ZapCommand.dispatch(dataDir, tail)
        "offer" -> OfferCommands.dispatch(dataDir, tail)
        "debit" -> DebitCommands.dispatch(dataDir, tail)
        "event" -> EventCommand.run(dataDir, tail)
        "publish" -> PublishCommand.run(dataDir, tail)
        "fetch" -> FetchCommand.run(dataDir, tail)
        "subscribe" -> SubscribeCommand.run(dataDir, tail)
        "count" -> CountCommand.run(dataDir, tail)
        "encrypt" -> EncryptCommand.run(dataDir, tail)
        "decrypt" -> DecryptCommand.run(dataDir, tail)
        "gift" -> GiftCommands.dispatch(dataDir, tail)
        "outbox" -> OutboxCommand.run(dataDir, tail)
        "blossom" -> BlossomCommands.dispatch(dataDir, tail)
        "sync" -> SyncCommand.run(dataDir, tail)
        "git" -> GitCommands.dispatch(dataDir, tail)
        "admin" -> AdminCommand.run(dataDir, tail)
        "serve" -> ServeCommand.run(dataDir, tail)
        "cashu" -> CashuCommands.dispatch(dataDir, tail)
        "podcast" -> PodcastCommands.dispatch(dataDir, tail)
        "podcast20" -> Podcast20Commands.dispatch(dataDir, tail)
        "bunker" -> BunkerCommand.run(dataDir, tail)
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
): Int =
    route(
        name = "marmot",
        tail = tail,
        usage = "marmot <key-package|group|message|await|reset>",
        routes =
            mapOf(
                "key-package" to { rest -> KeyPackageCommands.dispatch(dataDir, rest) },
                "group" to { rest -> GroupCommands.dispatch(dataDir, rest) },
                "message" to { rest -> MessageCommands.dispatch(dataDir, rest) },
                "await" to { rest -> AwaitCommands.dispatch(dataDir, rest) },
                "reset" to { rest -> MarmotResetCommand.run(dataDir, rest) },
            ),
    )

private enum class GlobalFlag(
    val long: String,
    val takesValue: Boolean = true,
    val short: String? = null,
) {
    ACCOUNT("--account"),
    SECRET_BACKEND("--secret-backend"),
    PASSPHRASE_FILE("--passphrase-file"),
    JSON("--json", takesValue = false),
    VERBOSE("--verbose", takesValue = false, short = "-v"),
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
        if (token == flag.long || token == flag.short) {
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
        |  amy [--account ACCOUNT]
        |      [--secret-backend auto|keychain|ncryptsec|plaintext]
        |      [--passphrase-file PATH]
        |      [--json]
        |      [--verbose|-v]
        |      <cmd> [args...]
        |
        |Account selection:
        |  All state lives under ~/.amy/. Per-account directories
        |  ~/.amy/<account>/ hold identity, cursors, MLS state, and
        |  aliases; every observed Nostr event lands in the shared
        |  store under ~/.amy/shared/ (a SQLite `events.db` by default, or
        |  the `events-store/` tree when AMY_STORE=fs). ACCOUNT must match
        |  [a-zA-Z0-9_-]{1,64} (no spaces, no slashes).
        |
        |  Resolution order:
        |    1. --account X if given.
        |    2. ~/.amy/current marker (set by `amy use X`).
        |    3. Sole subdirectory of ~/.amy/ other than shared/.
        |    4. Error — disambiguate with --account or `amy use`.
        |
        |  Test harnesses isolate by overriding ${'$'}HOME for the amy
        |  subprocess (`HOME=/tmp/run.123 amy --account alice ...`).
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
        |Primitives (stateless — no account or network needed):
        |  decode ENTITY                decode a NIP-19/21 entity (npub|nsec|note|nevent|
        |                                nprofile|naddr|nrelay|nembed) to JSON
        |  encode npub HEX              encode raw parts into a NIP-19 entity:
        |  encode nsec HEX                 nevent/nprofile/naddr accept --relay URL[,URL…];
        |  encode note ID                  nevent accepts --author HEX --kind N;
        |  encode nevent ID [...]          naddr needs --kind N --pubkey HEX --identifier D
        |  encode nprofile HEX [...]
        |  encode naddr --kind N --pubkey HEX --identifier D [--relay URL[,URL…]]
        |  verify [EVENT-JSON]          check an event's id hash + signature
        |                                (reads stdin when the arg is omitted or `-`)
        |  key generate                 mint a fresh keypair (nsec + npub + hex)
        |  key public NSEC|HEX          derive the public key from a secret key
        |  key encrypt NSEC|HEX --password X    NIP-49 encrypt to ncryptsec1…
        |  key decrypt NCRYPTSEC --password X   NIP-49 decrypt back to a secret key
        |  filter [--kind …] [--author …]   assemble + print a NIP-01 filter JSON from the
        |         [--id …] [--tag …] …        same flags fetch/subscribe use (no query sent)
        |  nip N                        show a NIP (repo first, then a Nostr wiki/long-form fallback)
        |  nip list                     fetch the NIP index (README) from the repo
        |  kind N|NAME                  look up an event kind's label + NIP (number, or search by name)
        |  namecoin resolve IDENT       resolve a Namecoin identifier (.bit, d/, id/, alice@x.bit)
        |    [--server URL[,URL]]         to a Nostr pubkey + relays via the Namecoin blockchain
        |    [--timeout SECS]             (no account, talks to ElectrumX over TLS)
        |  namecoin servers             print the default ElectrumX server list
        |
        |Identity:
        |  init [--nsec NSEC]           create or import a bare identity (no defaults published)
        |  create [--name NAME]            provision a full Amethyst-style account + publish bootstrap events
        |  login KEY [--password X]     import (nsec|ncryptsec|mnemonic|npub|nprofile|hex|nip05|bunker://)
        |  whoami                       print current identity
        |
        |Remote signing (NIP-46):
        |  bunker [--relay URL[,URL…]]  run a remote signer for this (local-key) account; prints a
        |    [--secret S] [--timeout SECS]  bunker:// uri and signs requests until interrupt/timeout
        |  bunker connect NOSTRCONNECT-URI             act as signer for a client's nostrconnect://
        |    [--timeout SECS]                            offer (acks + services its requests)
        |  login bunker://PUBKEY?relay=…&secret=…       sign through a remote bunker (mints a local
        |                                                transport key; the account acts as PUBKEY)
        |  login --nostrconnect [--relay URL[,URL…]]   client-initiated: print a nostrconnect:// offer,
        |    [--name N] [--timeout SECS]                 wait for a signer to connect, then persist it
        |
        |Relays: `relay NOUN [add|remove|set|clear|list] …` (bare NOUN lists it)
        |  NOUN = outbox|inbox|nip65 (kind:10002)  dm (10050)  key-package (10051)
        |         search (10007)  private (10013)  blocked|trusted|proxy|indexer|
        |         broadcast|feeds (NIP-51, encrypted)
        |  relay outbox add URL          add URL as a write relay (read-only → both)
        |  relay inbox add URL           add URL as a read relay (write-only → both)
        |  relay outbox remove URL       drop write (both → read; write-only → gone)
        |  relay blocked add URL         e.g. private lists: add/remove/set/clear
        |  relay nip65                   show the combined read/write list
        |  relay add URL                 fan-out: nip65(both)+dm+key-package
        |  relay remove URL              fan-out remove from those three
        |  relay list                    print every configured relay bucket
        |  relay publish-lists           broadcast every configured relay list
        |  relay info URL                fetch + print a relay's NIP-11 info document
        |  outbox USER [--refresh]       show USER's NIP-65 read/write relays (outbox model)
        |        [--timeout SECS]         (USER: npub|nprofile|hex|name@domain)
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
        |Raw events (build / sign / broadcast):
        |  event --kind N [--content TEXT]             build + sign an arbitrary event with the active
        |        [--tags JSON] [--created-at TS]        account. Prints the signed event; add --publish
        |        [--publish] [--relay URL[,URL…]]       (or --relay) to broadcast. --tags takes a JSON
        |                                                array-of-arrays, e.g. '[["t","nostr"]]'.
        |  publish [EVENT-JSON] [--relay URL[,URL…]]   broadcast a pre-made signed event (verified
        |                                                first; reads stdin when the arg is omitted/`-`)
        |
        |Queries (filter flags shared by fetch/subscribe):
        |  fetch  [--kind K[,K]] [--author U[,U]]      one-shot query: collect until EOSE, print, exit.
        |         [--id ID[,ID]] [--tag e=ID,p=PK,…]    --author/--id accept npub/nevent/note/hex.
        |         [--since TS] [--until TS] [--limit N]  default --limit 100, --timeout 8s.
        |         [--search TEXT] [--relay URL[,URL…]]
        |         [--timeout SECS]
        |  subscribe [<same filter flags as fetch>]    live stream: print each event as it arrives
        |         [--relay URL[,URL…]] [--timeout SECS]  (NDJSON). Runs until --timeout or interrupt.
        |  count  [<same filter flags as fetch>]        NIP-45 COUNT: per-relay match counts, no
        |         [--relay URL[,URL…]] [--timeout SECS]  event download.
        |  sync   --relay URL [<filter flags>]          NIP-77 Negentropy reconcile with the local
        |         [--down] [--up] [--timeout SECS]       store (--down default; --up to push ours;
        |                                                both for bidirectional).
        |
        |Encryption (active account's key):
        |  encrypt --to USER [TEXT] [--nip04]           NIP-44 (default) or NIP-04 encrypt. Reads
        |                                                stdin when TEXT is omitted or `-`.
        |  decrypt --from USER [CIPHERTEXT] [--nip04]   inverse of encrypt.
        |  gift wrap --to USER [EVENT-JSON]             NIP-59: seal + wrap a signed inner event for
        |         [--relay URL[,URL…]]                   USER (add --relay to broadcast the wrap).
        |  gift unwrap [GIFTWRAP-JSON]                  decrypt + unseal a kind:1059 wrap addressed
        |                                                to the active account.
        |
        |Blossom blobs (NIP-B7 / BUD-01/02/04):
        |  blossom upload --server URL FILE             upload a file (authed); prints the blob URL.
        |          [--mime-type M]
        |  blossom download URL [--out FILE]            download a blob (public). Accepts a full URL,
        |  blossom download HASH --server URL            or a HASH plus --server.
        |  blossom list --server URL [USER]             list a user's blobs (defaults to self)
        |  blossom delete HASH --server URL             delete a blob you own
        |  blossom check --server URL HASH[,HASH]       HEAD-check blobs exist (fails if any missing)
        |  blossom mirror --server URL SOURCE-URL       ask the server to mirror a blob (BUD-04)
        |
        |Git (NIP-34):
        |  git announce --name N [--description D]      publish a kind:30617 repo announcement
        |      [--clone URL[,URL]] [--web URL[,URL]]     (--d sets the identifier; defaults to name)
        |      [--relay URL[,URL]] [--maintainer HEX[,]]
        |      [--hashtag T[,T]] [--earliest-commit C] [--d ID]
        |  git list [USER]                              list a user's repo announcements (default self)
        |  git show NADDR|kind:pubkey:id                print one repo announcement
        |  git issue NADDR|coords --subject S [BODY]    publish a kind:1621 issue against a repo
        |      [--hashtag T[,T]] [--relay URL[,URL]]     (BODY from arg or stdin)
        |
        |Podcasts (NIP-F4):
        |  podcast metadata --title T --image URL        publish kind:10154 show metadata
        |      --description D [--website URL[,URL]]
        |  podcast publish --title T --description D     publish a kind:54 episode
        |      --audio URL[,URL] [--audio-type MIME]
        |      [--image URL] [--content MARKDOWN]
        |  podcast list [USER] [--limit N]              list a user's metadata + episodes
        |
        |Podcasts (Podcasting 2.0 / podstr):
        |  podcast20 metadata --title T                 publish kind:30078 show metadata (JSON body)
        |      [--description D] [--author A] [--image URL] [--language L]
        |      [--categories A,B] [--funding URL,URL] [--website URL]
        |      [--copyright C] [--type episodic|serial] [--explicit] [--complete]
        |      [--value-json JSON]                       value-for-value split block
        |  podcast20 episode --title T --audio URL[,URL]  publish a kind:30054 episode
        |      [--d ID] [--audio-type MIME] [--description D] [--image URL]
        |      [--duration SECS] [--video URL] [--video-type MIME]
        |      [--episode N] [--season N] [--transcript URL] [--chapters URL]
        |      [--value-json JSON] [--topic A,B] [--content MARKDOWN] [--pubdate RFC2822]
        |  podcast20 trailer --title T --url URL          publish a kind:30055 trailer
        |      [--d ID] [--type MIME] [--length BYTES] [--season N] [--pubdate RFC2822]
        |  podcast20 list [USER] [--limit N]            list a creator's metadata + episodes + trailers
        |
        |Static websites (NIP-5A kind:15128/35128):
        |  nsite fetch AUTHOR [--d ID] [--path P]      resolve one path over Nostr + Blossom and
        |        [--server URL[,URL]] [--relay URL[,URL]]  VERIFY it against the manifest's sha256 pin
        |        [--out FILE] [--timeout SECS]          (AUTHOR: npub|nprofile|hex|name@domain;
        |        [--max-inline-bytes N]                 --d selects a kind:35128 named site, else the
        |                                                kind:15128 root site; --path defaults to /)
        |
        |Napplets (NIP-5D kind:5129/15129/35129):
        |  napplet fetch AUTHOR [--d ID] [--path P]    like `nsite fetch`, plus NIP-5D verification:
        |        [--server URL[,URL]] [--relay URL[,URL]]  recompute + check the `x` aggregate hash and
        |        [--out FILE] [--timeout SECS]          report the napplet's `requires` capabilities
        |        [--max-inline-bytes N]                 (--d selects a kind:35129 named napplet, else
        |  napplet fetch --snapshot EVENT-ID            the kind:15129 root; --snapshot pins a kind:5129
        |        [--path P] …                            immutable snapshot by event id)
        |
        |Contacts (NIP-02 kind:3):
        |  follow USER [--timeout SECS]               add USER to your contact list
        |  unfollow USER [--timeout SECS]             remove USER from your contact list
        |                                              (USER: npub|nprofile|hex|name@domain)
        |
        |Web of Trust (GrapeRank):
        |  graperank [OBSERVER]                       compute subjective trust scores (0..1) for every
        |    [--limit N] [--min-score X]               user reachable in the follow/mute/report graph.
        |    [--rigor X] [--attenuation X]             Exhaustively crawls each user's kind:10002 outbox
        |    [--max-rounds N] [--max-hops N]           for their latest kind:3/10000/1984 until every
        |    [--offline] [--timeout SECS]              discovered user has been checked (no user cap;
        |    [--diagnose]                              --max-hops bounds follow distance, e.g. 8;
        |                                              --diagnose logs slow/failed relays on timeout).
        |    [--publish] [--min-rank N]                OBSERVER: npub|nprofile|hex|name@domain (default:
        |    [--publish-limit N] [--publish-relay URL] active account). --offline scores from the local
        |                                              store only. --publish reconciles NIP-85 kind:30382
        |                                              cards signed by a per-observer service key: sends
        |                                              new/changed ranks >= --min-rank (default 2), skips
        |                                              unchanged, and retracts (kind:5) any card whose
        |                                              target left the graph or fell below the cutoff.
        |  graperank operator [status|relay <url>…    manage the machine's operator keys (~/.amy/operator/,
        |    |providers]                               independent of accounts): relay sets where cards +
        |                                              retractions publish; status shows master + relays;
        |                                              providers lists observer -> service-pubkey.
        |  graperank register [PROVIDER]              declare a NIP-85 provider in your kind:10040 so
        |    [--service KIND:TAG] [--relay URL]        clients can discover it (default: self as the
        |    [--private]                               30382:rank provider at your first outbox relay).
        |  graperank providers [USER] [--refresh]     list a user's declared NIP-85 trusted providers
        |    [--timeout SECS]                          (default: active account).
        |
        |Zaps (NIP-57):
        |  zap user USER SATS               build a profile zap-request, fetch a BOLT11
        |    [--comment X] [--anon|--private]  invoice from the recipient's LN service
        |    [--timeout SECS]                   (no auto-payment — paste invoice into a wallet)
        |  zap event EVENT-ID SATS           same, but attribute the zap to a specific
        |    [--comment X] [--anon|--private]  event (must be in local store)
        |    [--timeout SECS]
        |
        |CLINK Offers:
        |  offer info NOFFER                          decode a noffer1… pointer (local, no network)
        |  offer request NOFFER [--amount SATS]       kind:21001 round-trip: ask the service for a
        |    [--timeout MS]                            fresh BOLT11 (amount required for spontaneous
        |                                              offers; defaults to the pointer's fixed price)
        |
        |CLINK Debits:
        |  debit info NDEBIT                          decode an ndebit1… pointer (local, no network)
        |  debit pay NDEBIT BOLT11 [--amount SATS]    kind:21002 round-trip: ask the wallet to pay the
        |    [--timeout MS]                            invoice; prints the preimage or a GFY error
        |  debit budget NDEBIT --amount SATS          authorize a spending budget; omit --frequency
        |    [--frequency day|week|month] [--timeout MS] for a one-time budget
        |
        |Search (NIP-50):
        |  search user QUERY [--limit N]              search kind:0 profiles
        |                    [--timeout SECS]
        |  search note QUERY [--limit N]              search event content
        |                    [--kinds K[,K…]]          (default kind:1; e.g. 1,30023)
        |                    [--timeout SECS]
        |                                              uses your kind:10007 search-relay
        |                                              list, falls back to Amethyst defaults
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
        |Local event store (shared, under `<data-dir>/shared/`):
        |  Backend selected by AMY_STORE: sqlite (default; `shared/events.db`)
        |  or fs (`AMY_STORE=fs`; the `shared/events-store/` tree). SQLite is
        |  far more compact at scale — the FS tree spends one file per index
        |  posting, so large crawls balloon on disk.
        |  store stat                                 event count + disk usage (kind histogram/mtime on fs)
        |  store sweep-expired                        delete events past their NIP-40 expiration
        |  store scrub                                fs: rebuild idx/ from canonical events; sqlite: no-op
        |  store compact                              fs: drop dangling idx entries; sqlite: VACUUM
        |  store reindex-fts                          rebuild the NIP-50 search index (after a searchable-kinds change)
        """.trimMargin(),
    )
}
