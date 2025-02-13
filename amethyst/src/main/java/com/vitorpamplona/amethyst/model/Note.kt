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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import com.vitorpamplona.amethyst.launchAndWaitAll
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.amethyst.ui.actions.relays.updated
import com.vitorpamplona.amethyst.ui.note.combineWith
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.quartz.experimental.bounties.addedRewardValue
import com.vitorpamplona.quartz.experimental.bounties.hasAdditionalReward
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.EventReference
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.anyHashTag
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.toNAddr
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@Stable
class AddressableNote(
    val address: ATag,
) : Note(address.toTag()) {
    override fun idNote() = address.toNAddr(relayHintUrl())

    override fun toNEvent() = address.toNAddr(relayHintUrl())

    override fun idDisplayNote() = idNote().toShortenHex()

    override fun address() = address

    override fun createdAt(): Long? {
        if (event == null) return null

        val publishedAt = (event as? LongTextNoteEvent)?.publishedAt() ?: Long.MAX_VALUE
        val lastCreatedAt = event?.createdAt ?: Long.MAX_VALUE

        return minOf(publishedAt, lastCreatedAt)
    }

    fun dTag(): String = address.dTag

    override fun wasOrShouldBeDeletedBy(
        deletionEvents: Set<HexKey>,
        deletionAddressables: Set<ATag>,
    ): Boolean {
        val thisEvent = event
        return deletionAddressables.contains(address) || (thisEvent != null && deletionEvents.contains(thisEvent.id))
    }

    override fun toATag() = ATag.parse(idHex, relayHintUrl())
}

@Stable
open class Note(
    val idHex: String,
) {
    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var event: Event? = null
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

    open fun idNote() = toNEvent()

    open fun toNEvent(): String {
        val myEvent = event
        return if (myEvent is WrappedEvent) {
            val host = myEvent.host
            if (host != null) {
                NEvent.create(
                    host.id,
                    host.pubKey,
                    host.kind,
                    relayHintUrl(),
                )
            } else {
                NEvent.create(idHex, author?.pubkeyHex, event?.kind, relayHintUrl())
            }
        } else {
            NEvent.create(idHex, author?.pubkeyHex, event?.kind, relayHintUrl())
        }
    }

    fun relayHintUrl(): String? {
        val authorRelay = author?.latestMetadataRelay

        return if (relays.isNotEmpty()) {
            if (authorRelay != null && relays.any { it.url == authorRelay }) {
                authorRelay
            } else {
                relays.firstOrNull()?.url
            }
        } else {
            null
        }
    }

    fun toNostrUri(): String = "nostr:${toNEvent()}"

    open fun idDisplayNote() = idNote().toShortenHex()

    fun channelHex(): HexKey? =
        if (
            event is ChannelMessageEvent ||
            event is ChannelMetadataEvent ||
            event is ChannelCreateEvent ||
            event is LiveActivitiesChatMessageEvent ||
            event is LiveActivitiesEvent
        ) {
            (event as? ChannelMessageEvent)?.channelId()
                ?: (event as? ChannelMetadataEvent)?.channelId()
                ?: (event as? ChannelCreateEvent)?.id
                ?: (event as? LiveActivitiesChatMessageEvent)?.activity()?.toTag()
                ?: (event as? LiveActivitiesEvent)?.aTag()?.toTag()
        } else {
            null
        }

    open fun address(): ATag? = null

    open fun createdAt() = event?.createdAt

    fun isDraft() = event is DraftEvent

    fun loadEvent(
        event: Event,
        author: User,
        replyTo: List<Note>,
    ) {
        if (this.event?.id != event.id) {
            this.event = event
            this.author = author
            this.replyTo = replyTo

            liveSet?.innerMetadata?.invalidateData()
            flowSet?.metadata?.invalidateData()
        }
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
        if (reportsChanged) {
            flowSet?.reports?.invalidateData()
        }
        if (zapsChanged) liveSet?.innerZaps?.invalidateData()

        return toBeRemoved
    }

    fun removeReaction(note: Note) {
        val tags = note.event?.tags ?: emptyArray()
        val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

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
                flowSet?.reports?.invalidateData()
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
        val tags = note.event?.tags ?: emptyArray()
        val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

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
            flowSet?.reports?.invalidateData()
        } else if (!reportsByAuthor.contains(note)) {
            reports = reports + Pair(author, reportsByAuthor + note)
            flowSet?.reports?.invalidateData()
        }
    }

    @Synchronized
    fun addRelaySync(briefInfo: RelayBriefInfoCache.RelayBriefInfo) {
        if (briefInfo !in relays) {
            relays = relays + briefInfo
        }
    }

    fun hasRelay(relay: Relay) = relay.brief !in relays

    fun addRelay(relay: Relay) {
        if (relay.brief !in relays) {
            addRelaySync(relay.brief)
            flowSet?.relays?.invalidateData()
        }
    }

    fun addRelayBrief(brief: RelayBriefInfoCache.RelayBriefInfo) {
        if (brief !in relays) {
            addRelaySync(brief)
            flowSet?.relays?.invalidateData()
        }
    }

    private suspend fun isPaidByCalculation(
        account: Account,
        zapEvents: List<Pair<Note, Note?>>,
        onWasZappedByAuthor: () -> Unit,
    ) {
        if (zapEvents.isEmpty()) {
            return
        }

        var hasSentOne = false

        launchAndWaitAll(zapEvents) { next ->
            val zapResponseEvent = next.second?.event as? LnZapPaymentResponseEvent

            if (zapResponseEvent != null) {
                val result =
                    tryAndWait { continuation ->
                        account.decryptZapPaymentResponseEvent(zapResponseEvent) { response ->
                            if (
                                response is PayInvoiceSuccessResponse &&
                                account.isNIP47Author(zapResponseEvent.requestAuthor())
                            ) {
                                continuation.resume(true)
                            }
                        }
                    }

                if (!hasSentOne && result == true) {
                    hasSentOne = true
                    onWasZappedByAuthor()
                }
            }
        }
    }

    private suspend fun isZappedByCalculation(
        option: Int?,
        user: User,
        account: Account,
        zapEvents: Map<Note, Note?>,
        onWasZappedByAuthor: () -> Unit,
    ) {
        if (zapEvents.isEmpty()) {
            return
        }

        zapEvents.forEach { next ->
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
                            tryAndWait { continuation ->
                                zapRequest.decryptPrivateZap(account.signer) {
                                    continuation.resume(it)
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
            isPaidByCalculation(account, zapPayments.toList(), onWasZappedByAuthor)
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

    fun getReactionBy(user: User): String? =
        reactions.firstNotNullOfOrNull {
            if (it.value.any { it.author?.pubkeyHex == user.pubkeyHex }) {
                it.key
            } else {
                null
            }
        }

    fun isBoostedBy(user: User): Boolean = boosts.any { it.author?.pubkeyHex == user.pubkeyHex }

    fun hasReportsBy(user: User): Boolean = reports[user]?.isNotEmpty() ?: false

    fun countReportAuthorsBy(users: Set<HexKey>): Int = reports.count { it.key.pubkeyHex in users }

    fun reportsBy(users: Set<HexKey>): List<Note> =
        reports
            .mapNotNull {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    null
                }
            }.flatten()

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

    private suspend fun zappedAmountCalculation(
        startAmount: BigDecimal,
        paidInvoiceSet: LinkedHashSet<String>,
        zapPayments: List<Pair<Note, Note?>>,
        signer: NostrSigner,
        onReady: (BigDecimal) -> Unit,
    ) {
        if (zapPayments.isEmpty()) {
            onReady(startAmount)
            return
        }

        var output: BigDecimal = startAmount

        launchAndWaitAll(zapPayments) { next ->
            val result =
                tryAndWait { continuation ->
                    processZapAmountFromResponse(
                        next.first,
                        next.second,
                        continuation,
                        signer,
                    )
                }

            if (result != null && !paidInvoiceSet.contains(result.invoice)) {
                paidInvoiceSet.add(result.invoice)
                output = output.add(result.amount)
            }
        }

        onReady(output)
    }

    private fun processZapAmountFromResponse(
        paymentRequest: Note,
        paymentResponse: Note?,
        continuation: Continuation<InvoiceAmount?>,
        signer: NostrSigner,
    ) {
        val nwcRequest = paymentRequest.event as? LnZapPaymentRequestEvent
        val nwcResponse = paymentResponse?.event as? LnZapPaymentResponseEvent

        if (nwcRequest != null && nwcResponse != null) {
            processZapAmountFromResponse(
                nwcRequest,
                nwcResponse,
                continuation,
                signer,
            )
        } else {
            continuation.resume(null)
        }
    }

    class InvoiceAmount(
        val invoice: String,
        val amount: BigDecimal,
    )

    private fun processZapAmountFromResponse(
        nwcRequest: LnZapPaymentRequestEvent,
        nwcResponse: LnZapPaymentResponseEvent,
        continuation: Continuation<InvoiceAmount?>,
        signer: NostrSigner,
    ) {
        // if we can decrypt the reply
        nwcResponse.response(signer) { noteEvent ->
            // if it is a sucess
            if (noteEvent is PayInvoiceSuccessResponse) {
                // if we can decrypt the invoice
                nwcRequest.lnInvoice(signer) { invoice ->
                    // if we can parse the amount
                    val amount =
                        try {
                            LnInvoiceUtil.getAmountInSats(invoice)
                        } catch (e: java.lang.Exception) {
                            if (e is CancellationException) throw e
                            null
                        }

                    // avoid double counting
                    if (amount != null) {
                        continuation.resume(InvoiceAmount(invoice, amount))
                    } else {
                        continuation.resume(null)
                    }
                }
            } else {
                continuation.resume(null)
            }
        }
    }

    suspend fun zappedAmountWithNWCPayments(
        signer: NostrSigner,
        onReady: (BigDecimal) -> Unit,
    ) {
        if (zapPayments.isEmpty()) {
            onReady(zapsAmount)
        }

        val invoiceSet = LinkedHashSet<String>(zaps.size + zapPayments.size)
        zaps.forEach { (it.value?.event as? LnZapEvent)?.lnInvoice()?.let { invoiceSet.add(it) } }

        zappedAmountCalculation(
            zapsAmount,
            invoiceSet,
            zapPayments.toList(),
            signer,
            onReady,
        )
    }

    fun hasPledgeBy(user: User): Boolean =
        replies
            .filter { it.event?.hasAdditionalReward() ?: false }
            .any {
                val pledgeValue =
                    try {
                        BigDecimal(it.event?.content)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                        // do nothing if it can't convert to bigdecimal
                    }

                pledgeValue != null && it.author == user
            }

    fun pledgedAmountByOthers(): BigDecimal = replies.sumOf { it.event?.addedRewardValue() ?: BigDecimal.ZERO }

    fun hasAnyReports(): Boolean {
        val dayAgo = TimeUtils.oneDayAgo()
        return reports.isNotEmpty() ||
            (
                author?.reports?.any { it.value.firstOrNull { (it.createdAt() ?: 0) > dayAgo } != null }
                    ?: false
            )
    }

    fun isNewThread(): Boolean =
        (
            event is RepostEvent ||
                event is GenericRepostEvent ||
                replyTo == null ||
                replyTo?.size == 0
        ) &&
            event !is ChannelMessageEvent &&
            event !is LiveActivitiesChatMessageEvent

    fun hasZapped(loggedIn: User): Boolean = zaps.any { it.key.author == loggedIn }

    fun hasReacted(
        loggedIn: User,
        content: String,
    ): Boolean = reactedBy(loggedIn, content).isNotEmpty()

    fun reactedBy(
        loggedIn: User,
        content: String,
    ): List<Note> = reactions[content]?.filter { it.author == loggedIn } ?: emptyList()

    fun reactedBy(loggedIn: User): List<String> = reactions.filter { it.value.any { it.author == loggedIn } }.mapNotNull { it.key }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        return boosts.firstOrNull {
            it.author == loggedIn && (it.createdAt() ?: 0) > TimeUtils.fiveMinutesAgo()
        } != null // 5 minute protection
    }

    fun boostedBy(loggedIn: User): List<Note> = boosts.filter { it.author == loggedIn }

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
        val hash = thisEvent.pubKey.hashCode()

        // if the author is hidden by spam or blocked
        if (accountChoices.hiddenUsersHashCodes.contains(hash) ||
            accountChoices.spammersHashCodes.contains(hash)
        ) {
            return true
        }

        // if the post is sensitive and the user doesn't want to see sensitive content
        if (accountChoices.showSensitiveContent == false && thisEvent.isSensitiveOrNSFW()) {
            return true
        }

        // if this is a repost, consider the inner event.
        if (
            thisEvent is GenericRepostEvent ||
            thisEvent is RepostEvent ||
            thisEvent is CommunityPostApprovalEvent
        ) {
            if (replyTo?.lastOrNull()?.isHiddenFor(accountChoices) == true) {
                return true
            }
        }

        if (accountChoices.hiddenWordsCase.isNotEmpty()) {
            if (thisEvent is BaseThreadedEvent && thisEvent.content.containsAny(accountChoices.hiddenWordsCase)) {
                return true
            }

            if (thisEvent.anyHashTag { it.containsAny(accountChoices.hiddenWordsCase) }) {
                return true
            }

            if (author?.containsAny(accountChoices.hiddenWordsCase) == true) return true
        }

        return false
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
        return deletionEvents.contains(idHex) || (thisEvent is AddressableEvent && deletionAddressables.contains(thisEvent.aTag()))
    }

    fun toETag(): ETag {
        val noteEvent = event
        return if (noteEvent != null) {
            ETag(noteEvent.id, relayHintUrl(), noteEvent.pubKey)
        } else {
            ETag(idHex, relayHintUrl(), author?.pubkeyHex)
        }
    }

    fun toEId(): EventReference {
        val noteEvent = event
        return if (noteEvent != null) {
            // uses the confirmed event id if available
            EventReference(noteEvent.id, noteEvent.pubKey, relayHintUrl())
        } else {
            EventReference(idHex, author?.pubkeyHex, relayHintUrl())
        }
    }

    fun <T : Event> toEventHint() = (event as? T)?.let { EventHintBundle(it, relayHintUrl(), author?.bestRelayHint()) }

    fun toMarkedETag(marker: MarkedETag.MARKER): MarkedETag {
        val noteEvent = event
        return if (noteEvent != null) {
            MarkedETag(noteEvent.id, relayHintUrl(), marker, noteEvent.pubKey)
        } else {
            MarkedETag(idHex, relayHintUrl(), marker, author?.pubkeyHex)
        }
    }

    open fun toATag(): ATag? {
        val noteEvent = event
        return if (noteEvent is AddressableEvent) {
            ATag(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag(), relayHintUrl())
        } else {
            null
        }
    }
}

@Stable
class NoteFlowSet(
    u: Note,
) {
    // Observers line up here.
    val metadata = NoteBundledRefresherFlow(u)
    val reports = NoteBundledRefresherFlow(u)
    val relays = NoteBundledRefresherFlow(u)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun author() =
        metadata.stateFlow.flatMapLatest {
            it.note.author
                ?.flow()
                ?.metadata
                ?.stateFlow ?: MutableStateFlow(null)
        }

    fun isInUse(): Boolean =
        metadata.stateFlow.subscriptionCount.value > 0 ||
            reports.stateFlow.subscriptionCount.value > 0 ||
            relays.stateFlow.subscriptionCount.value > 0

    fun destroy() {
        metadata.destroy()
        reports.destroy()
        relays.destroy()
    }
}

@Stable
class NoteLiveSet(
    u: Note,
) {
    // Observers line up here.
    val innerMetadata = NoteBundledRefresherLiveData(u)
    val innerReactions = NoteBundledRefresherLiveData(u)
    val innerBoosts = NoteBundledRefresherLiveData(u)
    val innerReplies = NoteBundledRefresherLiveData(u)
    val innerZaps = NoteBundledRefresherLiveData(u)
    val innerOts = NoteBundledRefresherLiveData(u)
    val innerModifications = NoteBundledRefresherLiveData(u)

    val metadata = innerMetadata.map { it }
    val reactions = innerReactions.map { it }
    val boosts = innerBoosts.map { it }
    val replies = innerReplies.map { it }
    val zaps = innerZaps.map { it }

    val hasEvent = innerMetadata.map { it.note.event != null }.distinctUntilChanged()

    val hasReactions =
        innerZaps
            .combineWith(innerBoosts, innerReactions) { zapState, boostState, reactionState ->
                zapState?.note?.zaps?.isNotEmpty()
                    ?: false ||
                    boostState?.note?.boosts?.isNotEmpty() ?: false ||
                    reactionState?.note?.reactions?.isNotEmpty() ?: false
            }.distinctUntilChanged()

    val replyCount = innerReplies.map { it.note.replies.size }.distinctUntilChanged()

    val reactionCount =
        innerReactions
            .map {
                var total = 0
                it.note.reactions.forEach { total += it.value.size }
                total
            }.distinctUntilChanged()

    val boostCount = innerBoosts.map { it.note.boosts.size }.distinctUntilChanged()

    val content = innerMetadata.map { it.note.event?.content ?: "" }

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            zaps.hasObservers() ||
            hasEvent.hasObservers() ||
            hasReactions.hasObservers() ||
            replyCount.hasObservers() ||
            reactionCount.hasObservers() ||
            boostCount.hasObservers() ||
            innerOts.hasObservers() ||
            innerModifications.hasObservers()

    fun destroy() {
        innerMetadata.destroy()
        innerReactions.destroy()
        innerBoosts.destroy()
        innerReplies.destroy()
        innerZaps.destroy()
        innerOts.destroy()
        innerModifications.destroy()
    }
}

@Stable
class NoteBundledRefresherFlow(
    val note: Note,
) {
    // Refreshes observers in batches.
    // TODO: Replace the bundler for .sample
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
class NoteBundledRefresherLiveData(
    val note: Note,
) : LiveData<NoteState>(NoteState(note)) {
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
class NoteLoadingLiveData<Y>(
    val note: Note,
    initialValue: Y?,
) : MediatorLiveData<Y>(initialValue) {
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

@Immutable class NoteState(
    val note: Note,
)
