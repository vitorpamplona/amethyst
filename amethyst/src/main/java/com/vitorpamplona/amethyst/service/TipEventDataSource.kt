/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.util.Log
import androidx.lifecycle.asFlow
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

data class TipRecipient(val address: String, val pubKey: HexKey? = null)

object TipEventDataSource {
    private const val MIN_CONFIRMATIONS = 10
    private val USER_METADATA_TIMEOUT = 10.seconds
    private val EVENT_METADATA_TIMEOUT = 10.seconds

    private val CHECK_DELAY = 500.milliseconds
    private const val MAX_EVENTS_TO_WATCH = 400

    lateinit var account: Account

    var job: Job? = null

    fun start() {
        val scope = Amethyst.instance.applicationIOScope
        job =
            scope.launch {
                var lastCheck: TimeSource.Monotonic.ValueTimeMark? = null
                var lastEvent: TipEvent? = null

                val invalidEvents: MutableSet<HexKey> = mutableSetOf()
                val verifiedEvents: MutableSet<HexKey> = mutableSetOf()
                val seenTxids = mutableSetOf<String>()

                var lastHeight: Long? = null

                val eventsToWatch: LinkedHashSet<TipEvent> = LinkedHashSet()

                combinedFlow.onEach { values ->
                    val event = values[0] as TipEvent?
                    val height = values[1] as Long?

                    if (event != null) {
                        if (event.id in invalidEvents) {
                            return@onEach
                        }

                        val proof = event.tipProof()
                        if (proof == null) {
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid proof")
                            return@onEach
                        }

                        if (event.id !in verifiedEvents && event !in eventsToWatch && proof.txId in seenTxids) {
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with duplicate txid")
                            return@onEach
                        } else if (proof.txId !in seenTxids) {
                            seenTxids += proof.txId
                        }
                    }

                    if (lastEvent == event && lastHeight != height) {
                        val now = TimeSource.Monotonic.markNow()
                        if (lastCheck != null && lastCheck!! + CHECK_DELAY < now) {
                            // the wallet is syncing, avoid checking too often
                            return@onEach
                        }
                        lastCheck = now
                        lastHeight = height
                        lastEvent = event
                    }

                    event?.let {
                        if (eventsToWatch.size == MAX_EVENTS_TO_WATCH) {
                            eventsToWatch.remove(eventsToWatch.first())
                        }

                        eventsToWatch += it
                    }

                    // start from the latest (because it's presumably the one the user requested last)
                    for (event in eventsToWatch.reversed()) {
                        var tipProof = event.tipProof()
                        if (tipProof == null) {
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid proof format")
                            invalidEvents += event.id
                            eventsToWatch -= event
                            return@onEach
                        }

                        if (tipProof.proofs.isEmpty()) {
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with no proofs")
                            invalidEvents += event.id
                            eventsToWatch -= event
                            return@onEach
                        }

                        val taggedUsers = event.taggedUsers()
                        // the tip event must reference at least one user (for the note author or profile). this is to
                        // make it easier for clients to display how many tips a note/user has received
                        if (taggedUsers.isEmpty()) {
                            invalidEvents += event.id
                            eventsToWatch -= event
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with no tagged users")
                            return@onEach
                        }

                        val taggedEvents = event.taggedEvents()
                        if (taggedEvents.size > 1) {
                            invalidEvents += event.id
                            eventsToWatch -= event
                            Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with too many tagged events")
                            return@onEach
                        }
                        val taggedEventId = taggedEvents.firstOrNull()

                        var proofs: MutableList<Pair<String, String>> = mutableListOf()
                        for ((proof, recipient) in tipProof.proofs) {
                            if (recipient.isEmpty()) {
                                Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid proof recipient specification")
                                invalidEvents += event.id
                                eventsToWatch -= event
                                return@onEach
                            }

                            val recipient = recipient.first()
                            var recipientUser: User? = null
                            var recipientAddress: String? = null
                            if (recipient.length == 95) {
                                // monero address
                                recipientAddress = recipient
                            } else {
                                recipientUser = getUser(recipient, USER_METADATA_TIMEOUT)
                                if (recipientUser == null) {
                                    eventsToWatch += event
                                    continue
                                }
                            }

                            if (recipientUser != null) {
                                // it's unlikely for info to be null because getUser handles it
                                recipientAddress = recipientUser.info?.moneroAddress()
                            }

                            if (recipientAddress == null) {
                                // assume that the event proof is invalid if the recipient
                                // does not have an address (could lead to excessive memory
                                // consumption otherwise)
                                invalidEvents += event.id
                                eventsToWatch -= event
                                Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with recipient who does not have Monero address")
                                return@onEach
                            }

                            proofs += proof to recipientAddress

                            var isInTipSplitSetup = false
                            var foundTaggedEvent = true
                            var taggedEvent: Note? = null
                            if (taggedEventId != null) {
                                try {
                                    taggedEvent = getEvent(taggedEventId, EVENT_METADATA_TIMEOUT)
                                    if (taggedEvent == null) {
                                        foundTaggedEvent = false
                                    } else {
                                        // taggedEvent.event is unlikely to be null because getEvent handles it
                                        isInTipSplitSetup = taggedEvent.event?.tipSplitSetup()?.firstOrNull {
                                            if (!it.isAddress) {
                                                if (recipientUser != null) {
                                                    recipientUser.pubkeyHex == it.addressOrPubKeyHex
                                                } else {
                                                    try {
                                                        val taggedEventRecipientUser = getUser(it.addressOrPubKeyHex, USER_METADATA_TIMEOUT)
                                                        if (taggedEventRecipientUser == null) {
                                                            false
                                                        } else {
                                                            recipientAddress == taggedEventRecipientUser.info?.moneroAddress()
                                                        }
                                                    } catch (e: IllegalArgumentException) {
                                                        false
                                                    }
                                                }
                                            } else {
                                                it.addressOrPubKeyHex == recipientAddress
                                            }
                                        } != null
                                    }
                                } catch (e: IllegalArgumentException) {
                                    // invalid event id
                                    invalidEvents += event.id
                                    eventsToWatch -= event
                                    Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid tagged event id")
                                    return@onEach
                                }
                            }

                            var isInTaggedUsers = false
                            var foundAllUsers = true

                            if (!isInTipSplitSetup) {
                                for (taggedUserId in taggedUsers) {
                                    try {
                                        val taggedUser = getUser(taggedUserId, USER_METADATA_TIMEOUT)
                                        if (taggedUser == null) {
                                            foundAllUsers = false
                                        } else {
                                            // info is unlikely to be null because getUser handles it
                                            if (taggedUser.info?.moneroAddress() == recipientAddress) {
                                                isInTaggedUsers = true
                                                break
                                            }
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        // invalid pubkey
                                        invalidEvents += event.id
                                        eventsToWatch -= event
                                        Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid tagged pubkey")
                                        return@onEach
                                    }
                                }
                            }

                            if (!isInTipSplitSetup && !isInTaggedUsers) {
                                if (foundTaggedEvent && foundAllUsers) {
                                    Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid recipient")
                                    invalidEvents += event.id
                                    eventsToWatch -= event
                                    return@onEach
                                }

                                // we couldn't find the user, so we don't have enough information to determine whether the proof is valid.
                                // we may be able to find the user later (e.g. if the relays change), so keep it in eventsToWatch
                                eventsToWatch += event
                                continue
                            }
                        }

                        for ((_, address) in proofs) {
                            val proofsWithSameAddress = proofs.filter { (_, proofAddress) -> proofAddress == address }
                            if (proofsWithSameAddress.size > 1) {
                                proofs = proofs.minusElement(proofsWithSameAddress.first()).toMutableList()
                            }
                        }

                        for ((proof, address) in proofs) {
                            val transfer = account.checkProof(event.pubKey, address, tipProof.txId, proof)
                            if (transfer == null) {
                                invalidEvents += event.id
                                eventsToWatch -= event
                                Log.w("TipEventDataSource", "Ignoring TipEvent ${event.id} with invalid proof")
                                return@onEach
                            }

                            if (transfer.confirmations < MIN_CONFIRMATIONS) {
                                eventsToWatch += event
                                return@onEach
                            }

                            event.valueByUser[address] = transfer.amount.toULong()
                            // if we've made it until here, all proofs are valid and recipients are unique. remove from eventsToWatch iff all users
                            // have already been verified.
                            // if we didn't have this check here, we could end up removing a partially verified proof if the confirmations check
                            // succeeds only for the last user.
                            if (event.valueByUser.size == proofs.size) {
                                eventsToWatch -= event
                                verifiedEvents += event.id
                            }
                            LocalCache.consume(event)
                        }
                    }
                }
                    .collect()
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private val tipEventChannel: Channel<TipEvent> = Channel()

    private val combinedFlow = instantCombine(tipEventChannel.receiveAsFlow(), MoneroDataSource.walletHeight())

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class, FlowPreview::class)
    fun consume(event: TipEvent) {
        GlobalScope.launch {
            tipEventChannel.send(event)
        }
    }
}

@OptIn(FlowPreview::class, DelicateCoroutinesApi::class)
suspend fun getEvent(
    id: HexKey,
    timeout: Duration,
): Note? {
    var note: Note? = LocalCache.getOrCreateNote(id)
    // this needs to be executed in the main thread so livedata works.
    GlobalScope.launch(Dispatchers.Main) {
        note =
            try {
                note!!.live().metadata.asFlow()
                    .timeout(timeout)
                    .catch { }
                    .first { it.note.event != null }
                    .note
            } catch (e: NoSuchElementException) {
                null
            }
    }.join()
    return note
}

@OptIn(FlowPreview::class, DelicateCoroutinesApi::class)
suspend fun getUser(
    id: HexKey,
    timeout: Duration,
): User? {
    var user: User? = LocalCache.getOrCreateUser(id)
    GlobalScope.launch(Dispatchers.Main) {
        user =
            try {
                user!!.live().metadata.asFlow()
                    .timeout(timeout)
                    .catch { }
                    .first { it.user.info != null }
                    .user
            } catch (e: NoSuchElementException) {
                null
            }
    }.join()
    return user
}

inline fun <reified T> instantCombine(vararg flows: Flow<T>) =
    channelFlow {
        val array: Array<Any?> =
            Array(flows.size) {
                null
            }

        flows.forEachIndexed { index, flow ->
            launch {
                flow.collect { emittedElement ->
                    array[index] = emittedElement
                    send(array.clone())
                }
            }
        }
    }
