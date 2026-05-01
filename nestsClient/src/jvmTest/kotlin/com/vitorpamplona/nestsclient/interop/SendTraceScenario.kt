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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.fail

/**
 * Knobs for one diagnostic pump scenario. Holds the parameters that the
 * sweep matrix in [NostrnestsProdAudioTransmissionTest] /
 * [NostrNestsSustainedSendOutcomesInteropTest] varies.
 *
 * Defaults match the production-realistic baseline (same shape that
 * [NostrnestsProdAudioTransmissionTest.sustained_real_time_cadence_two_users]
 * already uses): 100 Opus-sized frames, 20 ms cadence, one moq-lite
 * group per frame.
 */
data class Scenario(
    /** Number of frames the speaker pushes. */
    val frameCount: Int = 100,
    /** Wall-clock delay between consecutive [send] calls. */
    val cadenceMs: Long = 20L,
    /** Bytes per frame. Production Opus @ 32 kbps / 20 ms ≈ 80. */
    val payloadBytes: Int = 80,
    /**
     * How many frames per moq-lite group. The production broadcaster
     * uses `1` (one Opus packet → one uni stream). Higher values pack
     * multiple `varint(size)+payload` frames into the same uni stream
     * to test whether the loss is per-stream-creation or per-byte.
     */
    val framesPerGroup: Int = 1,
    /**
     * Wait this long between issuing the SUBSCRIBE and starting to
     * pump frames so SUBSCRIBE_OK lands at the speaker before the
     * first send (otherwise [MoqLitePublisherHandle.send] returns false
     * for early frames because `inboundSubs.isEmpty()`).
     */
    val subscribeSettleMs: Long = 500L,
    /**
     * Total wall-clock budget for the listener flow to drain
     * [Scenario.frameCount] frames. Includes the pump time itself, so
     * give plenty of grace beyond `frameCount * cadenceMs`.
     */
    val receiveGraceMs: Long = 30_000L,
    /**
     * Pause this long after pushing [pauseAfterFrame] frames before
     * resuming the pump. -1 disables the pause. Lets us see whether
     * the connection wedges during idle time (mid-stream uni-stream
     * cancellation, RTO PTO, etc).
     */
    val pauseAfterFrame: Int = -1,
    /** Pause duration when [pauseAfterFrame] >= 0. */
    val pauseDurationMs: Long = 0L,
    /**
     * Subscribe AFTER the speaker has already pushed this many frames
     * (or run for this many milliseconds — see [subscribeAtFrame]).
     * Tests "from-latest" join semantics — the listener should pick up
     * from the next group boundary, not from the very first.
     */
    val subscribeAtFrame: Int = -1,
    /** Listener consumer adds this delay between each frame consumed. */
    val listenerSlowConsumerMs: Long = 0L,
    /**
     * Number of independent listener subscriptions to attach. > 1
     * exercises fan-out; the relay forwards each group to every
     * subscriber over an independent uni stream.
     */
    val parallelSubscriptions: Int = 1,
    /** Larger payloads (e.g. 4 KB) test stream-write vs stream-creation cost. */
    val verbosePerFrame: Boolean = true,
)

/**
 * One-frame arrival record. Captures wall-clock timing relative to the
 * start of the collector so we can graph cadence drift / stalls
 * post-hoc.
 */
data class FrameArrival(
    val subscriberIndex: Int,
    val wallMs: Long,
    val groupId: Long,
    val objectId: Long,
    val firstByte: Int,
)

/**
 * Outcome of one [SendTraceScenario.run] call. The fields are
 * deliberately denormalised so a single object dumps cleanly through
 * [InteropDebug] and is easy to read in the failure message.
 */
data class ScenarioResult(
    val scenario: Scenario,
    val sendOutcomes: BooleanArray,
    val sendDurationsMicros: LongArray,
    val endGroupErrors: Array<String?>,
    val arrivalsPerSubscriber: List<List<FrameArrival>>,
    val pumpStartedAtMs: Long,
    val pumpDurationMs: Long,
    val collectStartedAtMs: Long,
) {
    val sendTrueCount: Int get() = sendOutcomes.count { it }
    val firstFalseSendIndex: Int get() = sendOutcomes.indexOfFirst { !it }

    /**
     * Per subscriber: `{objectId -> arrival}`. We index by objectId
     * (the per-session monotonic counter from
     * [com.vitorpamplona.nestsclient.MoqLiteNestsListener]) rather than
     * groupId so the sweep stays correct when multiple frames pack
     * into the same moq-lite group (`framesPerGroup > 1`). Each frame
     * the test pushes corresponds to exactly one object, and objectIds
     * arrive 0..N-1 in send order.
     */
    val arrivalIndexPerSubscriber: List<Map<Long, FrameArrival>> by lazy {
        arrivalsPerSubscriber.map { list -> list.associateBy { it.objectId } }
    }

    fun missingObjectIdsFor(subscriberIndex: Int): List<Long> {
        val seen = arrivalIndexPerSubscriber[subscriberIndex].keys
        return (0L until scenario.frameCount.toLong()).filter { it !in seen }
    }

    fun firstSentButLost(subscriberIndex: Int): Int {
        val seen = arrivalIndexPerSubscriber[subscriberIndex].keys
        return (0 until scenario.frameCount).firstOrNull { sendOutcomes[it] && it.toLong() !in seen } ?: -1
    }
}

/**
 * Diagnostic per-frame instrumented pump shared by the production and
 * local-harness end-to-end tests. Bypasses
 * [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster] so we
 * can capture the [MoqLitePublisherHandle.send] boolean per frame
 * (the production broadcaster swallows it via `runCatching {…}`).
 *
 * Logs everything via [InteropDebug.checkpoint]:
 *   - Scenario parameters at start
 *   - Per-frame: send result, send duration, endGroup exception (if any)
 *   - Per-arrival: subscriber index, wall-clock ms, groupId, objectId, first payload byte
 *   - Final summary: counts, missing-group run-length, first false-send,
 *     first sent-but-lost
 *
 * Caller responsibilities:
 *   - Provide a [MoqLitePublisherHandle] that has already
 *     `MoqLiteSession.publish()`'d a broadcast suffix.
 *   - Provide one or more [NestsListener]s, NOT yet subscribed (the
 *     scenario calls [NestsListener.subscribeSpeaker] internally so it
 *     can time the subscription relative to the pump for late-join
 *     scenarios).
 *   - Tear down all of those after [run] returns.
 */
object SendTraceScenario {
    suspend fun run(
        scope: String,
        publisher: MoqLitePublisherHandle,
        listeners: List<NestsListener>,
        speakerPubkeyHex: String,
        scenario: Scenario,
        pumpScope: CoroutineScope,
    ): ScenarioResult {
        require(listeners.size == scenario.parallelSubscriptions) {
            "expected ${scenario.parallelSubscriptions} listener(s), got ${listeners.size}"
        }
        InteropDebug.checkpoint(scope, "scenario=$scenario speaker=${speakerPubkeyHex.take(8)}…")

        val sendOutcomes = BooleanArray(scenario.frameCount)
        val sendDurationsMicros = LongArray(scenario.frameCount)
        val endGroupErrors = arrayOfNulls<String>(scenario.frameCount)
        val arrivalsPerSubscriber =
            List(scenario.parallelSubscriptions) {
                java.util.concurrent.CopyOnWriteArrayList<FrameArrival>()
            }
        val collectStart = System.currentTimeMillis()

        // Subscribe before the pump starts UNLESS subscribeAtFrame says
        // we should wait. We launch one collector per listener; each
        // takes [scenario.frameCount] frames or runs to grace timeout.
        val collectorJobs = mutableListOf<Job>()
        if (scenario.subscribeAtFrame < 0) {
            for ((idx, listener) in listeners.withIndex()) {
                collectorJobs += spawnCollector(scope, idx, listener, speakerPubkeyHex, scenario, arrivalsPerSubscriber[idx], collectStart, pumpScope)
            }
            delay(scenario.subscribeSettleMs)
            InteropDebug.checkpoint(scope, "settled (${scenario.subscribeSettleMs}ms); starting pump")
        } else {
            InteropDebug.checkpoint(
                scope,
                "deferred subscribe — listeners will subscribe AFTER frame ${scenario.subscribeAtFrame}",
            )
        }

        val pumpStart = System.currentTimeMillis()
        val payloadPrefix = ByteArray(scenario.payloadBytes - 1) { 0x4F.toByte() }
        for (i in 0 until scenario.frameCount) {
            // Late-join hook: drop the subscriptions in *now* if we
            // crossed the configured frame index.
            if (scenario.subscribeAtFrame == i) {
                for ((idx, listener) in listeners.withIndex()) {
                    collectorJobs +=
                        spawnCollector(
                            scope,
                            idx,
                            listener,
                            speakerPubkeyHex,
                            scenario,
                            arrivalsPerSubscriber[idx],
                            collectStart,
                            pumpScope,
                        )
                }
                InteropDebug.checkpoint(
                    scope,
                    "subscribed at frame $i (t=${System.currentTimeMillis() - collectStart}ms)",
                )
            }
            if (scenario.pauseAfterFrame == i && scenario.pauseDurationMs > 0) {
                InteropDebug.checkpoint(
                    scope,
                    "pause: idling ${scenario.pauseDurationMs}ms after frame $i (t=${System.currentTimeMillis() - collectStart}ms)",
                )
                delay(scenario.pauseDurationMs)
                InteropDebug.checkpoint(scope, "pause ended (t=${System.currentTimeMillis() - collectStart}ms)")
            }

            val payload = payloadPrefix + byteArrayOf(i.toByte())
            val sendStartedNs = System.nanoTime()
            sendOutcomes[i] = publisher.send(payload)
            val sendElapsedNs = System.nanoTime() - sendStartedNs
            sendDurationsMicros[i] = sendElapsedNs / 1_000

            // endGroup boundary: only end at the configured cadence
            // (default = every frame, which matches production).
            val isGroupBoundary = ((i + 1) % scenario.framesPerGroup) == 0 || i == scenario.frameCount - 1
            if (isGroupBoundary) {
                runCatching { publisher.endGroup() }
                    .onFailure { endGroupErrors[i] = it::class.simpleName + ": " + it.message }
            }
            if (scenario.verbosePerFrame) {
                val tag = if (sendOutcomes[i]) "ok" else "send=false"
                val err = endGroupErrors[i]?.let { ", endGroup err=$it" } ?: ""
                val groupBoundary = if (isGroupBoundary) " ⏎" else ""
                InteropDebug.checkpoint(
                    scope,
                    "tx i=$i $tag dt=${sendDurationsMicros[i]}us$groupBoundary$err " +
                        "(t=${System.currentTimeMillis() - collectStart}ms)",
                )
            }
            if (i < scenario.frameCount - 1 && scenario.cadenceMs > 0) {
                delay(scenario.cadenceMs)
            }
        }
        val pumpDuration = System.currentTimeMillis() - pumpStart
        InteropDebug.checkpoint(
            scope,
            "pump done: ${scenario.frameCount} frames in ${pumpDuration}ms " +
                "(target=${scenario.frameCount * scenario.cadenceMs}ms) " +
                "sendTrue=${sendOutcomes.count { it }}/${scenario.frameCount}",
        )

        // Wait for collectors. If they hit `take(N)` they exit naturally;
        // otherwise the per-collector withTimeoutOrNull cancels them.
        for (job in collectorJobs) {
            withTimeoutOrNull(scenario.receiveGraceMs - (System.currentTimeMillis() - collectStart)) {
                job.join()
            }
            if (job.isActive) job.cancelAndJoin()
        }

        return ScenarioResult(
            scenario = scenario,
            sendOutcomes = sendOutcomes,
            sendDurationsMicros = sendDurationsMicros,
            endGroupErrors = endGroupErrors,
            arrivalsPerSubscriber = arrivalsPerSubscriber.map { it.toList() },
            pumpStartedAtMs = pumpStart - collectStart,
            pumpDurationMs = pumpDuration,
            collectStartedAtMs = collectStart,
        )
    }

    private fun spawnCollector(
        scope: String,
        subscriberIndex: Int,
        listener: NestsListener,
        speakerPubkeyHex: String,
        scenario: Scenario,
        sink: java.util.concurrent.CopyOnWriteArrayList<FrameArrival>,
        collectStart: Long,
        pumpScope: CoroutineScope,
    ): Job =
        pumpScope.launch {
            val sub = listener.subscribeSpeaker(speakerPubkeyHex)
            try {
                withTimeoutOrNull(scenario.receiveGraceMs) {
                    sub.objects
                        .onEach { obj ->
                            val now = System.currentTimeMillis()
                            sink +=
                                FrameArrival(
                                    subscriberIndex = subscriberIndex,
                                    wallMs = now - collectStart,
                                    groupId = obj.groupId,
                                    objectId = obj.objectId,
                                    firstByte =
                                        obj.payload
                                            .firstOrNull()
                                            ?.toInt()
                                            ?.and(0xFF) ?: -1,
                                )
                            if (scenario.verbosePerFrame) {
                                InteropDebug.checkpoint(
                                    scope,
                                    "rx[$subscriberIndex] gid=${obj.groupId} oid=${obj.objectId} " +
                                        "firstByte=0x${(
                                            obj.payload
                                                .firstOrNull()
                                                ?.toInt()
                                                ?.and(0xFF) ?: -1
                                        ).toString(16)} " +
                                        "(t=${now - collectStart}ms)",
                                )
                            }
                            if (scenario.listenerSlowConsumerMs > 0) {
                                delay(scenario.listenerSlowConsumerMs)
                            }
                        }.take(scenario.frameCount)
                        .toList()
                }
            } finally {
                runCatching { sub.unsubscribe() }
            }
        }

    /**
     * Pretty-print the result + assert the expected outcome.
     *
     * @param expectAllReceived true → every frame must have arrived at
     *   every subscriber. false → just dump the diagnostic summary
     *   (lets a "reproduce the cliff" scenario succeed even when the
     *   relay drops trailing frames).
     */
    fun reportAndAssert(
        scope: String,
        result: ScenarioResult,
        expectAllReceived: Boolean,
    ) {
        val s = result.scenario
        for (subscriberIndex in 0 until s.parallelSubscriptions) {
            val arrivals = result.arrivalsPerSubscriber[subscriberIndex]
            val missing = result.missingObjectIdsFor(subscriberIndex)
            val firstSentButLost = result.firstSentButLost(subscriberIndex)
            val firstArrival = arrivals.firstOrNull()?.wallMs ?: -1
            val lastArrival = arrivals.lastOrNull()?.wallMs ?: -1
            InteropDebug.checkpoint(
                scope,
                "sub[$subscriberIndex] received=${arrivals.size}/${s.frameCount} " +
                    "firstArrival=${firstArrival}ms lastArrival=${lastArrival}ms " +
                    "firstFalseSend=${result.firstFalseSendIndex} " +
                    "firstSentButLost=$firstSentButLost " +
                    "missing=${runs(missing)}",
            )
        }

        // Average / min / max / p99 send latency, plus count of sends
        // that took > 1 ms (a hint that publisher.send() blocked on a
        // mutex / IO).
        val nonZero = result.sendDurationsMicros.filter { it > 0 }
        if (nonZero.isNotEmpty()) {
            val sorted = nonZero.sorted()
            val p50 = sorted[sorted.size / 2]
            val p99 = sorted[(sorted.size * 99 / 100).coerceAtMost(sorted.size - 1)]
            val max = sorted.last()
            val sloweries = sorted.count { it > 1_000 }
            InteropDebug.checkpoint(
                scope,
                "send latency: p50=${p50}us p99=${p99}us max=${max}us sends>1ms=$sloweries",
            )
        }
        if (result.endGroupErrors.any { it != null }) {
            val errs = result.endGroupErrors.withIndex().filter { it.value != null }
            InteropDebug.checkpoint(
                scope,
                "endGroup errors: ${errs.joinToString { "i=${it.index} ${it.value}" }}",
            )
        }

        if (expectAllReceived) {
            for (subIdx in 0 until s.parallelSubscriptions) {
                val arrivals = result.arrivalsPerSubscriber[subIdx]
                if (arrivals.size != s.frameCount) {
                    fail(
                        "[$scope] subscriber $subIdx received ${arrivals.size}/${s.frameCount} — " +
                            "missing=${runs(result.missingObjectIdsFor(subIdx))} " +
                            "firstSentButLost=${result.firstSentButLost(subIdx)} " +
                            "firstFalseSend=${result.firstFalseSendIndex}",
                    )
                }
            }
        }
    }

    /** Compress a sorted list of integers into run-length ranges, e.g. `[3-7,42,90-99]`. */
    private fun runs(values: List<Long>): String {
        if (values.isEmpty()) return "[]"
        return buildString {
            append('[')
            var run: Pair<Long, Long>? = null

            fun flush() {
                run?.let {
                    if (length > 1) append(',')
                    if (it.first == it.second) append(it.first) else append("${it.first}-${it.second}")
                }
            }
            for (v in values) {
                run =
                    when {
                        run == null -> {
                            v to v
                        }

                        v == run.second + 1 -> {
                            run.first to v
                        }

                        else -> {
                            flush()
                            v to v
                        }
                    }
            }
            flush()
            append(']')
        }
    }
}
