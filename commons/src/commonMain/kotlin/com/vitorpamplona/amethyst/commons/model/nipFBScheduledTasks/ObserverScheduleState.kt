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
package com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The account's morning schedule as this device knows it. */
@Immutable
sealed interface SyncedSchedule {
    /** No schedule event seen yet: none exists, or it hasn't arrived. Decide nothing on it. */
    data object Unknown : SyncedSchedule

    data class Readable(
        val event: ScheduledTaskEvent,
        val task: ScheduledTask,
    ) : SyncedSchedule

    /** An event this signer can't decrypt or doesn't understand (a newer `v`): never overwritten. */
    data class Unreadable(
        val event: ScheduledTaskEvent,
    ) : SyncedSchedule
}

/**
 * The newest schedule as a runner reads it before claiming: [quorum] when more than half of the
 * write [relays] answered (NIP-FB: otherwise the device doesn't run).
 */
class QuorumRead(
    val event: ScheduledTaskEvent?,
    val task: ScheduledTask?,
    val quorum: Boolean,
    val relays: Set<NormalizedRelayUrl>,
)

/** How an edit of the schedule ended. */
@Immutable
sealed interface ScheduleEdit {
    data class Saved(
        val task: ScheduledTask,
    ) : ScheduleEdit

    /** The edit changed nothing, so nothing was written. */
    data object Unchanged : ScheduleEdit

    /** Not more than half of the reader's write relays answered the read: nothing was written. */
    data object Unreachable : ScheduleEdit

    /** Written, but not more than half of the write relays accepted it: other devices may not see it. */
    data object NotAccepted : ScheduleEdit

    /** The newest event can't be read here: rewriting it would lose what it holds. */
    data object Unreadable : ScheduleEdit

    data object CannotSign : ScheduleEdit
}

/**
 * What an edit writes, from what was read: [current] is the newest event's task (null when there
 * is no event, or it is unreadable: [hasEvent] tells them apart), [quorum] whether more than half
 * of the write relays answered the read. Nothing is written without that: too few answers is
 * not "no schedule". Null task in the result means nothing is written, with that outcome.
 */
fun planScheduleEdit(
    hasEvent: Boolean,
    current: ScheduledTask?,
    quorum: Boolean,
    transform: (ScheduledTask?) -> ScheduledTask?,
): Pair<ScheduledTask?, ScheduleEdit> {
    if (!quorum) return null to ScheduleEdit.Unreachable
    if (hasEvent && current == null) return null to ScheduleEdit.Unreadable
    val next = transform(current) ?: return null to ScheduleEdit.Unchanged
    if (next == current) return null to ScheduleEdit.Unchanged
    return next to ScheduleEdit.Saved(next)
}

/**
 * The account's morning Observer schedule, a NIP-FB scheduled task (kind 34817, `d` =
 * `observer`), decrypted from the cache as it arrives with the account's own lists, read and
 * written only on the reader's NIP-65 write relays ([writeRelays], never a default or fallback):
 * more than half of them must answer each read and accept each write
 * (`quartz/plans/2026-11-11-observer-site.md`, "The morning schedule, synced").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserverScheduleState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    private val client: INostrClient,
    private val writeRelays: StateFlow<Set<NormalizedRelayUrl>>,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val scheduleNote = cache.getOrCreateAddressableNote(ObserverSchedule.address(signer.pubKey))

    private fun event(): ScheduledTaskEvent? = scheduleNote.event as? ScheduledTaskEvent

    /** Decrypted once per event: the screens, the claim and its watch all read through it. */
    private val decryption = PrivateTagArrayEventCache<ScheduledTaskEvent>(signer)

    /** [event]'s tags, public and private; null when this signer can't decrypt it (yet). */
    suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? {
        if (event.pubKey != signer.pubKey) return null
        if (event.content.isBlank()) return ScheduledTask(event.tags, emptyArray())
        return decryption.privateTags(event)?.let { ScheduledTask(event.tags, it) }
    }

    private suspend fun decode(event: ScheduledTaskEvent?): SyncedSchedule {
        if (event == null) return SyncedSchedule.Unknown
        val task = taskOf(event) ?: return SyncedSchedule.Unreadable(event)
        return SyncedSchedule.Readable(event, task)
    }

    /**
     * The schedule as the cache holds it. A content that doesn't decrypt is tried again a few
     * times with a growing pause: a signer that timed out once isn't a schedule that can never be
     * read.
     */
    val flow: StateFlow<SyncedSchedule> =
        scheduleNote
            .flow()
            .metadata.stateFlow
            .map { it.note.event as? ScheduledTaskEvent }
            .onStart { emit(event()) }
            .distinctUntilChanged()
            .transformLatest { event ->
                var decoded = decode(event)
                emit(decoded)
                var tries = 0
                while (decoded is SyncedSchedule.Unreadable && tries < DECRYPT_RETRIES) {
                    tries++
                    delay(DECRYPT_RETRY_MS * tries)
                    decoded = decode(event)
                    emit(decoded)
                }
            }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, SyncedSchedule.Unknown)

    private val editing = Mutex()

    /**
     * The newest schedule across every write relay, read to EOSE, and the cache: what a claim or an
     * edit starts from. Every event read goes through the cache, so the screens see it too.
     */
    suspend fun readQuorum(): QuorumRead {
        val relays = writeRelays.value
        if (relays.isEmpty()) return QuorumRead(null, null, false, relays)
        val read = mutableListOf<ScheduledTaskEvent>()
        val result =
            try {
                client.fetchAllWithHooks(relays.associateWith { listOf(ObserverSchedule.filter(signer.pubKey)) }, idleTimeoutMs = READ_IDLE_MS) { relay, event ->
                    if (event is ScheduledTaskEvent && ObserverSchedule.isObserverSchedule(event) && event.pubKey == signer.pubKey && event.verify()) {
                        cache.justConsume(event, relay, wasVerified = true)
                        read += event
                    }
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the morning schedule", e)
                null
            }
        val answered = result?.doneReasons?.values?.count { it == DONE_REASON_EOSE } ?: 0
        val newest = ScheduledTaskClaims.winner(read + listOfNotNull(event()))
        return QuorumRead(newest, newest?.let { taskOf(it) }, ScheduledTaskClaims.hasQuorum(relays.size, answered), relays)
    }

    /**
     * Signs [task] as the event after [previous] and sends it to the write [relays], waiting for
     * their OKs; true when more than half accepted it.
     */
    suspend fun publishClaim(
        previous: ScheduledTaskEvent?,
        task: ScheduledTask,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<ScheduledTaskEvent, Boolean> {
        val signed = signer.sign(ObserverSchedule.build(task, signer, previous, TimeUtils.now()))
        cache.justConsumeMyOwnEvent(signed)
        val accepted =
            try {
                client.publishAndCollectResults(signed, relays, timeoutInSeconds = WRITE_CONFIRM_SECONDS).count { it.value.accepted }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not publish the schedule", e)
                0
            }
        return signed to ScheduledTaskClaims.hasQuorum(relays.size, accepted)
    }

    /** Every schedule event the write [relays] send from now on (verified, this author's): a claimer listens while it runs. */
    fun watch(relays: Set<NormalizedRelayUrl>): Flow<List<ScheduledTaskEvent>> =
        relays
            .map { relay ->
                client.subscribeAsFlow(relay, ObserverSchedule.filter(signer.pubKey)).map { events ->
                    events.filterIsInstance<ScheduledTaskEvent>().filter { it.pubKey == signer.pubKey && ObserverSchedule.isObserverSchedule(it) && it.verify() }
                }
            }.merge()

    /**
     * Read-modify-write (NIP-FB, Editing): the newest event across the write relays and the cache
     * (more than half must answer), [transform]ed (null or the same task writes nothing), signed
     * with `created_at = max(now, previous + 1)` and sent to the same relays (more than half must
     * accept). One edit at a time on this device.
     */
    suspend fun edit(
        now: Long = TimeUtils.now(),
        transform: (ScheduledTask?) -> ScheduledTask?,
    ): ScheduleEdit =
        editing.withLock {
            if (!signer.isWriteable()) return@withLock ScheduleEdit.CannotSign
            val read = readQuorum()
            val previous = read.event
            val (next, outcome) = planScheduleEdit(previous != null, read.task, read.quorum, transform)
            if (next == null) return@withLock outcome
            try {
                val signed = signer.sign(ObserverSchedule.build(next, signer, previous, now))
                cache.justConsumeMyOwnEvent(signed)
                val accepted = client.publishAndCollectResults(signed, read.relays, timeoutInSeconds = WRITE_CONFIRM_SECONDS).count { it.value.accepted }
                if (ScheduledTaskClaims.hasQuorum(read.relays.size, accepted)) outcome else ScheduleEdit.NotAccepted
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the morning schedule", e)
                ScheduleEdit.CannotSign
            }
        }

    companion object {
        private const val TAG = "ObserverScheduleState"
        private const val READ_IDLE_MS = 8_000L
        private const val WRITE_CONFIRM_SECONDS = 10L
        private const val DECRYPT_RETRIES = 4
        private const val DECRYPT_RETRY_MS = 30_000L
    }
}
