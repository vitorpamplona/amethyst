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
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.AgentKeysUnreadableException
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.readAgentKeysForRewrite
import com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys.rewriteAgentKeys
import com.vitorpamplona.amethyst.commons.service.http.SingleRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.UnreadableAgentKeysException
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import java.io.File
import java.io.IOException

/**
 * `amy agent …` — NIP-FA AI agent keys (kind 12000). The read-modify-write, the orderings and
 * the key check are commons' (`model/nipFAAgentKeys/AgentKeyEdits.kt`, `agents/AgentKeyProbe`);
 * this file reads the key from a file or stdin, never from argv, and never prints one.
 */
object AgentCommands {
    val USAGE: String =
        """
        |amy agent — NIP-FA AI agent keys (kind 12000, encrypted to yourself)
        |
        |  agent show                         the keys, the default first (masked, never printed whole)
        |  agent set URL [--key-file PATH | --key-stdin] [--default]
        |                                     add or replace the key for URL (neither flag: no key,
        |                                     for a local runner); a new key goes last unless --default
        |  agent test [URL]                   check the default key (or URL's) with one free GET
        |  agent default URL                  make URL's key the default
        |  agent remove URL                   drop URL's key
        |  agent clear                        drop every key (other tags are kept)
        |
        |  A key never goes on the command line (it would show in `ps` and shell history).
        |  Reads and writes go to your NIP-65 write relays only (`amy relay outbox add URL`).
        """.trimMargin()

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

    /** One key as amy shows it: the provider, the URL and a masked key. */
    private fun row(
        key: AgentKeyTag,
        index: Int,
    ) = mapOf(
        "url" to key.url,
        "provider" to AgentProviderPresets.displayName(key.url),
        "key_masked" to AgentProviderPresets.maskKey(key.key),
        "has_key" to key.hasKey(),
        "default" to (index == 0),
        "local" to AgentProviderPresets.isLocalUrl(key.url),
    )

    private fun rows(keys: List<AgentKeyTag>) = keys.mapIndexed { i, key -> row(key, i) }

    /** Runs [body] on the newest kind 12000 from the write relays (null: none yet) and its keys. */
    private suspend fun withKeys(
        dataDir: DataDir,
        write: Boolean,
        body: suspend (Context, Set<NormalizedRelayUrl>, AgentKeyEvent?, List<AgentKeyTag>) -> Int,
    ): Int {
        Context.open(dataDir).use { ctx ->
            if (write && !ctx.signer.isWriteable()) return Output.error("read_only", "this account can't sign (npub-only login)")
            ctx.prepare()
            val relays = ctx.nip65WriteRelays()
            if (relays.isEmpty()) return Output.error("no_relays", "no NIP-65 write relays; add one with `amy relay outbox add URL`")
            val me = ctx.identity.pubKeyHex
            val known = ctx.latestReplaceable(me, AgentKeyEvent.KIND) as? AgentKeyEvent
            val earlier =
                try {
                    ctx.client.readAgentKeysForRewrite(relays, me, known) { event, _ -> ctx.verifyAndStore(event) }
                } catch (e: AgentKeysUnreadableException) {
                    return Output.error("relay_error", "none of your ${relays.size} write relay(s) answered and no copy is saved here; nothing was changed")
                }
            val keys = if (earlier == null) emptyList() else earlier.agentKeys(ctx.signer) ?: return Output.error("decrypt_failed", "this signer couldn't decrypt your agent keys")
            return body(ctx, relays, earlier, keys)
        }
    }

    private suspend fun show(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return withKeys(dataDir, write = false) { _, _, earlier, keys ->
            Output.emit(mapOf("keys" to rows(keys), "count" to keys.size, "event_id" to earlier?.id, "created_at" to earlier?.createdAt))
            0
        }
    }

    private suspend fun set(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val keyFile = args.flag("key-file")
        val fromStdin = args.bool("key-stdin")
        val makeDefault = args.bool("default")
        args.rejectUnknown()
        // Never echo a stray positional: it is most likely the key itself.
        if (args.positional.size != 1) return Output.error("bad_args", "agent set URL [--key-file PATH | --key-stdin] [--default] (the key is never an argument)")
        if (keyFile != null && fromStdin) return Output.error("bad_args", "pass --key-file or --key-stdin, not both")
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
                return Output.error("bad_args", "can't read --key-file $keyFile")
            }
        when (AgentKeyEdits.problem(url, key)) {
            AgentKeyProblem.BAD_URL -> return Output.error("bad_args", "not an http(s) URL with a host: $url")
            AgentKeyProblem.NEEDS_HTTPS -> return Output.error("bad_args", "NIP-FA sends a key over https only, or http to this machine: $url")
            AgentKeyProblem.NEEDS_KEY -> return Output.error("bad_args", "${AgentProviderPresets.displayName(url)} needs a key: pass --key-file PATH or --key-stdin")
            null -> Unit
        }
        val tag = AgentKeyTag(key, url)
        return withKeys(dataDir, write = true) { ctx, relays, earlier, _ ->
            write(ctx, relays, earlier) { keys -> AgentKeyEdits.withKey(keys, tag, makeDefault) }
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
        return withKeys(dataDir, write = true) { ctx, relays, earlier, keys ->
            if (AgentKeyEdits.forUrl(keys, url) == null) return@withKeys Output.error("not_found", "no key for $url (agent $verb)")
            write(ctx, relays, earlier) { change(it, url) }
        }
    }

    private suspend fun clear(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        Args(rest).rejectUnknown()
        return withKeys(dataDir, write = true) { ctx, relays, earlier, _ -> write(ctx, relays, earlier) { emptyList() } }
    }

    /** Rewrites [earlier] with [change] of its keys and publishes it to [relays]; nothing when nothing changes. */
    private suspend fun write(
        ctx: Context,
        relays: Set<NormalizedRelayUrl>,
        earlier: AgentKeyEvent?,
        change: (List<AgentKeyTag>) -> List<AgentKeyTag>?,
    ): Int {
        var after: List<AgentKeyTag> = emptyList()
        val template =
            try {
                rewriteAgentKeys(earlier, ctx.signer) { keys -> change(keys).also { after = it ?: keys } }
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
        val ack = ctx.publish(event, relays)
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
        return withKeys(dataDir, write = false) { ctx, _, _, keys ->
            val key = (if (url != null) AgentKeyEdits.forUrl(keys, url) else keys.firstOrNull()) ?: return@withKeys Output.error("not_found", if (url != null) "no key for $url" else "no agent keys saved")
            val shown = row(key, keys.indexOf(key))
            when (val result = SingleRoleBasedHttpClientBuilder(ctx.okhttp).probeAgentKey(key)) {
                is AgentKeyProbeResult.Ok -> {
                    Output.emit(shown + mapOf("result" to "ok", "model_count" to result.modelCount, "key_checked" to result.keyChecked))
                    0
                }
                is AgentKeyProbeResult.Rejected -> Output.error("key_rejected", "the provider refused the key (HTTP ${result.code})", shown + mapOf("result" to "rejected", "status" to result.code))
                is AgentKeyProbeResult.Failed -> Output.error("http_error", "the provider answered HTTP ${result.code}", shown + mapOf("result" to "failed", "status" to result.code))
                AgentKeyProbeResult.Unreachable -> Output.error("provider_unreachable", "no answer from ${key.url}", shown + mapOf("result" to "unreachable"))
                AgentKeyProbeResult.Unsupported -> Output.error("runtime", "this platform can't check a key", shown + mapOf("result" to "unsupported"))
            }
        }
    }
}
