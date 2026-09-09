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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamFinal
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamStart
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamSubscriber
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.PreviewStatus
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a front end renders for one live agent text stream.
 *
 * [isConfirmed] is the only thing that licenses showing this as ordinary
 * content. Until the durable kind:9 lands and its transcript matches what we
 * folded, this is provisional and a renderer MUST make it visibly distinct —
 * every record can open individually and the stream still be wrong, if one was
 * dropped, reordered or injected.
 */
class AgentStreamPreview(
    val streamId: HexKey,
    val startEventId: HexKey,
    /** The account that anchored the stream — not necessarily the group's agent. */
    val author: HexKey,
    val text: String,
    val status: PreviewStatus,
    /** Latest `Status` label, for chrome. Never part of the answer text. */
    val statusLabel: String? = null,
    /** Latest `ProgressDelta`, for chrome. Never part of the answer text. */
    val progressLabel: String? = null,
    val isConfirmed: Boolean = false,
)

/**
 * Watches one Marmot group for an agent text stream and exposes it as UI state.
 *
 * The live preview is a progressive enhancement, and every failure here is
 * meant to look like "no preview" rather than like a broken group: a group
 * with no broker candidate, a candidate that will not connect, a platform with
 * no QUIC at all, or a stream type we do not implement all end the same way —
 * the group still works and the final kind:9 still arrives as normal chat.
 *
 * [transport] is null on a platform that cannot open a raw QUIC connection.
 * That is a supported configuration, not a degraded one: `receive` explicitly
 * does not require implementing the QUIC data plane.
 */
class MarmotAgentStreamWatcher(
    private val marmot: MarmotManager,
    private val transport: MarmotQuicTransport?,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow<AgentStreamPreview?>(null)
    val preview: StateFlow<AgentStreamPreview?> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var job: Job? = null
    private var watchingStreamId: HexKey? = null
    private var subscriber: AgentTextStreamSubscriber? = null

    /**
     * Start (or keep) watching the newest agent text stream in [nostrGroupId].
     *
     * Idempotent: calling it again for a stream already being watched does
     * nothing, so a front end can call it on every feed update.
     */
    suspend fun watchLatest(nostrGroupId: HexKey) {
        // Resolve first: the durable message may already be in the log (a
        // catch-up sync delivers the whole stream at once), and a preview we
        // can no longer improve should be settled before we open a socket.
        resolveAgainstStoredFinal(nostrGroupId)

        if (transport == null) return
        val anchor = findLatestStart(nostrGroupId) ?: return
        val (startEvent, start) = anchor

        // A stream type or route we do not implement is not an error: ignore
        // the live route and let the final message do its job.
        if (!start.isTextProfile || !start.isQuicRoute || start.brokerCandidates.isEmpty()) return

        mutex.withLock {
            if (watchingStreamId == start.streamId) return@withLock
            job?.cancel()
            watchingStreamId = start.streamId
            mutable.value = null
            job = scope.launch { follow(nostrGroupId, startEvent, start) }
        }
    }

    /**
     * A durable kind:9 closed a stream out. Confirms the preview when our fold
     * agrees with it, and drops the preview when it does not — a disagreement
     * means we rendered something the publisher did not send, so the durable
     * message is the only thing that should remain on screen.
     */
    fun onFinal(
        streamId: HexKey,
        transcriptHash: HexKey,
        chunkCount: Long,
    ) {
        val current = mutable.value ?: return
        if (!current.streamId.equals(streamId, ignoreCase = true)) return
        val folded = subscriber
        val matches = folded != null && folded.matchesFinal(transcriptHash.hexToByteArray(), chunkCount)
        mutable.value = if (matches) AgentStreamPreviewCopy.confirmed(current) else null
        if (!matches) {
            Log.d("MarmotAgentStreamWatcher") {
                "stream ${streamId.take(8)}… did not match its final message — dropping the preview"
            }
        }
    }

    /**
     * Apply the durable kind:9 for the stream being previewed, if the group's
     * log already holds it.
     *
     * A front end only has to say "the feed moved"; deciding whether a preview
     * is confirmed, contradicted or still pending is this class's job, and
     * keeping it here is what makes it testable without a UI.
     */
    private suspend fun resolveAgainstStoredFinal(nostrGroupId: HexKey) {
        val current = mutable.value ?: return
        for (line in marmot.loadStoredMessages(nostrGroupId)) {
            val parsed = Event.fromJsonOrNull(line) ?: continue
            if (parsed.kind != AgentTextStreamStart.FINAL_KIND_TEXT) continue
            val final = AgentTextStreamFinal.fromTags(parsed.tags) ?: continue
            if (!final.streamId.equals(current.streamId, ignoreCase = true)) continue
            onFinal(final.streamId, final.transcriptHash, final.chunkCount)
            return
        }
    }

    /** Stop watching and clear the preview. */
    fun stop() {
        job?.cancel()
        job = null
        watchingStreamId = null
        subscriber = null
        mutable.value = null
    }

    private suspend fun follow(
        nostrGroupId: HexKey,
        startEvent: Event,
        start: AgentTextStreamStart,
    ) {
        val quic = transport ?: return
        // The epoch that DELIVERED the anchor, not the group's current one —
        // the record key context binds it, and a commit landing in between
        // would otherwise derive a key nobody else is using.
        val epoch = marmot.storedEpochs(nostrGroupId)[startEvent.id]
        val crypto =
            try {
                marmot.agentTextStreamCrypto(
                    nostrGroupId = nostrGroupId,
                    streamId = start.streamId.hexToByteArray(),
                    startEventId = startEvent.id.hexToByteArray(),
                    senderPubKey = startEvent.pubKey,
                    epoch = epoch,
                )
            } catch (e: Exception) {
                Log.w("MarmotAgentStreamWatcher", "cannot derive stream keys for $nostrGroupId", e)
                return
            }

        val folding = AgentTextStreamSubscriber(crypto)
        subscriber = folding

        // "A receiver tries advertised candidates in listed order"; the first
        // that yields the matching stream wins, and one that fails is simply
        // skipped.
        for (candidate in start.brokerCandidates) {
            val stream =
                try {
                    quic.subscribe(candidate, start.streamId.hexToByteArray(), startEvent.id.hexToByteArray())
                } catch (e: Exception) {
                    Log.d("MarmotAgentStreamWatcher") { "candidate $candidate unusable: ${e.message}" }
                    continue
                }
            try {
                stream.incoming().collect { record ->
                    folding.accept(record)
                    mutable.value =
                        AgentStreamPreview(
                            streamId = start.streamId,
                            startEventId = startEvent.id,
                            author = startEvent.pubKey,
                            text = folding.previewText,
                            status = folding.status,
                            statusLabel = folding.latestStatus,
                            progressLabel = folding.latestProgress,
                            isConfirmed = false,
                        )
                }
            } catch (e: Exception) {
                Log.d("MarmotAgentStreamWatcher") { "stream from $candidate ended: ${e.message}" }
            } finally {
                runCatching { stream.close() }
            }
            return
        }
    }

    /** Newest kind:1200 in the group's decrypted log, with its own event. */
    private suspend fun findLatestStart(nostrGroupId: HexKey): Pair<Event, AgentTextStreamStart>? {
        var best: Pair<Event, AgentTextStreamStart>? = null
        for (line in marmot.loadStoredMessages(nostrGroupId)) {
            val parsed = Event.fromJsonOrNull(line) ?: continue
            val start = AgentTextStreamStart.fromTags(parsed.kind, parsed.tags) ?: continue
            if (best == null || parsed.createdAt >= best.first.createdAt) best = parsed to start
        }
        return best
    }
}

private object AgentStreamPreviewCopy {
    fun confirmed(p: AgentStreamPreview) =
        AgentStreamPreview(
            streamId = p.streamId,
            startEventId = p.startEventId,
            author = p.author,
            text = p.text,
            status = p.status,
            statusLabel = p.statusLabel,
            progressLabel = p.progressLabel,
            isConfirmed = true,
        )
}
