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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.amethyst.commons.threading.checkNotInMainThread
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.quartz.experimental.bounties.addedRewardValue
import com.vitorpamplona.quartz.experimental.bounties.hasAdditionalReward
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.anyHashTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.anyAsync
import com.vitorpamplona.quartz.utils.containsAny
import com.vitorpamplona.quartz.utils.launchAndWaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.math.BigDecimal

interface NotesGatherer {
    fun removeNote(note: Note)
}

@Stable
class AddressableNote(
    val address: Address,
) : Note(address.toValue()) {
    override fun idNote() = toNAddr()

    override fun toNEvent() = toNAddr()

    override fun idDisplayNote() = idNote().toShortDisplay()

    override fun address() = address

    override fun createdAt(): Long? {
        val currentEvent = event
        if (currentEvent == null) return null
        if (currentEvent is PublishedAtProvider) return currentEvent.publishedAt() ?: currentEvent.createdAt
        return currentEvent.createdAt
    }

    fun dTag(): String = address.dTag

    fun toNAddr() = NAddress.create(address.kind, address.pubKeyHex, address.dTag, relayHintUrl())
}

@Stable
open class Note(
    val idHex: HexKey,
) : NotesGatherer {
    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var event: Event? = null
    var author: User? = null
    var replyTo: List<Note>? = null

    var inGatherers: List<NotesGatherer>? = null

    fun inGatherers() = inGatherers ?: listOf<NotesGatherer>().also { inGatherers = it }

    fun addGatherer(gatherer: NotesGatherer) {
        inGatherers = inGatherers() + gatherer
    }

    fun removeGatherer(gatherer: NotesGatherer) {
        inGatherers = inGatherers() - gatherer
    }

    override fun removeNote(note: Note) {
        removeReply(note)
        removeBoost(note)
        removeReaction(note)
        removeZap(note)
        removeZapPayment(note)
        removeReport(note)
    }

    var poll: PollResponsesCache? = null

    fun pollStateOrNull(): PollResponsesCache? = poll

    fun pollState(): PollResponsesCache = poll ?: PollResponsesCache().also { poll = it }

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

    var relays = listOf<NormalizedRelayUrl>()
        private set

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

    fun relayUrls(): List<NormalizedRelayUrl> {
        val authorRelay = author?.relayHints() ?: emptyList()

        return authorRelay + relays
    }

    fun relayUrlsForReactions(): List<NormalizedRelayUrl> {
        val authorRelay = author?.inboxRelays() ?: emptyList()

        return authorRelay + relays
    }

    fun relayHintUrl(): NormalizedRelayUrl? {
        // checks Community Events first
        when (val noteEvent = event) {
            is CommunityDefinitionEvent -> {
                noteEvent.relayUrls().firstOrNull()?.let { return it }
            }

            is IsInPublicChatChannel -> {
                inGatherers?.forEach {
                    if (it is com.vitorpamplona.amethyst.commons.model.Channel) {
                        it.relays().firstOrNull()?.let { return it }
                    }
                }
            }

            is LiveActivitiesEvent -> {
                noteEvent.relays().ifEmpty { null }?.toSet()
            }

            is LiveActivitiesChatMessageEvent -> {
                inGatherers?.forEach {
                    if (it is com.vitorpamplona.amethyst.commons.model.Channel) {
                        it.relays().firstOrNull()?.let { return it }
                    }
                }
            }

            is EphemeralChatEvent -> {
                noteEvent.roomId()?.let { return it.relayUrl }
            }
        }

        val currentOutbox = author?.outboxRelays()?.toSet()

        return if (relays.isNotEmpty()) {
            if (currentOutbox != null && currentOutbox.isNotEmpty()) {
                val relayMatchesOutbox = relays.firstOrNull { it in currentOutbox }
                if (relayMatchesOutbox != null) {
                    return relayMatchesOutbox
                }
            }

            return relays.firstOrNull()
        } else {
            currentOutbox?.firstOrNull() ?: author?.mostUsedNonLocalRelay()
        }
    }

    fun toNostrUri(): String = "nostr:${toNEvent()}"

    open fun idDisplayNote() = idNote().toShortDisplay()

    open fun address(): Address? = null

    open fun createdAt() = event?.createdAt

    fun isDraft() = event is DraftWrapEvent

    fun loadEvent(
        event: Event,
        author: User,
        replyTo: List<Note>,
    ) {
        if (this.event?.id != event.id) {
            this.event = event
            this.author = author
            this.replyTo = replyTo

            flowSet?.metadata?.invalidateData()
        }
    }

    fun hasZapsBoostsOrReactions(): Boolean = reactions.isNotEmpty() || zaps.isNotEmpty() || boosts.isNotEmpty()

    fun countReactions(): Int {
        var total = 0
        reactions.forEach { total += it.value.size }
        return total
    }

    fun addReply(note: Note) {
        if (note !in replies) {
            replies = replies + note
            flowSet?.replies?.invalidateData()
        }
    }

    fun removeReply(note: Note) {
        if (note in replies) {
            replies = replies - note
            flowSet?.replies?.invalidateData()
        }
    }

    fun removeBoost(note: Note) {
        if (note in boosts) {
            boosts = boosts - note
            flowSet?.boosts?.invalidateData()
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
        relays = listOf<NormalizedRelayUrl>()

        if (repliesChanged) flowSet?.replies?.invalidateData()
        if (reactionsChanged) flowSet?.reactions?.invalidateData()
        if (boostsChanged) flowSet?.boosts?.invalidateData()
        if (reportsChanged) flowSet?.reports?.invalidateData()
        if (zapsChanged) flowSet?.zaps?.invalidateData()

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

                    flowSet?.reactions?.invalidateData()
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
            flowSet?.zaps?.invalidateData()
        } else if (zaps.containsValue(note)) {
            zaps = zaps.filterValues { it != note }
            updateZapTotal()
            flowSet?.zaps?.invalidateData()
        }
    }

    fun removeZapPayment(note: Note) {
        if (zapPayments.containsKey(note)) {
            zapPayments = zapPayments.minus(note)
            flowSet?.zaps?.invalidateData()
        } else if (zapPayments.containsValue(note)) {
            zapPayments = zapPayments.filterValues { it != note }
            flowSet?.zaps?.invalidateData()
        }
    }

    fun addBoost(note: Note) {
        if (note !in boosts) {
            boosts = boosts + note
            flowSet?.boosts?.invalidateData()
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
        if (zaps[zapRequest] == null) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted && zap != null) {
                updateZapTotal()
                flowSet?.zaps?.invalidateData()
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
                flowSet?.zaps?.invalidateData()
            }
        }
    }

    fun addReaction(note: Note) {
        val tags = note.event?.tags ?: emptyArray()
        val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        val listOfAuthors = reactions[reaction]
        if (listOfAuthors == null) {
            reactions = reactions + Pair(reaction, listOf(note))
            flowSet?.reactions?.invalidateData()
        } else if (!listOfAuthors.contains(note)) {
            reactions = reactions + Pair(reaction, listOfAuthors + note)
            flowSet?.reactions?.invalidateData()
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
    fun addRelaySync(relay: NormalizedRelayUrl) {
        if (relay !in relays) {
            relays = relays + relay
        }
    }

    fun hasRelay(relay: NormalizedRelayUrl) = relay in relays

    fun addRelay(relay: NormalizedRelayUrl) {
        if (relay !in relays) {
            addRelaySync(relay)
            flowSet?.relays?.invalidateData()
        }
    }

    private suspend fun isPaidByCalculation(
        zapPayments: List<Pair<Note, Note?>>,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean {
        if (zapPayments.isEmpty()) {
            return false
        }

        return anyAsync(zapPayments) { next ->
            val zapResponseEvent = next.second?.event as? LnZapPaymentResponseEvent

            if (zapResponseEvent != null) {
                val response = account.nip47SignerState.decryptResponse(zapResponseEvent)
                if (response != null) {
                    response is PayInvoiceSuccessResponse &&
                        account.nip47SignerState.isNIP47Author(zapResponseEvent.requestAuthor()) &&
                        zapResponseEvent.createdAt > afterTimeInSeconds
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private suspend fun isZappedByCalculation(
        option: Int?,
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
        zapEvents: Map<Note, Note?>,
    ): Boolean {
        if (zapEvents.isEmpty()) {
            return false
        }

        val parallelDecrypt = mutableListOf<Pair<LnZapRequestEvent, LnZapEvent>>()

        zapEvents.forEach { next ->
            val zapRequest = next.key.event as LnZapRequestEvent
            val zapEvent = next.value?.event as? LnZapEvent

            if (zapEvent != null) {
                if (!zapRequest.isPrivateZap()) {
                    // public events
                    if (zapRequest.pubKey == user.pubkeyHex &&
                        zapEvent.createdAt > afterTimeInSeconds &&
                        (option == null || option == zapEvent.zappedPollOption())
                    ) {
                        return true
                    }
                } else {
                    // private events

                    // if has already decrypted
                    val privateZap = account.privateZapsDecryptionCache.cachedPrivateZap(zapRequest)
                    if (privateZap != null) {
                        if (privateZap.pubKey == user.pubkeyHex &&
                            zapEvent.createdAt > afterTimeInSeconds &&
                            (option == null || option == zapEvent.zappedPollOption())
                        ) {
                            return true
                        }
                    } else {
                        if (account.isWriteable()) {
                            parallelDecrypt.add(Pair(zapRequest, zapEvent))
                        }
                    }
                }
            }
        }

        if (parallelDecrypt.isEmpty()) {
            return false
        }

        return anyAsync(parallelDecrypt) { pair ->
            val result = account.privateZapsDecryptionCache.decryptPrivateZap(pair.first)
            result?.pubKey == user.pubkeyHex &&
                pair.second.createdAt > afterTimeInSeconds &&
                (option == null || option == pair.second.zappedPollOption())
        }
    }

    suspend fun isZappedBy(
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean {
        val first = isZappedByCalculation(null, user, afterTimeInSeconds, account, zaps)
        if (first) return true
        if (account.userProfile() == user) {
            return isPaidByCalculation(zapPayments.toList(), afterTimeInSeconds, account)
        }
        return false
    }

    suspend fun isZappedBy(
        option: Int?,
        user: User,
        afterTimeInSeconds: Long,
        account: IAccount,
    ): Boolean = isZappedByCalculation(option, user, afterTimeInSeconds, account, zaps)

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
        signerState: INwcSignerState,
    ): BigDecimal {
        if (zapPayments.isEmpty()) {
            return startAmount
        }

        var output: BigDecimal = startAmount

        launchAndWaitAll(zapPayments) { next ->
            val result =
                processZapAmountFromResponse(
                    next.first,
                    next.second,
                    signerState,
                )

            if (result != null && !paidInvoiceSet.contains(result.invoice)) {
                paidInvoiceSet.add(result.invoice)
                output = output.add(result.amount)
            }
        }

        return output
    }

    private suspend fun processZapAmountFromResponse(
        paymentRequest: Note,
        paymentResponse: Note?,
        signerState: INwcSignerState,
    ): InvoiceAmount? {
        val nwcRequest = paymentRequest.event as? LnZapPaymentRequestEvent
        val nwcResponse = paymentResponse?.event as? LnZapPaymentResponseEvent

        return if (nwcRequest != null && nwcResponse != null) {
            processZapAmountFromResponse(
                nwcRequest,
                nwcResponse,
                signerState,
            )
        } else {
            null
        }
    }

    class InvoiceAmount(
        val invoice: String,
        val amount: BigDecimal,
    )

    private suspend fun processZapAmountFromResponse(
        nwcRequest: LnZapPaymentRequestEvent,
        nwcResponse: LnZapPaymentResponseEvent,
        signerState: INwcSignerState,
    ): InvoiceAmount? {
        // if we can decrypt the reply
        return signerState.decryptResponse(nwcResponse)?.let { noteEvent ->
            // if it is a sucess
            if (noteEvent is PayInvoiceSuccessResponse) {
                // if we can decrypt the invoice
                val request = signerState.decryptRequest(nwcRequest)
                val invoice = (request as? PayInvoiceMethod)?.params?.invoice
                if (invoice != null) {
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
                        InvoiceAmount(invoice, amount)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun zappedAmountWithNWCPayments(signerState: INwcSignerState): BigDecimal {
        if (zapPayments.isEmpty()) {
            return zapsAmount
        }

        val invoiceSet = LinkedHashSet<String>(zaps.size + zapPayments.size)
        zaps.forEach { (it.value?.event as? LnZapEvent)?.lnInvoice()?.let { invoiceSet.add(it) } }

        return zappedAmountCalculation(
            zapsAmount,
            invoiceSet,
            zapPayments.toList(),
            signerState,
        )
    }

    fun hasReport(
        loggedIn: User,
        type: ReportType,
    ): Boolean =
        reports[loggedIn]?.firstOrNull {
            it.event is ReportEvent &&
                (it.event as ReportEvent).reportedAuthor().any { it.type == type }
        } != null

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

        if (reports.isNotEmpty()) return true

        return author?.reportsOrNull()?.hasReportNewerThan(dayAgo) ?: false
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
    ): Boolean = allReactionsOfContentByAuthor(loggedIn, content).isNotEmpty()

    fun allReactionsOfContentByAuthor(
        loggedIn: User,
        content: String,
    ): List<Note> = reactions[content]?.filter { it.author == loggedIn } ?: emptyList()

    fun allReactionsByAuthor(loggedIn: User): List<String> = reactions.filter { it.value.any { it.author == loggedIn } }.mapNotNull { it.key }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        val fiveMinsAgo = TimeUtils.fiveMinutesAgo()
        return boosts.any {
            it.author == loggedIn && (it.createdAt() ?: 0L) > fiveMinsAgo
        }
    }

    fun hasBoostedInTheLast5Minutes(loggedIn: HexKey): Boolean {
        val fiveMinsAgo = TimeUtils.fiveMinutesAgo()
        return boosts.any {
            (it.createdAt() ?: 0L) > fiveMinsAgo && it.author?.pubkeyHex == loggedIn
        }
    }

    fun boostedBy(loggedIn: User): List<Note> = boosts.filter { it.author == loggedIn }

    fun moveAllReferencesTo(note: AddressableNote) {
        // migrates these comments to a new version
        replies.forEach {
            note.addReply(it)
            it.replyTo = it.replyTo?.replace(this, note)
        }
        reactions.forEach {
            it.value.forEach {
                note.addReaction(it)
                it.replyTo = it.replyTo?.replace(this, note)
            }
        }
        boosts.forEach {
            note.addBoost(it)
            it.replyTo = it.replyTo?.replace(this, note)
        }
        reports.forEach {
            it.value.forEach {
                note.addReport(it)
                it.replyTo = it.replyTo?.replace(this, note)
            }
        }
        zaps.forEach {
            note.addZap(it.key, it.value)
            it.key.replyTo = it.key.replyTo?.replace(this, note)
            it.value?.replyTo = it.value?.replyTo?.replace(this, note)
        }

        replyTo = null
        replies = emptyList()
        reactions = emptyMap()
        boosts = emptyList()
        reports = emptyMap()
        zaps = emptyMap()
        zapsAmount = BigDecimal.ZERO
    }

    fun isHiddenFor(accountChoices: LiveHiddenUsers): Boolean {
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

            if (thisEvent is CommentEvent) {
                thisEvent.isScoped { it.containsAny(accountChoices.hiddenWordsCase) }
            }

            if (thisEvent.anyHashTag { it.containsAny(accountChoices.hiddenWordsCase) }) {
                return true
            }

            if (author?.containsAny(accountChoices.hiddenWordsCase) == true) return true
        }

        return false
    }

    var flowSet: NoteFlowSet? = null

    @Synchronized
    fun createOrDestroyFlowSync(create: Boolean) {
        if (create) {
            if (flowSet == null) {
                flowSet = NoteFlowSet(this)
            }
        } else {
            if (flowSet != null && flowSet?.isInUse() == false) {
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

    inline fun <reified T : Event> toEventHint(): EventHintBundle<T>? {
        val safeEvent = event
        return if (safeEvent is T) {
            EventHintBundle(safeEvent, relayHintUrl(), author?.bestRelayHint())
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
    val reactions = NoteBundledRefresherFlow(u)
    val boosts = NoteBundledRefresherFlow(u)
    val replies = NoteBundledRefresherFlow(u)
    val zaps = NoteBundledRefresherFlow(u)
    val ots = NoteBundledRefresherFlow(u)
    val edits = NoteBundledRefresherFlow(u)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun author() =
        metadata.stateFlow.flatMapLatest {
            it.note.author
                ?.metadata()
                ?.flow ?: MutableStateFlow(null)
        }

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            reports.hasObservers() ||
            relays.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            zaps.hasObservers() ||
            ots.hasObservers() ||
            edits.hasObservers()
}

@Stable
class NoteBundledRefresherFlow(
    val note: Note,
) {
    // Refreshes observers in batches.
    val stateFlow = MutableStateFlow(NoteState(note))

    fun invalidateData() {
        stateFlow.tryEmit(NoteState(note))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

@Immutable
class NoteState(
    val note: Note,
)

fun List<AddressableNote>.eventIdSet() = mapNotNullTo(mutableSetOf<HexKey>()) { it.event?.id }

fun <T : Event> Array<NoteState>.events() = mapNotNull { it.note.event as? T }

fun <T : Event> List<AddressableNote>.events() = mapNotNull { it.event as? T }

fun <T : Event> List<AddressableNote>.updateFlow(): Flow<List<T>> =
    if (this.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            flows = this.map { it.flow().metadata.stateFlow },
        ) {
            it.events()
        }
    }

public inline fun <T> Iterable<Note>.anyEvent(predicate: (T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    for (note in this) {
        val noteEvent = note.event as? T
        if (noteEvent != null && predicate(noteEvent)) return true
    }
    return false
}

public inline fun <T> Iterable<Note>.filterEvents(predicate: (T) -> Boolean): List<T> {
    if (this is Collection && isEmpty()) return emptyList()

    val dest = ArrayList<T>()
    for (note in this) {
        val noteEvent = note.event as? T
        if (noteEvent != null && predicate(noteEvent)) {
            dest.add(noteEvent)
        }
    }
    return dest
}

public inline fun <T> Iterable<Note>.filterAuthoredEvents(pubkey: HexKey): List<T> {
    if (this is Collection && isEmpty()) return emptyList()

    val dest = ArrayList<T>()
    for (note in this) {
        if (note.author?.pubkeyHex != pubkey) {
            val noteEvent = note.event as? T
            if (noteEvent != null) {
                dest.add(noteEvent)
            }
        }
    }
    return dest
}

public inline fun Iterable<Note>.anyNotNullEvent(predicate: (Event) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    for (note in this) {
        val noteEvent = note.event
        if (noteEvent != null && predicate(noteEvent)) return true
    }
    return false
}

fun <T : Event> List<Note>.latestByAuthor(): Map<User, T> {
    val oneResponsePerUser = mutableMapOf<User, T>()

    forEach { note ->
        val author = note.author ?: return@forEach
        val event = note.event as? T ?: return@forEach

        val currentResponse = oneResponsePerUser[author]
        if (currentResponse == null) {
            oneResponsePerUser[author] = event
        } else if (event.createdAt > currentResponse.createdAt) {
            oneResponsePerUser[author] = event
        }
    }

    return oneResponsePerUser
}
