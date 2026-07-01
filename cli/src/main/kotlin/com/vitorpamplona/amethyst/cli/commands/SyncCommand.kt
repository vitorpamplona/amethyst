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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * `amy sync --relay URL [filter flags] [--down] [--up] [--timeout SECS]`
 *
 * NIP-77 Negentropy set-reconciliation between the local event store and a
 * relay (nak's `sync`, adapted to amy's local-store model). First it
 * negotiates the symmetric difference under [filter], then closes the loop:
 *
 *   --down  (default)  download events the relay has and we lack (REQ by id)
 *   --up               upload events we have and the relay lacks (EVENT)
 *
 * Pass both for a full bidirectional sync. The filter flags are the same as
 * `fetch`/`subscribe`; an empty filter reconciles the whole store.
 *
 * Thin assembly only: the negentropy protocol lives in quartz
 * (`NegentropySession`); this file drives the WebSocket round-trips and
 * reuses `Context.drain` / `Context.publish` for the NIP-01 follow-up.
 */
object SyncCommand {
    private const val ID_CHUNK = 500

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl =
            args.flag("relay")
                ?: return Output.error("bad_args", "sync requires --relay URL")
        val relay =
            RelayUrlNormalizer.normalizeOrNull(relayUrl)
                ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 30L) * 1000
        // Default direction is download; --up adds upload.
        val up = args.bool("up")
        val down = args.bool("down") || !up
        val filter = RawEventSupport.buildFilter(args)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val localEvents = ctx.store.query<Event>(filter)
            val localById = localEvents.associateBy { it.id }

            val diff =
                negotiate(relay.toHttp(), filter, localEvents, timeoutMs)
                    ?: return Output.error("timeout", "no negentropy response from ${relay.url} within ${timeoutMs}ms")
            if (diff.error != null) {
                return Output.error("sync_error", diff.error)
            }

            // needIds = relay has, we lack; haveIds = we have, relay lacks.
            var downloaded = 0
            if (down && diff.needIds.isNotEmpty()) {
                diff.needIds.chunked(ID_CHUNK).forEach { chunk ->
                    val got = ctx.drain(mapOf(relay to listOf(Filter(ids = chunk))), timeoutMs)
                    downloaded += got.size
                }
            }

            var uploaded = 0
            if (up && diff.haveIds.isNotEmpty()) {
                diff.haveIds.forEach { id ->
                    val ev = localById[id] ?: return@forEach
                    val ack = ctx.publish(ev, setOf(relay))
                    if (ack.values.any { it }) uploaded++
                }
            }

            Output.emit(
                mapOf(
                    "relay" to relay.url,
                    "local_events" to localEvents.size,
                    "rounds" to diff.rounds,
                    "need" to diff.needIds.size,
                    "have" to diff.haveIds.size,
                    "downloaded" to downloaded,
                    "uploaded" to uploaded,
                ),
            )
            return 0
        }
    }

    private data class Diff(
        val haveIds: List<HexKey>,
        val needIds: List<HexKey>,
        val rounds: Int,
        val error: String?,
    )

    /**
     * Drive one NIP-77 reconciliation over a raw WebSocket and return the
     * symmetric difference. Mirrors geode's interop sync driver: the protocol
     * state machine is [NegentropySession]; this only shuttles frames.
     */
    private suspend fun negotiate(
        httpUrl: String,
        filter: Filter,
        localEvents: List<Event>,
        timeoutMs: Long,
        subId: String = "amy-sync",
        maxRounds: Int = 64,
    ): Diff? {
        val incoming = Channel<String>(UNLIMITED)
        val client = OkHttpClient.Builder().build()
        val ws =
            client.newWebSocket(
                Request.Builder().url(httpUrl).build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        incoming.trySend(text)
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        incoming.close()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        incoming.close(t)
                    }
                },
            )

        return try {
            withTimeoutOrNull(timeoutMs) {
                val session = NegentropySession(subId, filter, localEvents, frameSizeLimit = 0)
                ws.send(OptimizedJsonMapper.toJson(session.open()))

                val have = mutableSetOf<HexKey>()
                val need = mutableSetOf<HexKey>()
                var rounds = 0
                while (rounds < maxRounds) {
                    val raw = incoming.receive()
                    rounds++
                    when (val msg = OptimizedJsonMapper.fromJsonToMessage(raw)) {
                        is NegErrMessage -> return@withTimeoutOrNull Diff(have.toList(), need.toList(), rounds, "${msg.subId}: ${msg.reason}")
                        is NegMsgMessage -> {
                            val r = session.processMessage(msg.message)
                            have += r.haveIds
                            need += r.needIds
                            if (r.isComplete()) {
                                return@withTimeoutOrNull Diff(have.toList(), need.toList(), rounds, null)
                            }
                            ws.send(OptimizedJsonMapper.toJson(r.nextCmd!!))
                        }
                        else -> {} // ignore NOTICE/etc. and keep waiting
                    }
                }
                Diff(have.toList(), need.toList(), rounds, "did not converge in $maxRounds rounds")
            }
        } finally {
            ws.close(1000, "amy-sync-done")
        }
    }
}
