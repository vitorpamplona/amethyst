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
package com.vitorpamplona.amethyst.model

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.actions.updated
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.combineWith
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

@Stable
class AddressableNote(val address: ATag) : Note(address.toTag()) {
    override fun idNote() = address.toNAddr()

    override fun toNEvent() = address.toNAddr()

    override fun idDisplayNote() = idNote().toShortenHex()

    override fun address() = address

    override fun createdAt(): Long? {
        if (event == null) return null

        val publishedAt = (event as? LongTextNoteEvent)?.publishedAt() ?: Long.MAX_VALUE
        val lastCreatedAt = event?.createdAt() ?: Long.MAX_VALUE

        return minOf(publishedAt, lastCreatedAt)
    }

    fun dTag(): String? {
        return (event as? AddressableEvent)?.dTag()
    }

    override fun wasOrShouldBeDeletedBy(
        deletionEvents: Set<HexKey>,
        deletionAddressables: Set<ATag>,
    ): Boolean {
        val thisEvent = event
        return deletionAddressables.contains(address) || (thisEvent != null && deletionEvents.contains(thisEvent.id()))
    }
}

@Stable
open class Note(val idHex: String) {
    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var event: EventInterface? = null
    var author: User? = null
    var replyTo: List<Note>? = null

    // These fields are updated every time an event related to this note is received.
    var replies = listOf<Note>()
        private set

    var reactions = mapOf<String, List<Note>>()
        private set

    var boosts = listOf<Note>()
        private set

    var reports = mapOf<User, List<Note>>()
        private set

    var zaps = mapOf<Note, Note?>()
        private set

    var zapsAmount: BigDecimal = BigDecimal.ZERO

    var zapPayments = mapOf<Note, Note?>()
        private set

    var relays = listOf<RelayBriefInfoCache.RelayBriefInfo>()
        private set

    var lastReactionsDownloadTime: Map<String, EOSETime> = emptyMap()

    fun id() = Hex.decode(idHex)

    open fun idNote() = id().toNote()

    open fun toNEvent(): String {
        val myEvent = event
        return if (myEvent is WrappedEvent) {
            val host = myEvent.host
            if (host != null) {
                Nip19Bech32.createNEvent(
                    host.id,
                    host.pubKey,
                    host.kind(),
                    relays.firstOrNull()?.url,
                )
            } else {
                Nip19Bech32.createNEvent(idHex, author?.pubkeyHex, event?.kind(), relays.firstOrNull()?.url)
            }
        } else {
            Nip19Bech32.createNEvent(idHex, author?.pubkeyHex, event?.kind(), relays.firstOrNull()?.url)
        }
    }

    fun toNostrUri(): String {
        return "nostr:${toNEvent()}"
    }

    open fun idDisplayNote() = idNote().toShortenHex()

    fun channelHex(): HexKey? {
        return if (
            event is ChannelMessageEvent ||
            event is ChannelMetadataEvent ||
            event is ChannelCreateEvent ||
            event is LiveActivitiesChatMessageEvent ||
            event is LiveActivitiesEvent
        ) {
            (event as? ChannelMessageEvent)?.channel()
                ?: (event as? ChannelMetadataEvent)?.channel()
                ?: (event as? ChannelCreateEvent)?.id
                ?: (event as? LiveActivitiesChatMessageEvent)?.activity()?.toTag()
                ?: (event as? LiveActivitiesEvent)?.address()?.toTag()
        } else {
            null
        }
    }

    open fun address(): ATag? = null

    open fun createdAt() = event?.createdAt()

    fun isDraft() = event is DraftEvent

    fun loadEvent(
        event: Event,
        author: User,
        replyTo: List<Note>,
    ) {
        if (this.event?.id() != event.id()) {
            this.event = event
            this.author = author
            this.replyTo = replyTo

            liveSet?.innerMetadata?.invalidateData()
            flowSet?.metadata?.invalidateData()
        }
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HH:mm:ss"))
    }

    data class LevelSignature(val signature: String, val createdAt: Long?, val author: User?)

    /**
     * This method caches signatures during each execution to avoid recalculation in longer threads
     */
    fun replyLevelSignature(
        eventsToConsider: Set<HexKey>,
        cachedSignatures: MutableMap<Note, LevelSignature>,
        account: User,
        accountFollowingSet: Set<String>,
        now: Long,
    ): LevelSignature {
        val replyTo = replyTo
        if (
            event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()
        ) {
            return LevelSignature(
                signature = "/" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) + ";",
                createdAt = createdAt(),
                author = author,
            )
        }

        val parent =
            (
                replyTo
                    .filter {
                        it.idHex in eventsToConsider
                    } // This forces the signature to be based on a branch, avoiding two roots
                    .map {
                        cachedSignatures[it]
                            ?: it
                                .replyLevelSignature(
                                    eventsToConsider,
                                    cachedSignatures,
                                    account,
                                    accountFollowingSet,
                                    now,
                                )
                                .apply { cachedSignatures.put(it, this) }
                    }
                    .maxByOrNull { it.signature.length }
            )

        val parentSignature = parent?.signature?.removeSuffix(";") ?: ""

        val threadOrder =
            if (parent?.author == author && createdAt() != null) {
                // author of the thread first, in **ascending** order
                "9" +
                    formattedDateTime((parent?.createdAt ?: 0) + (now - (createdAt() ?: 0))) +
                    idHex.substring(0, 8)
            } else if (author?.pubkeyHex == account.pubkeyHex) {
                "8" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // my replies
            } else if (author?.pubkeyHex in accountFollowingSet) {
                "7" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // my follows replies.
            } else {
                "0" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // everyone else.
            }

        val mySignature =
            LevelSignature(
                signature = parentSignature + "/" + threadOrder + ";",
                createdAt = createdAt(),
                author = author,
            )

        cachedSignatures[this] = mySignature
        return mySignature
    }

    fun replyLevel(cachedLevels: MutableMap<Note, Int> = mutableMapOf()): Int {
        val replyTo = replyTo
        if (
            event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()
        ) {
            return 0
        }

        return replyTo.maxOf {
            cachedLevels[it] ?: it.replyLevel(cachedLevels).apply { cachedLevels.put(it, this) }
        } + 1
    }

    fun addReply(note: Note) {
        if (note !in replies) {
            replies = replies + note
            liveSet?.innerReplies?.invalidateData()
        }
    }

    fun removeReply(note: Note) {
        if (note in replies) {
            replies = replies - note
            liveSet?.innerReplies?.invalidateData()
        }
    }

    fun removeBoost(note: Note) {
        if (note in boosts) {
            boosts = boosts - note
            liveSet?.innerBoosts?.invalidateData()
        }
    }

    fun removeAllChildNotes(): List<Note> {
        val repliesChanged = replies.isNotEmpty()
        val reactionsChanged = reactions.isNotEmpty()
        val zapsChanged = zaps.isNotEmpty() || zapPayments.isNotEmpty()
        val boostsChanged = boosts.isNotEmpty()
        val reportsChanged = reports.isNotEmpty()

        val toBeRemoved =
            replies +
                reactions.values.flatten() +
                boosts +
                reports.values.flatten() +
                zaps.keys +
                zaps.values.filterNotNull() +
                zapPayments.keys +
                zapPayments.values.filterNotNull()

        replies = listOf<Note>()
        reactions = mapOf<String, List<Note>>()
        boosts = listOf<Note>()
        reports = mapOf<User, List<Note>>()
        zaps = mapOf<Note, Note?>()
        zapPayments = mapOf<Note, Note?>()
        zapsAmount = BigDecimal.ZERO
        relays = listOf<RelayBriefInfoCache.RelayBriefInfo>()
        lastReactionsDownloadTime = emptyMap()

        if (repliesChanged) liveSet?.innerReplies?.invalidateData()
        if (reactionsChanged) liveSet?.innerReactions?.invalidateData()
        if (boostsChanged) liveSet?.innerBoosts?.invalidateData()
        if (reportsChanged) liveSet?.innerReports?.invalidateData()
        if (zapsChanged) liveSet?.innerZaps?.invalidateData()

        return toBeRemoved
    }

    fun removeReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyArray()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        if (reactions[reaction]?.contains(note) == true) {
            reactions[reaction]?.let {
                if (note in it) {
                    val newList = it.minus(note)
                    if (newList.isEmpty()) {
                        reactions = reactions.minus(reaction)
                    } else {
                        reactions = reactions + Pair(reaction, newList)
                    }

                    liveSet?.innerReactions?.invalidateData()
                }
            }
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.innerReports?.invalidateData()
            }
        }
    }

    fun removeZap(note: Note) {
        if (zaps[note] != null) {
            zaps = zaps.minus(note)
            updateZapTotal()
            liveSet?.innerZaps?.invalidateData()
        } else if (zaps.containsValue(note)) {
            zaps = zaps.filterValues { it != note }
            updateZapTotal()
            liveSet?.innerZaps?.invalidateData()
        }
    }

    fun removeZapPayment(note: Note) {
        if (zapPayments.containsKey(note)) {
            zapPayments = zapPayments.minus(note)
            liveSet?.innerZaps?.invalidateData()
        } else if (zapPayments.containsValue(note)) {
            zapPayments = zapPayments.filterValues { it != note }
            liveSet?.innerZaps?.invalidateData()
        }
    }

    fun addBoost(note: Note) {
        if (note !in boosts) {
            boosts = boosts + note
            liveSet?.innerBoosts?.invalidateData()
        }
    }

    @Synchronized
    private fun innerAddZap(
        zapRequest: Note,
        zap: Note?,
    ): Boolean {
        if (zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            return true
        }

        return false
    }

    fun addZap(
        zapRequest: Note,
        zap: Note?,
    ) {
        checkNotInMainThread()

        if (zaps[zapRequest] == null) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted) {
                updateZapTotal()
                liveSet?.innerZaps?.invalidateData()
            }
        }
    }

    @Synchronized
    private fun innerAddZapPayment(
        zapPaymentRequest: Note,
        zapPayment: Note?,
    ): Boolean {
        if (zapPayments[zapPaymentRequest] == null) {
            zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
            return true
        }

        return false
    }

    fun addZapPayment(
        zapPaymentRequest: Note,
        zapPayment: Note?,
    ) {
        checkNotInMainThread()
        if (zapPayments[zapPaymentRequest] == null) {
            val inserted = innerAddZapPayment(zapPaymentRequest, zapPayment)
            if (inserted) {
                liveSet?.innerZaps?.invalidateData()
            }
        }
    }

    fun addReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyArray()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        val listOfAuthors = reactions[reaction]
        if (listOfAuthors == null) {
            reactions = reactions + Pair(reaction, listOf(note))
            liveSet?.innerReactions?.invalidateData()
        } else if (!listOfAuthors.contains(note)) {
            reactions = reactions + Pair(reaction, listOfAuthors + note)
            liveSet?.innerReactions?.invalidateData()
        }
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        val reportsByAuthor = reports[author]

        if (reportsByAuthor == null) {
            reports = reports + Pair(author, listOf(note))
            liveSet?.innerReports?.invalidateData()
        } else if (!reportsByAuthor.contains(note)) {
            reports = reports + Pair(author, reportsByAuthor + note)
            liveSet?.innerReports?.invalidateData()
        }
    }

    @Synchronized
    fun addRelaySync(briefInfo: RelayBriefInfoCache.RelayBriefInfo) {
        if (briefInfo !in relays) {
            relays = relays + briefInfo
        }
    }

    fun addRelay(relay: Relay) {
        if (relay.brief !in relays) {
            addRelaySync(relay.brief)
            liveSet?.innerRelays?.invalidateData()
        }
    }

    private fun recursiveIsPaidByCalculation(
        account: Account,
        remainingZapPayments: List<Pair<Note, Note?>>,
        onWasZappedByAuthor: () -> Unit,
    ) {
        if (remainingZapPayments.isEmpty()) {
            return
        }

        val next = remainingZapPayments.first()

        val zapResponseEvent = next.second?.event as? LnZapPaymentResponseEvent
        if (zapResponseEvent != null) {
            account.decryptZapPaymentResponseEvent(zapResponseEvent) { response ->
                if (
                    response is PayInvoiceSuccessResponse &&
                    account.isNIP47Author(zapResponseEvent.requestAuthor())
                ) {
                    onWasZappedByAuthor()
                } else {
                    recursiveIsPaidByCalculation(
                        account,
                        remainingZapPayments.minus(next),
                        onWasZappedByAuthor,
                    )
                }
            }
        }
    }

    private suspend fun isZappedByCalculation(
        option: Int?,
        user: User,
        account: Account,
        remainingZapEvents: Map<Note, Note?>,
        onWasZappedByAuthor: () -> Unit,
    ) {
        if (remainingZapEvents.isEmpty()) {
            return
        }

        remainingZapEvents.forEach { next ->
            val zapRequest = next.key.event as LnZapRequestEvent
            val zapEvent = next.value?.event as? LnZapEvent

            if (!zapRequest.isPrivateZap()) {
                // public events
                if (zapRequest.pubKey == user.pubkeyHex && (option == null || option == zapEvent?.zappedPollOption())) {
                    onWasZappedByAuthor()
                    return
                }
            } else {
                // private events

                // if has already decrypted
                val privateZap = zapRequest.cachedPrivateZap()
                if (privateZap != null) {
                    if (privateZap.pubKey == user.pubkeyHex && (option == null || option == zapEvent?.zappedPollOption())) {
                        onWasZappedByAuthor()
                        return
                    }
                } else {
                    if (account.isWriteable()) {
                        val result =
                            withTimeoutOrNull(1000) {
                                suspendCancellableCoroutine { continuation ->
                                    zapRequest.decryptPrivateZap(account.signer) {
                                        continuation.resume(it)
                                    }
                                }
                            }

                        if (result?.pubKey == user.pubkeyHex && (option == null || option == zapEvent?.zappedPollOption())) {
                            onWasZappedByAuthor()
                            return
                        }
                    }
                }
            }
        }
    }

    suspend fun isZappedBy(
        user: User,
        account: Account,
        onWasZappedByAuthor: () -> Unit,
    ) {
        isZappedByCalculation(null, user, account, zaps, onWasZappedByAuthor)
        if (account.userProfile() == user) {
            recursiveIsPaidByCalculation(account, zapPayments.toList(), onWasZappedByAuthor)
        }
    }

    suspend fun isZappedBy(
        option: Int?,
        user: User,
        account: Account,
        onWasZappedByAuthor: () -> Unit,
    ) {
        isZappedByCalculation(option, user, account, zaps, onWasZappedByAuthor)
    }

    fun getReactionBy(user: User): String? {
        return reactions.firstNotNullOfOrNull {
            if (it.value.any { it.author?.pubkeyHex == user.pubkeyHex }) {
                it.key
            } else {
                null
            }
        }
    }

    fun isBoostedBy(user: User): Boolean {
        return boosts.any { it.author?.pubkeyHex == user.pubkeyHex }
    }

    fun hasReportsBy(user: User): Boolean {
        return reports[user]?.isNotEmpty() ?: false
    }

    fun countReportAuthorsBy(users: Set<HexKey>): Int {
        return reports.count { it.key.pubkeyHex in users }
    }

    fun reportsBy(users: Set<HexKey>): List<Note> {
        return reports
            .mapNotNull {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    null
                }
            }
            .flatten()
    }

    private fun updateZapTotal() {
        var sumOfAmounts = BigDecimal.ZERO

        // Regular Zap Receipts
        zaps.forEach {
            val noteEvent = it.value?.event
            if (noteEvent is LnZapEvent) {
                sumOfAmounts += noteEvent.amount ?: BigDecimal.ZERO
            }
        }

        zapsAmount = sumOfAmounts
    }

    private fun recursiveZappedAmountCalculation(
        invoiceSet: LinkedHashSet<String>,
        remainingZapPayments: List<Pair<Note, Note?>>,
        signer: NostrSigner,
        output: BigDecimal,
        onReady: (BigDecimal) -> Unit,
    ) {
        if (remainingZapPayments.isEmpty()) {
            onReady(output)
            return
        }

        val next = remainingZapPayments.first()

        (next.second?.event as? LnZapPaymentResponseEvent)?.response(signer) { noteEvent ->
            if (noteEvent is PayInvoiceSuccessResponse) {
                (next.first.event as? LnZapPaymentRequestEvent)?.lnInvoice(signer) { invoice ->
                    val amount =
                        try {
                            LnInvoiceUtil.getAmountInSats(invoice)
                        } catch (e: java.lang.Exception) {
                            if (e is CancellationException) throw e
                            null
                        }

                    var newAmount = output

                    if (amount != null && !invoiceSet.contains(invoice)) {
                        invoiceSet.add(invoice)
                        newAmount += amount
                    }

                    recursiveZappedAmountCalculation(
                        invoiceSet,
                        remainingZapPayments.minus(next),
                        signer,
                        newAmount,
                        onReady,
                    )
                }
            }
        }
    }

    fun zappedAmountWithNWCPayments(
        signer: NostrSigner,
        onReady: (BigDecimal) -> Unit,
    ) {
        if (zapPayments.isEmpty()) {
            onReady(zapsAmount)
        }

        val invoiceSet = LinkedHashSet<String>(zaps.size + zapPayments.size)
        zaps.forEach { (it.value?.event as? LnZapEvent)?.lnInvoice()?.let { invoiceSet.add(it) } }

        recursiveZappedAmountCalculation(
            invoiceSet,
            zapPayments.toList(),
            signer,
            zapsAmount,
            onReady,
        )
    }

    fun hasPledgeBy(user: User): Boolean {
        return replies
            .filter { it.event?.isTaggedHash("bounty-added-reward") ?: false }
            .any {
                val pledgeValue =
                    try {
                        BigDecimal(it.event?.content())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                        // do nothing if it can't convert to bigdecimal
                    }

                pledgeValue != null && it.author == user
            }
    }

    fun pledgedAmountByOthers(): BigDecimal {
        return replies
            .filter { it.event?.isTaggedHash("bounty-added-reward") ?: false }
            .mapNotNull {
                try {
                    BigDecimal(it.event?.content())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                    // do nothing if it can't convert to bigdecimal
                }
            }
            .sumOf { it }
    }

    fun hasAnyReports(): Boolean {
        val dayAgo = TimeUtils.oneDayAgo()
        return reports.isNotEmpty() ||
            (
                author?.reports?.any { it.value.firstOrNull { (it.createdAt() ?: 0) > dayAgo } != null }
                    ?: false
            )
    }

    fun isNewThread(): Boolean {
        return (
            event is RepostEvent ||
                event is GenericRepostEvent ||
                replyTo == null ||
                replyTo?.size == 0
        ) &&
            event !is ChannelMessageEvent &&
            event !is LiveActivitiesChatMessageEvent
    }

    fun hasZapped(loggedIn: User): Boolean {
        return zaps.any { it.key.author == loggedIn }
    }

    fun hasReacted(
        loggedIn: User,
        content: String,
    ): Boolean {
        return reactedBy(loggedIn, content).isNotEmpty()
    }

    fun reactedBy(
        loggedIn: User,
        content: String,
    ): List<Note> {
        return reactions[content]?.filter { it.author == loggedIn } ?: emptyList()
    }

    fun reactedBy(loggedIn: User): List<String> {
        return reactions.filter { it.value.any { it.author == loggedIn } }.mapNotNull { it.key }
    }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        return boosts.firstOrNull {
            it.author == loggedIn && (it.createdAt() ?: 0) > TimeUtils.fiveMinutesAgo()
        } != null // 5 minute protection
    }

    fun boostedBy(loggedIn: User): List<Note> {
        return boosts.filter { it.author == loggedIn }
    }

    fun moveAllReferencesTo(note: AddressableNote) {
        // migrates these comments to a new version
        replies.forEach {
            note.addReply(it)
            it.replyTo = it.replyTo?.updated(this, note)
        }
        reactions.forEach {
            it.value.forEach {
                note.addReaction(it)
                it.replyTo = it.replyTo?.updated(this, note)
            }
        }
        boosts.forEach {
            note.addBoost(it)
            it.replyTo = it.replyTo?.updated(this, note)
        }
        reports.forEach {
            it.value.forEach {
                note.addReport(it)
                it.replyTo = it.replyTo?.updated(this, note)
            }
        }
        zaps.forEach {
            note.addZap(it.key, it.value)
            it.key.replyTo = it.key.replyTo?.updated(this, note)
            it.value?.replyTo = it.value?.replyTo?.updated(this, note)
        }

        replyTo = null
        replies = emptyList()
        reactions = emptyMap()
        boosts = emptyList()
        reports = emptyMap()
        zaps = emptyMap()
        zapsAmount = BigDecimal.ZERO
    }

    fun clearEOSE() {
        lastReactionsDownloadTime = emptyMap()
    }

    fun isHiddenFor(accountChoices: Account.LiveHiddenUsers): Boolean {
        val thisEvent = event ?: return false

        val isBoostedNoteHidden =
            if (
                thisEvent is GenericRepostEvent ||
                thisEvent is RepostEvent ||
                thisEvent is CommunityPostApprovalEvent
            ) {
                replyTo?.lastOrNull()?.isHiddenFor(accountChoices) ?: false
            } else {
                false
            }

        val isHiddenByWord =
            if (thisEvent is BaseTextNoteEvent) {
                accountChoices.hiddenWords.any {
                    thisEvent.content.containsAny(accountChoices.hiddenWordsCase)
                }
            } else {
                false
            }

        val isSensitive = thisEvent.isSensitive()
        return isBoostedNoteHidden ||
            isHiddenByWord ||
            accountChoices.hiddenUsers.contains(author?.pubkeyHex) ||
            accountChoices.spammers.contains(author?.pubkeyHex) ||
            (isSensitive && accountChoices.showSensitiveContent == false)
    }

    var liveSet: NoteLiveSet? = null
    var flowSet: NoteFlowSet? = null

    @Synchronized
    fun createOrDestroyLiveSync(create: Boolean) {
        if (create) {
            if (liveSet == null) {
                liveSet = NoteLiveSet(this)
            }
        } else {
            if (liveSet != null && liveSet?.isInUse() == false) {
                liveSet?.destroy()
                liveSet = null
            }
        }
    }

    fun live(): NoteLiveSet {
        if (liveSet == null) {
            createOrDestroyLiveSync(true)
        }
        return liveSet!!
    }

    fun clearLive() {
        if (liveSet != null && liveSet?.isInUse() == false) {
            createOrDestroyLiveSync(false)
        }
    }

    @Synchronized
    fun createOrDestroyFlowSync(create: Boolean) {
        if (create) {
            if (flowSet == null) {
                flowSet = NoteFlowSet(this)
            }
        } else {
            if (flowSet != null && flowSet?.isInUse() == false) {
                flowSet?.destroy()
                flowSet = null
            }
        }
    }

    fun flow(): NoteFlowSet {
        if (flowSet == null) {
            createOrDestroyFlowSync(true)
        }
        return flowSet!!
    }

    fun clearFlow() {
        if (flowSet != null && flowSet?.isInUse() == false) {
            createOrDestroyFlowSync(false)
        }
    }

    open fun wasOrShouldBeDeletedBy(
        deletionEvents: Set<HexKey>,
        deletionAddressables: Set<ATag>,
    ): Boolean {
        val thisEvent = event
        return deletionEvents.contains(idHex) || (thisEvent is AddressableEvent && deletionAddressables.contains(thisEvent.address()))
    }
}

@Stable
class NoteFlowSet(u: Note) {
    // Observers line up here.
    val metadata = NoteBundledRefresherFlow(u)

    fun isInUse(): Boolean {
        return metadata.stateFlow.subscriptionCount.value > 0
    }

    fun destroy() {
        metadata.destroy()
    }
}

@Stable
class NoteLiveSet(u: Note) {
    // Observers line up here.
    val innerMetadata = NoteBundledRefresherLiveData(u)
    val innerReactions = NoteBundledRefresherLiveData(u)
    val innerBoosts = NoteBundledRefresherLiveData(u)
    val innerReplies = NoteBundledRefresherLiveData(u)
    val innerReports = NoteBundledRefresherLiveData(u)
    val innerRelays = NoteBundledRefresherLiveData(u)
    val innerZaps = NoteBundledRefresherLiveData(u)
    val innerOts = NoteBundledRefresherLiveData(u)
    val innerModifications = NoteBundledRefresherLiveData(u)

    val metadata = innerMetadata.map { it }
    val reactions = innerReactions.map { it }
    val boosts = innerBoosts.map { it }
    val replies = innerReplies.map { it }
    val reports = innerReports.map { it }
    val relays = innerRelays.map { it }
    val zaps = innerZaps.map { it }

    val hasEvent = innerMetadata.map { it.note.event != null }.distinctUntilChanged()

    val hasReactions =
        innerZaps
            .combineWith(innerBoosts, innerReactions) { zapState, boostState, reactionState ->
                zapState?.note?.zaps?.isNotEmpty()
                    ?: false ||
                    boostState?.note?.boosts?.isNotEmpty() ?: false ||
                    reactionState?.note?.reactions?.isNotEmpty() ?: false
            }
            .distinctUntilChanged()

    val replyCount = innerReplies.map { it.note.replies.size }.distinctUntilChanged()

    val reactionCount =
        innerReactions
            .map {
                var total = 0
                it.note.reactions.forEach { total += it.value.size }
                total
            }
            .distinctUntilChanged()

    val boostCount = innerBoosts.map { it.note.boosts.size }.distinctUntilChanged()

    val relayInfo = innerRelays.map { it.note.relays }

    val content = innerMetadata.map { it.note.event?.content() ?: "" }

    fun isInUse(): Boolean {
        return metadata.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            reports.hasObservers() ||
            relays.hasObservers() ||
            zaps.hasObservers() ||
            hasEvent.hasObservers() ||
            hasReactions.hasObservers() ||
            replyCount.hasObservers() ||
            reactionCount.hasObservers() ||
            boostCount.hasObservers() ||
            innerOts.hasObservers() ||
            innerModifications.hasObservers()
    }

    fun destroy() {
        innerMetadata.destroy()
        innerReactions.destroy()
        innerBoosts.destroy()
        innerReplies.destroy()
        innerReports.destroy()
        innerRelays.destroy()
        innerZaps.destroy()
        innerOts.destroy()
        innerModifications.destroy()
    }
}

@Stable
class NoteBundledRefresherFlow(val note: Note) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)
    val stateFlow = MutableStateFlow(NoteState(note))

    fun destroy() {
        bundler.cancel()
    }

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate {
            checkNotInMainThread()

            stateFlow.emit(NoteState(note))
        }
    }
}

@Stable
class NoteBundledRefresherLiveData(val note: Note) : LiveData<NoteState>(NoteState(note)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    fun destroy() {
        bundler.cancel()
    }

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate {
            checkNotInMainThread()

            postValue(NoteState(note))
        }
    }

    fun <Y> map(transform: (NoteState) -> Y): NoteLoadingLiveData<Y> {
        val initialValue = this.value?.let { transform(it) }
        val result = NoteLoadingLiveData(note, initialValue)
        result.addSource(this) { x -> result.value = transform(x) }
        return result
    }
}

@Stable
class NoteLoadingLiveData<Y>(val note: Note, initialValue: Y?) : MediatorLiveData<Y>(initialValue) {
    override fun onActive() {
        super.onActive()
        if (note is AddressableNote) {
            NostrSingleEventDataSource.addAddress(note)
        } else {
            NostrSingleEventDataSource.add(note)
        }
    }

    override fun onInactive() {
        super.onInactive()
        if (note is AddressableNote) {
            NostrSingleEventDataSource.removeAddress(note)
        } else {
            NostrSingleEventDataSource.remove(note)
        }
    }
}

@Immutable class NoteState(val note: Note)

object RelayBriefInfoCache {
    val cache = LruCache<String, RelayBriefInfo?>(50)

    @Immutable
    data class RelayBriefInfo(
        val url: String,
        val displayUrl: String =
            url.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/").intern(),
        val favIcon: String = "https://$displayUrl/favicon.ico".intern(),
    )

    fun get(url: String): RelayBriefInfo {
        val info = cache[url]
        if (info != null) return info

        val newInfo = RelayBriefInfo(url)
        cache.put(url, newInfo)
        return newInfo
    }
}
