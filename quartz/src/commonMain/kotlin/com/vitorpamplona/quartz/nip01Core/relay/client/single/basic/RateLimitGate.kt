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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One relay's outgoing back-off: holds the commands of a type the relay refused as
 * `rate-limited:` WITH a back-off (NIP-01's `params`, see
 * [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.RetryAfter]) until it passes, then
 * releases them in order.
 *
 * It sits on the connection ([BasicRelayClient]), where every command to the relay passes,
 * so nothing that sends a `REQ`, `EVENT` or `COUNT` can skip it: new subscriptions and
 * publishes, a reconnect's replay, a subscription's update after its `EOSE`, an auth retry.
 * The connection feeds it every message before its listener, so a refusal's back-off is in
 * force before anyone reacts to it.
 *
 * The back-off covers every command of the refused type (the spec's "same message type"):
 * `EVENT` after a refused `OK`, `REQ` or `COUNT` after a `CLOSED`, by which of the two the
 * `CLOSED` answered. What arrives while it runs queues in arrival order, keyed by
 * subscription or event id, so a newer `REQ` replaces a held one for the same subscription and
 * a `CLOSE` drops it. Re-sending what was refused is the caller's call (the pool knows whether
 * the subscription is still wanted): handed back right after the refusal, it goes to the head
 * of the queue.
 *
 * When the back-off passes the queue drains one command at a time. A stated back-off says when
 * the relay takes the NEXT command, not how many: a sliding window (nostream states the whole
 * window, e.g. 60 s for 12 notes a minute) then takes many, a token bucket one per token. So
 * each command the relay answers without throttling (`OK` true, `EOSE`, a `COUNT` result, or a
 * refusal for another reason) releases the next at once, and one it refuses again waits out its
 * new back-off. Unanswered, the next goes after the LONGEST back-off the relay stated for the
 * type plus a tenth: a token bucket states the time to its next token, a little under it when
 * stated just after a token was spent (measured: 486 ms on a 500 ms bucket).
 *
 * A back-off longer than [maxDelayMs] is not honored (see [honors]): the refused command stays
 * parked and the next reconnect re-sends it. A disconnect drops what is queued, since the
 * reconnect replays everything still wanted, but keeps the back-off running for that replay.
 */
class RateLimitGate(
    private val scope: CoroutineScope,
    /** Writes [Command] to the socket; false when nothing went out (the socket is gone). */
    private val send: (Command) -> Boolean,
    private val maxDelayMs: Long = MAX_DELAY_MS,
    private val clock: () -> Long = TimeUtils::nowMillis,
) {
    /** One command type's back-off and what it holds. */
    private class Lane(
        var dueAtMs: Long,
        /** The longest back-off the relay stated for this type; see [spacing]. */
        var longestBackOffMs: Long,
    ) {
        /** Keys in release order, and the latest command for each. */
        private val order = ArrayDeque<String>()
        private val commands = HashMap<String, Command>()

        /** True once a command went out: a refusal from then on is a released one coming back. */
        var draining = false

        /** Refused while draining: handed back, they go to the head of the queue. */
        val refused = HashSet<String>()

        /** The key released last, until the relay answers it; see [onIncoming]. */
        var inFlight: String? = null

        /** The deadline set when [inFlight] went out: a later one means a newer refusal moved it. */
        var releasedDueAtMs = 0L

        /** Cuts the drain's wait short when [inFlight] is answered. */
        val wake = Channel<Unit>(Channel.CONFLATED)

        fun isEmpty() = order.isEmpty()

        fun spacing() = longestBackOffMs + longestBackOffMs / SPACING_MARGIN_DIVISOR + 1

        fun enqueue(
            key: String,
            cmd: Command,
        ) {
            if (commands.put(key, cmd) != null) return
            if (refused.remove(key)) order.addFirst(key) else order.addLast(key)
        }

        fun remove(key: String) {
            if (commands.remove(key) != null) order.remove(key)
        }

        fun removeFirst(): Command {
            val key = order.removeFirst()
            inFlight = key
            return commands.remove(key)!!
        }

        fun clearQueue() {
            order.clear()
            commands.clear()
            refused.clear()
            inFlight = null
        }
    }

    private val lock = PlatformLock()
    private val lanes = HashMap<String, Lane>()

    /** Subscriptions whose last command was a `COUNT`: their `CLOSED` refuses a `COUNT`, not a `REQ`. */
    private val counts = HashSet<String>()

    /** True when a back-off of [delayMs] is one this gate holds commands for. */
    private fun holdsFor(delayMs: Long) = delayMs in 1..maxDelayMs

    /** True while commands of [type] (a command label: `REQ`, `EVENT`, `COUNT`) are held. */
    fun isHolding(type: String): Boolean = lock.withLock { type in lanes }

    /**
     * Whether [cmd] may go out now. False means it is held, to be sent when its turn comes:
     * the caller must not send it.
     */
    fun admit(cmd: Command): Boolean {
        val type: String
        val key: String
        when (cmd) {
            is ReqCmd -> {
                type = ReqCmd.LABEL
                key = cmd.subId
            }

            is EventCmd -> {
                type = EventCmd.LABEL
                key = cmd.event.id
            }

            is CountCmd -> {
                type = CountCmd.LABEL
                key = cmd.queryId
            }

            is CloseCmd -> {
                lock.withLock {
                    counts.remove(cmd.subId)
                    lanes[ReqCmd.LABEL]?.remove(cmd.subId)
                    lanes[CountCmd.LABEL]?.remove(cmd.subId)
                }
                return true
            }

            else -> {
                return true
            }
        }
        return lock.withLock {
            when (cmd) {
                is ReqCmd -> counts.remove(key)
                is CountCmd -> counts.add(key)
                else -> {}
            }
            val lane = lanes[type] ?: return@withLock true
            lane.enqueue(key, cmd)
            false
        }
    }

    /** Reads every message from the relay, before anyone else does. */
    fun onIncoming(msg: Message) {
        when (msg) {
            is OkMessage -> answered(EventCmd.LABEL, msg.eventId, msg.success, msg.message, msg.retryAfterMs)

            is ClosedMessage -> {
                val type = lock.withLock { if (counts.remove(msg.subId)) CountCmd.LABEL else ReqCmd.LABEL }
                answered(type, msg.subId, false, msg.message, msg.retryAfterMs)
            }

            is EoseMessage -> answered(ReqCmd.LABEL, msg.subId, true, "", null)

            is CountMessage -> {
                lock.withLock { counts.remove(msg.queryId) }
                answered(CountCmd.LABEL, msg.queryId, true, "", null)
            }

            else -> {}
        }
    }

    /** The connection dropped: what is queued goes, the back-offs stay for the reconnect's replay. */
    fun onDisconnected() =
        lock.withLock {
            counts.clear()
            lanes.values.forEach { it.clearQueue() }
        }

    private fun answered(
        type: String,
        key: String,
        accepted: Boolean,
        message: String,
        backOffMs: Long?,
    ) {
        if (backOffMs != null && holdsFor(backOffMs)) {
            refused(type, key, backOffMs)
            return
        }
        // Throttled without a stated back-off: no sign the relay takes more yet.
        if (!accepted && MachineReadablePrefix.parse(message) == MachineReadablePrefix.RATE_LIMITED) return

        // Answered without throttling: if it was the command just released, the relay takes
        // commands again and the next one goes now instead of waiting out the spacing.
        val lane =
            lock.withLock {
                val lane = lanes[type] ?: return
                if (lane.inFlight != key) return
                lane.inFlight = null
                // A refusal that landed meanwhile named a later deadline: honor it.
                if (lane.dueAtMs != lane.releasedDueAtMs) return
                lane.dueAtMs = clock()
                lane
            }
        lane.wake.trySend(Unit)
    }

    private fun refused(
        type: String,
        key: String,
        backOffMs: Long,
    ) {
        val dueAt = clock() + backOffMs
        val newLane =
            lock.withLock {
                val lane = lanes[type]
                if (lane == null) {
                    Lane(dueAt, backOffMs).also { lanes[type] = it }
                } else {
                    if (lane.inFlight == key) lane.inFlight = null
                    if (lane.draining) lane.refused.add(key)
                    if (dueAt > lane.dueAtMs) lane.dueAtMs = dueAt
                    if (backOffMs > lane.longestBackOffMs) lane.longestBackOffMs = backOffMs
                    null
                }
            }
        if (newLane != null) scope.launch { drain(type, newLane) }
    }

    /** Releases [lane]'s commands on its schedule, and retires it once its back-off passed with nothing left. */
    private suspend fun drain(
        type: String,
        lane: Lane,
    ) {
        while (true) {
            // The deadline moves while we wait (a newer refusal named a later one, an answer
            // pulled it in), so re-read it after every wait and only release once it passed.
            var due: Command? = null
            var nextAt = 0L
            val waitMs =
                lock.withLock {
                    if (lanes[type] !== lane) return
                    val now = clock()
                    val left = lane.dueAtMs - now
                    if (left <= 0) {
                        if (lane.isEmpty()) {
                            lanes.remove(type)
                            return
                        }
                        due = lane.removeFirst()
                        lane.draining = true
                        nextAt = now + lane.spacing()
                        lane.dueAtMs = nextAt
                        lane.releasedDueAtMs = nextAt
                    }
                    left
                }
            val cmd = due
            if (cmd != null) {
                // A failure here must not end the loop: the lane would stay registered with
                // nobody draining it, and every later command of its type would be held forever.
                val sent =
                    try {
                        send(cmd)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("RateLimitGate", "Failed to send a held ${cmd.label()}", e)
                        false
                    }
                if (!sent) {
                    // Nothing used this slot: release the next now, unless a refusal moved the deadline.
                    lock.withLock {
                        lane.inFlight = null
                        if (lane.dueAtMs == nextAt) lane.dueAtMs = clock()
                    }
                }
            } else {
                withTimeoutOrNull(waitMs) { lane.wake.receive() }
            }
        }
    }

    companion object {
        /** Ten minutes: a relay asking for longer is better served by the next reconnect. */
        const val MAX_DELAY_MS = 10 * 60 * 1000L

        /** The spacing adds 1/10 of the longest back-off: see the class notes. */
        private const val SPACING_MARGIN_DIVISOR = 10

        /** True when a gate with the default [MAX_DELAY_MS] holds commands for a back-off of [delayMs]. */
        fun honors(delayMs: Long) = delayMs in 1..MAX_DELAY_MS
    }
}
