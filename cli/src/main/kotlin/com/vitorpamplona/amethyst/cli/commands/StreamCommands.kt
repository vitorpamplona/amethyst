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
import com.vitorpamplona.marmotquic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamPublisher
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamRecordV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamStart
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamSubscriber
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.InMemoryAgentTextStreamSequenceStore
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.PreviewStatus
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.RecordOutcome
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quic.tls.CertificateValidator
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import com.vitorpamplona.quic.tls.PinnedCertificateValidator
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `amy marmot stream` — agent text stream previews (`0x8006`).
 *
 * The durable half is ordinary Marmot messaging: a hidden kind:1200 anchors
 * the stream and a kind:9 closes it, both over MLS. The live half is raw QUIC
 * to a broker, and it is strictly a progressive enhancement — a member that
 * never opens a QUIC connection still reads the whole answer from the final
 * kind:9.
 */
object StreamCommands {
    private const val STREAM_ID_FLAG = "stream-id"

    val USAGE: String =
        """
        |amy marmot stream — agent text stream previews over QUIC
        |
        |  marmot stream start GID [--stream-id HEX] [--broker quic://HOST:PORT[,…]]
        |        publish the kind:1200 that anchors a stream; prints stream_id + start_event_id
        |
        |  marmot stream send GID --stream-id HEX --start-event-id HEX
        |        (--broker URI | --direct quic://HOST:PORT) TEXT…
        |        push TEXT as TextDelta records; --direct dials the receiver point to point
        |        (ALPN marmot.quic_stream.v1, no control envelope) instead of a broker
        |
        |  marmot stream watch GID [--stream-id HEX] [--timeout SECS]
        |        find the kind:1200 in the group, subscribe over QUIC, fold the preview
        |
        |  marmot stream finish GID --stream-id HEX --transcript-hash HEX --chunk-count N TEXT…
        |        publish the authoritative kind:9 carrying the transcript a receiver checks against
        |
        |Every record is encrypted under the group's own MLS exporter secret, so a
        |broker relays ciphertext and learns only which room it belongs to.
        |
        |TLS trust for the QUIC hop (send and watch):
        |  --pin-sha256 HEX[,HEX…]   trust exactly these leaf certificates (self-signed
        |                            endpoints; colons and whitespace are ignored)
        |  --insecure                accept any certificate — local testing only
        |Without either, the platform trust store decides.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "stream",
            tail,
            "stream <start|send|watch|finish> …",
            mapOf(
                "start" to { rest -> start(dataDir, rest) },
                "send" to { rest -> send(dataDir, rest) },
                "watch" to { rest -> watch(dataDir, rest) },
                "finish" to { rest -> finish(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun start(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val positional = args.positional
        if (positional.isEmpty()) return Output.error("bad_args", "stream start GID [--stream-id HEX] [--broker URI]…")

        val streamId = args.flag(STREAM_ID_FLAG) ?: MlsCryptoProvider.randomBytes(32).toHexKey()
        if (streamId.length != 64) return Output.error("bad_args", "--stream-id must be 32 bytes of hex")
        // Repeatable in the spec, comma-separated here: `Args` keeps one
        // value per flag and a receiver tries them in the order given.
        val brokers =
            args
                .flag("broker")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(positional[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val bundle = ctx.marmot.buildAgentStreamStart(gid, streamId, brokers, parentEventId = args.flag("parent"))
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "stream_id" to streamId,
                    // The start payload's OWN id is the stream anchor that
                    // goes into the key context — not the kind:445 that
                    // carried it, and not the MLS message id.
                    "start_event_id" to bundle.innerEvent.id,
                    "epoch" to ctx.marmot.currentEpoch(gid),
                    "brokers" to brokers,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val positional = args.positional
        val streamId = args.flag(STREAM_ID_FLAG)
        val startEventId = args.flag("start-event-id")
        val broker = args.flag("broker")
        // The two delivery modes are alternatives, not a fallback chain: one
        // dials a broker room, the other dials the receiver itself, and they
        // negotiate different ALPNs. Picking silently when both are given
        // would hide which one actually carried the records.
        val direct = args.flag("direct")
        if (positional.size < 2 || streamId == null || startEventId == null || (broker == null) == (direct == null)) {
            return Output.error(
                "bad_args",
                "stream send GID --stream-id HEX --start-event-id HEX (--broker URI | --direct quic://HOST:PORT) TEXT…",
            )
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(positional[0])
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            // The stream's epoch is the one that carried its kind:1200, not
            // whatever the group has reached by now — a commit between the
            // start and the first record would otherwise put the publisher on
            // a key no receiver derives.
            val anchorEpoch =
                args.flag("epoch")?.toLongOrNull()
                    ?: ctx.marmot.storedEpochs(gid)[startEventId]
            val crypto =
                ctx.marmot.agentTextStreamCrypto(
                    nostrGroupId = gid,
                    streamId = streamId.hexToByteArray(),
                    startEventId = startEventId.hexToByteArray(),
                    epoch = anchorEpoch,
                )
            val publisher = AgentTextStreamPublisher.open(crypto, InMemoryAgentTextStreamSequenceStore())
            val transport = QuicAgentTextStreamTransport(certificateValidator = certificateValidator(args))

            val stream =
                try {
                    if (direct != null) {
                        transport.sendDirect(direct, streamId.hexToByteArray(), startEventId.hexToByteArray())
                    } else {
                        transport.publish(broker!!, streamId.hexToByteArray(), startEventId.hexToByteArray())
                    }
                } catch (e: Exception) {
                    return Output.error(
                        if (direct != null) "receiver_unreachable" else "broker_unreachable",
                        "${e.message}",
                    )
                }
            try {
                for (text in positional.drop(1)) {
                    stream.send(publisher.publish(AgentTextStreamRecordV1.TYPE_TEXT_DELTA, text.encodeToByteArray()))
                }
                stream.finish()
            } finally {
                stream.close()
            }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "stream_id" to streamId,
                    "start_event_id" to startEventId,
                    "mode" to if (direct != null) "direct" else "broker",
                    "endpoint" to (direct ?: broker),
                    "records" to positional.size - 1,
                    "epoch" to crypto.context.mlsEpoch,
                    // What `stream finish` has to publish so a receiver can
                    // prove it saw this exact stream.
                    "transcript_hash" to publisher.transcript.hash.toHexKey(),
                    "chunk_count" to publisher.transcript.chunkCount,
                ),
            )
            return 0
        }
    }

    /**
     * The TLS trust policy for the QUIC hop, from the flags.
     *
     * Pinning is the interesting one and the binding calls it out: preview
     * endpoints and brokers are commonly self-signed, so a client MAY pin the
     * endpoint certificate by SHA-256 fingerprint instead of chaining to a CA.
     * `--insecure` stays available because a local test broker mints a fresh
     * certificate on every boot, but it is not a weaker trust model — it is
     * none, so it has to be asked for by name.
     */
    private fun certificateValidator(args: Args): CertificateValidator {
        val pins =
            args
                .flag("pin-sha256")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (pins.isNotEmpty()) return PinnedCertificateValidator.ofSha256Hex(*pins.toTypedArray())
        if (args.bool("insecure")) return PermissiveCertificateValidator()
        return JdkCertificateValidator()
    }

    private suspend fun watch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val positional = args.positional
        if (positional.isEmpty()) return Output.error("bad_args", "stream watch GID [--stream-id HEX] [--timeout SECS]")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 30L) * 1000

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(positional[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val wanted = args.flag(STREAM_ID_FLAG)
            val anchor =
                findStart(ctx, gid, wanted)
                    ?: return Output.error("no_stream", "no kind:1200 stream start in group $gid")
            val (startEvent, start) = anchor

            if (!start.isTextProfile) {
                return Output.error("unsupported_stream", "stream-type=${start.streamType} final-kind=${start.finalKind}")
            }
            if (!start.isQuicRoute) {
                return Output.error("unsupported_route", "route=${start.route} — only the raw QUIC binding is implemented")
            }
            if (start.brokerCandidates.isEmpty()) {
                return Output.error("no_candidate", "the start payload advertises no broker; the final kind:9 is the answer")
            }

            // The key context is the PUBLISHER's, not ours: the sender id and
            // the epoch are theirs, and every member of that epoch derives the
            // same record key from the group exporter.
            //
            // The epoch is the one that DELIVERED the anchor, not the group's
            // current one. A commit landing between the start and the watch
            // moves the group on, and deriving under the newer epoch produces
            // a different key and an empty preview.
            val anchorEpoch =
                args.flag("epoch")?.toLongOrNull()
                    ?: ctx.marmot.storedEpochs(gid)[startEvent.id]
            val crypto =
                ctx.marmot.agentTextStreamCrypto(
                    nostrGroupId = gid,
                    streamId = start.streamId.hexToByteArray(),
                    startEventId = startEvent.id.hexToByteArray(),
                    senderPubKey = startEvent.pubKey,
                    epoch = anchorEpoch,
                )
            val subscriber = AgentTextStreamSubscriber(crypto)
            val transport = QuicAgentTextStreamTransport(certificateValidator = certificateValidator(args))

            // "A receiver tries advertised candidates in listed order"; the
            // first that yields the matching stream wins.
            var lastError: String? = null
            for (candidate in start.brokerCandidates) {
                val stream =
                    try {
                        transport.subscribe(candidate, start.streamId.hexToByteArray(), startEvent.id.hexToByteArray())
                    } catch (e: Exception) {
                        lastError = "${e.message}"
                        continue
                    }
                val outcomes = mutableMapOf<String, Int>()
                try {
                    withTimeoutOrNull(timeoutMs) {
                        stream.incoming().collect { record ->
                            val outcome = subscriber.accept(record)
                            outcomes[outcome.name] = (outcomes[outcome.name] ?: 0) + 1
                            if (outcome == RecordOutcome.Accepted &&
                                (subscriber.status == PreviewStatus.FINISHED || subscriber.status == PreviewStatus.ABORTED)
                            ) {
                                throw StreamComplete()
                            }
                        }
                    }
                } catch (_: StreamComplete) {
                    // The publisher said the final message is coming.
                } finally {
                    stream.close()
                }

                Output.emit(
                    mapOf(
                        "group_id" to gid,
                        "stream_id" to start.streamId,
                        "start_event_id" to startEvent.id,
                        "author" to startEvent.pubKey,
                        "broker" to candidate,
                        "preview" to subscriber.previewText,
                        "status" to subscriber.status.name,
                        "records" to subscriber.highWaterMark,
                        "transcript_hash" to subscriber.transcript.hash.toHexKey(),
                        "chunk_count" to subscriber.transcript.chunkCount,
                        "epoch" to crypto.context.mlsEpoch,
                        "outcomes" to outcomes,
                        "latest_status" to subscriber.latestStatus,
                        "latest_progress" to subscriber.latestProgress,
                    ),
                )
                return 0
            }
            return Output.error("no_candidate_worked", lastError ?: "every advertised broker candidate was unusable")
        }
    }

    private suspend fun finish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val positional = args.positional
        val streamId = args.flag(STREAM_ID_FLAG)
        val transcriptHash = args.flag("transcript-hash")
        val chunkCount = args.flag("chunk-count")?.toLongOrNull()
        if (positional.size < 2 || streamId == null || transcriptHash == null || chunkCount == null) {
            return Output.error(
                "bad_args",
                "stream finish GID --stream-id HEX --transcript-hash HEX --chunk-count N TEXT…",
            )
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(positional[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val text = positional.drop(1).joinToString(" ")
            val bundle = ctx.marmot.buildAgentStreamFinal(gid, streamId, transcriptHash, chunkCount, text)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "stream_id" to streamId,
                    "final_event_id" to bundle.innerEvent.id,
                    "transcript_hash" to transcriptHash,
                    "chunk_count" to chunkCount,
                    "content" to text,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    /**
     * The newest kind:1200 in the group's decrypted log, optionally pinned to
     * one stream id. Newest wins because a group can carry many streams over
     * its life and a watcher almost always means the current one.
     */
    private suspend fun findStart(
        ctx: Context,
        nostrGroupId: String,
        streamId: String?,
    ): Pair<Event, AgentTextStreamStart>? {
        var best: Pair<Event, AgentTextStreamStart>? = null
        for (line in ctx.marmot.loadStoredMessages(nostrGroupId)) {
            val parsed = Event.fromJsonOrNull(line) ?: continue
            val start = AgentTextStreamStart.fromTags(parsed.kind, parsed.tags) ?: continue
            if (streamId != null && !start.streamId.equals(streamId, ignoreCase = true)) continue
            if (best == null || parsed.createdAt >= best.first.createdAt) best = parsed to start
        }
        return best
    }

    /** Unwinds the collect loop once the publisher signalled the end. */
    private class StreamComplete : RuntimeException(null, null, false, false)
}
