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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.agents.AgentKeyProbeResult
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.agents.probeAgentKey
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyEdits
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeyProblem
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeysNoQuorumException
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeysNoWriteRelaysException
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.readAgentKeysForRewrite
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.rewriteAgentKeys
import com.vitorpamplona.amethyst.commons.service.http.SingleRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.UnreadableAgentKeysException
import com.vitorpamplona.quartz.nipFAAgentKeys.agentKeys
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import java.io.File
import java.io.IOException

/**
 * `amy agent …` — NIP-FA AI agent keys (kind 12000). The read-modify-write, the orderings and
 * the key check are commons' (`model/nipFAAgentKeys/AgentKeyEdits.kt`, `agents/AgentKeyProbe`);
 * this file reads the key from a file or stdin, never from argv, and never prints one, nor
 * anything the user typed where a key may have landed by mistake (a URL, a path).
 */
object AgentCommands {
    val USAGE: String =
        """
        |amy agent — NIP-FA AI agent keys (kind 12000, encrypted to yourself)
        |
        |  agent show                         the keys, the default first (masked, never printed whole)
        |  agent set URL (--key-file PATH | --key-stdin | --no-key) [--default]
        |                                     add or replace the key for URL (--no-key: an endpoint
        |                                     that takes none, a local runner); a new key goes last
        |                                     unless --default
        |  agent test [URL]                   check the default key (or URL's) with one free GET
        |  agent default URL                  make URL's key the default
        |  agent remove URL                   drop URL's key
        |  agent clear                        drop every key (other tags are kept)
        |
        |  A key never goes on the command line (it would show in `ps` and shell history).
        |  Reads and writes go to your NIP-65 write relays only (`amy relay outbox add URL`);
        |  more than half of them must answer the read before anything is written.
        """.trimMargin()

    /** The flags that take no value: one never swallows the URL after it. */
    private val BOOLEANS = setOf("key-stdin", "no-key", "default")

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "agent",
            tail,
            "agent <show|set|test|default|remove|clear>",
            help = USAGE,
            routes =
                mapOf(
                    "show" to { rest -> show(dataDir, rest) },
                    "set" to { rest -> set(dataDir, rest) },
                    "test" to { rest -> test(dataDir, rest) },
                    "default" to { rest -> edit(dataDir, rest, "default") { keys, url -> AgentKeyEdits.withDefault(keys, url) } },
                    "remove" to { rest -> edit(dataDir, rest, "remove") { keys, url -> AgentKeyEdits.without(keys, url) } },
                    "clear" to { rest -> clear(dataDir, rest) },
                ),
        )

    /**
     * One key as amy shows it: the provider, the URL and a masked key. [usable] is NIP-FA's
     * reader rule (a key over plain http to another machine is ignored); [default] is the first
     * usable one, what every client sends.
     */
    private fun row(
        key: AgentKeyTag,
        default: Boolean,
    ) = mapOf(
        "url" to key.url,
        "provider" to AgentProviderPresets.displayName(key.url),
        "key_masked" to AgentProviderPresets.maskKey(key.key),
        "has_key" to key.hasKey(),
        "default" to default,
        "local" to AgentProviderPresets.isLocalUrl(key.url),
        "usable" to key.isUrlAllowed(),
    )

    private fun rows(keys: List<AgentKeyTag>): List<Map<String, Any?>> {
        val default = keys.firstOrNull { it.isUrlAllowed() }
        return keys.map { row(it, default != null && it === default) }
    }

    /** What the write relays hold: the newest kind 12000 (null: none yet), its private tags read once, and its keys. */
    private class Held(
        val relays: Set<NormalizedRelayUrl>,
        val event: AgentKeyEvent?,
        val private: TagArray?,
        val keys: List<AgentKeyTag>,
    )

    /** Runs [body] on the newest kind 12000 from more than half of the write relays, decrypted once. */
    private suspend fun withKeys(
        dataDir: DataDir,
        write: Boolean,
        body: suspend (Context, Held) -> Int,
    ): Int {
        Context.open(dataDir).use { ctx ->
            if (write && !ctx.signer.isWriteable()) return Output.error("read_only", "this account can't sign (npub-only login)")
            ctx.prepare()
            val relays = ctx.ownWriteRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no NIP-65 write relays (none found on this machine or the bootstrap relays); add one with `amy relay outbox add URL`")
            val me = ctx.identity.pubKeyHex
            val known = ctx.latestReplaceable(me, AgentKeyEvent.KIND) as? AgentKeyEvent
            val earlier =
                try {
                    ctx.client.readAgentKeysForRewrite(relays, me, known) { event, _ -> ctx.verifyAndStore(event) }
                } catch (e: AgentKeysNoQuorumException) {
                    return Output.error("no_quorum", "only ${e.answered} of your ${e.asked} write relay(s) answered (more than half must); nothing was changed", mapOf("asked" to e.asked, "answered" to e.answered))
                } catch (e: AgentKeysNoWriteRelaysException) {
                    return Output.error("no_relays", "no NIP-65 write relays; add one with `amy relay outbox add URL`")
                }
            if (earlier == null) return body(ctx, Held(relays, null, null, emptyList()))
            // One decrypt for the whole edit: the rewrite reuses these tags.
            val private =
                try {
                    AgentKeyEvent.readForRewrite(earlier, ctx.signer)
                } catch (e: UnreadableAgentKeysException) {
                    return Output.error("decrypt_failed", "your agent keys event holds content amy can't read; not overwriting it")
                } catch (e: SignerExceptions) {
                    return Output.error("decrypt_failed", "this signer couldn't decrypt your agent keys")
                }
            return body(ctx, Held(relays, earlier, private, private.agentKeys()))
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return withKeys(dataDir, write = false) { _, held ->
            Output.emit(mapOf("keys" to rows(held.keys), "count" to held.keys.size, "event_id" to held.event?.id, "created_at" to held.event?.createdAt))
            0
        }
    }

    private suspend fun set(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest, booleanFlags = BOOLEANS, valueFlags = setOf("key-file"))
        val keyFile = args.flag("key-file")
        val fromStdin = args.bool("key-stdin")
        val noKey = args.bool("no-key")
        val makeDefault = args.bool("default")
        args.rejectUnknown()
        // Never echo a stray positional: it is most likely the key itself.
        if (args.positional.size != 1) return Output.error("bad_args", "agent set URL (--key-file PATH | --key-stdin | --no-key) [--default] (the key is never an argument)")
        if (listOf(keyFile != null, fromStdin, noKey).count { it } != 1) return Output.error("bad_args", "pass exactly one of --key-file PATH, --key-stdin or --no-key")
        val url = args.positional[0].trim()
        val key =
            try {
                when {
                    keyFile != null -> File(keyFile).readText().trim()
                    fromStdin ->
                        System.`in`
                            .readBytes()
                            .decodeToString()
                            .trim()
                    else -> ""
                }
            } catch (e: IOException) {
                // Not the path: a key passed where the path goes would be printed.
                return Output.error("bad_args", "can't read the --key-file")
            }
        // An empty key only when asked for: a file or stdin that held nothing is a mistake.
        if (!noKey && key.isEmpty()) return Output.error("bad_args", "the key was empty; pass --no-key for an endpoint that takes none")
        // The URL may be the key in the wrong place: name what is wrong, never what was typed.
        when (AgentKeyEdits.problem(url, key)) {
            AgentKeyProblem.BAD_URL -> return Output.error("bad_args", "URL isn't an http(s) URL with a host")
            AgentKeyProblem.NEEDS_HTTPS -> return Output.error("bad_args", "NIP-FA sends a key over https only, or over http to this machine (localhost, 127.0.0.0/8, ::1), with nothing before the host")
            AgentKeyProblem.NEEDS_KEY -> return Output.error("bad_args", "${AgentProviderPresets.displayName(url)} needs a key: pass --key-file PATH or --key-stdin")
            null -> Unit
        }
        val tag = AgentKeyTag(key, url)
        return withKeys(dataDir, write = true) { ctx, held ->
            write(ctx, held) { keys -> AgentKeyEdits.withKey(keys, tag, makeDefault) }
        }
    }

    private suspend fun edit(
        dataDir: DataDir,
        rest: Array<String>,
        verb: String,
        change: (List<AgentKeyTag>, String) -> List<AgentKeyTag>?,
    ): Int {
        val args = Args(rest)
        val url = args.positional(0, "url").trim()
        args.rejectUnknown()
        return withKeys(dataDir, write = true) { ctx, held ->
            // Not the URL: a key typed here by mistake would be printed back.
            if (AgentKeyEdits.forUrl(held.keys, url) == null) return@withKeys Output.error("not_found", "no key is saved for that URL (agent $verb); `amy agent show` lists them")
            write(ctx, held) { change(it, url) }
        }
    }

    private suspend fun clear(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return withKeys(dataDir, write = true) { ctx, held -> write(ctx, held) { emptyList() } }
    }

    /** Rewrites [held] with [change] of its keys and publishes it to the same relays; nothing when nothing changes. */
    private suspend fun write(
        ctx: Context,
        held: Held,
        change: (List<AgentKeyTag>) -> List<AgentKeyTag>?,
    ): Int {
        var after: List<AgentKeyTag> = held.keys
        val template =
            try {
                rewriteAgentKeys(held.event, ctx.signer, earlierPrivate = held.private) { keys -> change(keys).also { after = it ?: keys } }
            } catch (e: UnreadableAgentKeysException) {
                return Output.error("decrypt_failed", "your agent keys event holds content amy can't read; not overwriting it")
            } catch (e: SignerExceptions) {
                return Output.error("signer_error", "the signer couldn't read your agent keys")
            }
        if (template == null) {
            Output.emit(mapOf("changed" to false, "keys" to rows(after), "count" to after.size))
            return 0
        }
        val event = ctx.signer.sign(template)
        val ack = ctx.publish(event, held.relays)
        RawEventSupport.publishGuard(ack, event.id)?.let { return it }
        Output.emit(mapOf("changed" to true, "event_id" to event.id, "keys" to rows(after), "count" to after.size) + RawEventSupport.ackFields(ack))
        return 0
    }

    private suspend fun test(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val url = args.positionalOrNull(0)?.trim()
        args.rejectUnknown()
        return withKeys(dataDir, write = false) { ctx, held ->
            val keys = held.keys
            // The default is the first key a reader may send: never one NIP-FA's rule leaves out.
            val key =
                (if (url != null) AgentKeyEdits.forUrl(keys, url) else keys.firstOrNull { it.isUrlAllowed() })
                    ?: return@withKeys Output.error("not_found", if (url != null) "no key is saved for that URL" else "no usable agent keys saved")
            if (!key.isUrlAllowed()) return@withKeys Output.error("bad_args", "NIP-FA never sends that key: its URL is plain http to another machine")
            val shown = rows(keys).first { it["url"] == key.url && it["key_masked"] == AgentProviderPresets.maskKey(key.key) }
            when (val result = SingleRoleBasedHttpClientBuilder(ctx.okhttp).probeAgentKey(key)) {
                is AgentKeyProbeResult.Ok -> {
                    Output.emit(shown + mapOf("result" to "ok", "model_count" to result.modelCount, "key_checked" to result.keyChecked))
                    0
                }
                is AgentKeyProbeResult.Rejected -> Output.error("key_rejected", "the provider refused the key (HTTP ${result.code})", shown + mapOf("result" to "rejected", "status" to result.code))
                is AgentKeyProbeResult.Failed -> Output.error("http_error", "the provider answered HTTP ${result.code}", shown + mapOf("result" to "failed", "status" to result.code))
                AgentKeyProbeResult.Unreachable -> Output.error("provider_unreachable", "no answer from ${AgentProviderPresets.displayName(key.url)}", shown + mapOf("result" to "unreachable"))
                AgentKeyProbeResult.Unsupported -> Output.error("runtime", "this platform can't check a key", shown + mapOf("result" to "unsupported"))
            }
        }
    }
}
