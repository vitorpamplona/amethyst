package com.vitorpamplona.amethyst.model

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
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var zapPayments = mapOf<Note, Note?>()
        private set

    var relays = listOf<String>()
        private set

    var lastReactionsDownloadTime: Map<String, EOSETime> = emptyMap()

    fun id() = Hex.decode(idHex)
    open fun idNote() = id().toNote()

    open fun toNEvent(): String {
        val myEvent = event
        return if (myEvent is WrappedEvent) {
            val host = myEvent.host
            if (host != null) {
                Nip19.createNEvent(
                    host.id,
                    host.pubKey,
                    host.kind(),
                    relays.firstOrNull()
                )
            } else {
                Nip19.createNEvent(idHex, author?.pubkeyHex, event?.kind(), relays.firstOrNull())
            }
        } else {
            Nip19.createNEvent(idHex, author?.pubkeyHex, event?.kind(), relays.firstOrNull())
        }
    }

    fun toNostrUri(): String {
        return "nostr:${toNEvent()}"
    }

    open fun idDisplayNote() = idNote().toShortenHex()

    fun channelHex(): HexKey? {
        return if (event is ChannelMessageEvent ||
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

    fun loadEvent(event: Event, author: User, replyTo: List<Note>) {
        if (this.event?.id() != event.id()) {
            this.event = event
            this.author = author
            this.replyTo = replyTo

            liveSet?.innerMetadata?.invalidateData()
        }
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
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
        now: Long
    ): LevelSignature {
        val replyTo = replyTo
        if (event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()) {
            return LevelSignature(
                signature = "/" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) + ";",
                createdAt = createdAt(),
                author = author
            )
        }

        val parent = (
            replyTo
                .filter { it.idHex in eventsToConsider } // This forces the signature to be based on a branch, avoiding two roots
                .map {
                    cachedSignatures[it] ?: it.replyLevelSignature(
                        eventsToConsider,
                        cachedSignatures,
                        account,
                        accountFollowingSet,
                        now
                    ).apply { cachedSignatures.put(it, this) }
                }
                .maxByOrNull { it.signature.length }
            )

        val parentSignature = parent?.signature?.removeSuffix(";") ?: ""

        val threadOrder = if (parent?.author == author && createdAt() != null) {
            // author of the thread first, in **ascending** order
            "9" + formattedDateTime((parent?.createdAt ?: 0) + (now - (createdAt() ?: 0))) + idHex.substring(0, 8)
        } else if (author?.pubkeyHex == account.pubkeyHex) {
            "8" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // my replies
        } else if (author?.pubkeyHex in accountFollowingSet) {
            "7" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // my follows replies.
        } else {
            "0" + formattedDateTime(createdAt() ?: 0) + idHex.substring(0, 8) // everyone else.
        }

        val mySignature = LevelSignature(
            signature = parentSignature + "/" + threadOrder + ";",
            createdAt = createdAt(),
            author = author
        )

        cachedSignatures[this] = mySignature
        return mySignature
    }

    fun replyLevel(cachedLevels: MutableMap<Note, Int> = mutableMapOf()): Int {
        val replyTo = replyTo
        if (event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()) {
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
        val toBeRemoved = replies +
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
        relays = listOf<String>()
        lastReactionsDownloadTime = emptyMap()

        liveSet?.innerReplies?.invalidateData()
        liveSet?.innerReactions?.invalidateData()
        liveSet?.innerBoosts?.invalidateData()
        liveSet?.innerReports?.invalidateData()
        liveSet?.innerZaps?.invalidateData()

        return toBeRemoved
    }

    fun removeReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyList()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        if (reaction in reactions.keys && reactions[reaction]?.contains(note) == true) {
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

        if (author in reports.keys && reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.innerReports?.invalidateData()
            }
        }
    }

    fun removeZap(note: Note) {
        if (zaps[note] != null) {
            zaps = zaps.minus(note)
            liveSet?.innerZaps?.invalidateData()
        } else if (zaps.containsValue(note)) {
            zaps = zaps.filterValues { it != note }
            liveSet?.innerZaps?.invalidateData()
        }
    }

    fun removeZapPayment(note: Note) {
        if (zapPayments[note] != null) {
            zapPayments = zapPayments.minus(note)
            liveSet?.innerZaps?.invalidateData()
        } else if (zapPayments.containsValue(note)) {
            val toRemove = zapPayments.filterValues { it == note }
            zapPayments = zapPayments.minus(toRemove.keys)
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
    private fun innerAddZap(zapRequest: Note, zap: Note?): Boolean {
        if (zapRequest !in zaps.keys) {
            zaps = zaps + Pair(zapRequest, zap)
            return true
        } else if (zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            return true
        }

        return false
    }

    fun addZap(zapRequest: Note, zap: Note?) {
        checkNotInMainThread()
        if (zapRequest !in zaps.keys) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted) {
                liveSet?.innerZaps?.invalidateData()
            }
        } else if (zaps[zapRequest] == null) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted) {
                liveSet?.innerZaps?.invalidateData()
            }
        }
    }

    @Synchronized
    private fun innerAddZapPayment(zapPaymentRequest: Note, zapPayment: Note?): Boolean {
        if (zapPaymentRequest !in zapPayments.keys) {
            zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
            return true
        } else if (zapPayments[zapPaymentRequest] == null) {
            zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
            return true
        }

        return false
    }

    fun addZapPayment(zapPaymentRequest: Note, zapPayment: Note?) {
        checkNotInMainThread()
        if (zapPaymentRequest !in zapPayments.keys) {
            val inserted = innerAddZapPayment(zapPaymentRequest, zapPayment)
            if (inserted) {
                liveSet?.innerZaps?.invalidateData()
            }
        } else if (zapPayments[zapPaymentRequest] == null) {
            val inserted = innerAddZapPayment(zapPaymentRequest, zapPayment)
            if (inserted) {
                liveSet?.innerZaps?.invalidateData()
            }
        }
    }

    fun addReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyList()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        if (reaction !in reactions.keys) {
            reactions = reactions + Pair(reaction, listOf(note))
            liveSet?.innerReactions?.invalidateData()
        } else if (reactions[reaction]?.contains(note) == false) {
            reactions = reactions + Pair(reaction, (reactions[reaction] ?: emptySet()) + note)
            liveSet?.innerReactions?.invalidateData()
        }
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        if (author !in reports.keys) {
            reports = reports + Pair(author, listOf(note))
            liveSet?.innerReports?.invalidateData()
        } else if (reports[author]?.contains(note) == false) {
            reports = reports + Pair(author, (reports[author] ?: emptySet()) + note)
            liveSet?.innerReports?.invalidateData()
        }
    }

    @Synchronized
    fun addRelaySync(url: String) {
        if (url !in relays) {
            relays = relays + url
        }
    }

    fun addRelay(relay: Relay) {
        if (relay.url !in relays) {
            addRelaySync(relay.url)
            liveSet?.innerRelays?.invalidateData()
        }
    }

    fun isZappedBy(user: User, account: Account): Boolean {
        // Zaps who the requester was the user
        return zaps.any {
            it.key.author?.pubkeyHex == user.pubkeyHex || account.decryptZapContentAuthor(it.key)?.pubKey == user.pubkeyHex
        } || zapPayments.any {
            val zapResponseEvent = it.value?.event as? LnZapPaymentResponseEvent
            val response = if (zapResponseEvent != null) {
                account.decryptZapPaymentResponseEvent(zapResponseEvent)
            } else {
                null
            }
            response is PayInvoiceSuccessResponse && account.isNIP47Author(zapResponseEvent?.requestAuthor())
        }
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

    fun reportAuthorsBy(users: Set<HexKey>): List<User> {
        return reports.keys.filter { it.pubkeyHex in users }
    }

    fun countReportAuthorsBy(users: Set<HexKey>): Int {
        return reports.keys.count { it.pubkeyHex in users }
    }

    fun reportsBy(users: Set<HexKey>): List<Note> {
        return reportAuthorsBy(users).mapNotNull {
            reports[it]
        }.flatten()
    }

    fun zappedAmount(privKey: ByteArray?, walletServicePubkey: ByteArray?): BigDecimal {
        // Regular Zap Receipts
        val completedZaps = zaps.asSequence()
            .mapNotNull { it.value?.event }
            .filterIsInstance<LnZapEvent>()
            .filter { it.amount != null }
            .associate {
                it.lnInvoice() to it.amount
            }
            .toMap()

        val completedPayments = if (privKey != null && walletServicePubkey != null) {
            // Payments confirmed by the User's Wallet
            zapPayments
                .asSequence()
                .filter {
                    val response = (it.value?.event as? LnZapPaymentResponseEvent)?.response(privKey, walletServicePubkey)
                    response is PayInvoiceSuccessResponse
                }
                .associate {
                    val lnInvoice = (it.key.event as? LnZapPaymentRequestEvent)?.lnInvoice(privKey, walletServicePubkey)
                    val amount = try {
                        if (lnInvoice == null) {
                            null
                        } else {
                            LnInvoiceUtil.getAmountInSats(lnInvoice)
                        }
                    } catch (e: java.lang.Exception) {
                        null
                    }
                    lnInvoice to amount
                }
                .toMap()
        } else {
            emptyMap()
        }

        return (completedZaps + completedPayments).values.filterNotNull().sumOf { it }
    }

    fun hasPledgeBy(user: User): Boolean {
        return replies
            .filter { it.event?.isTaggedHash("bounty-added-reward") ?: false }
            .any {
                val pledgeValue = try {
                    BigDecimal(it.event?.content())
                } catch (e: Exception) {
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
                author?.reports?.values?.any {
                    it.firstOrNull { (it.createdAt() ?: 0) > dayAgo } != null
                } ?: false
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

    fun hasReacted(loggedIn: User, content: String): Boolean {
        return reactedBy(loggedIn, content).isNotEmpty()
    }

    fun reactedBy(loggedIn: User, content: String): List<Note> {
        return reactions[content]?.filter { it.author == loggedIn } ?: emptyList()
    }

    fun reactedBy(loggedIn: User): List<String> {
        return reactions.filter { it.value.any { it.author == loggedIn } }.mapNotNull { it.key }
    }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        return boosts.firstOrNull { it.author == loggedIn && (it.createdAt() ?: 0) > TimeUtils.fiveMinutesAgo() } != null // 5 minute protection
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
    }

    fun clearEOSE() {
        lastReactionsDownloadTime = emptyMap()
    }

    var liveSet: NoteLiveSet? = null

    fun live(): NoteLiveSet {
        if (liveSet == null) {
            liveSet = NoteLiveSet(this)
        }
        return liveSet!!
    }

    fun clearLive() {
        if (liveSet != null && liveSet?.isInUse() == false) {
            liveSet?.destroy()
            liveSet = null
        }
    }

    fun isHiddenFor(accountChoices: Account.LiveHiddenUsers): Boolean {
        val thisEvent = event ?: return false

        val isBoostedNoteHidden = if (thisEvent is GenericRepostEvent || thisEvent is RepostEvent || thisEvent is CommunityPostApprovalEvent) {
            replyTo?.lastOrNull()?.isHiddenFor(accountChoices) ?: false
        } else {
            false
        }

        val isHiddenByWord = if (thisEvent is BaseTextNoteEvent) {
            accountChoices.hiddenWords.any {
                thisEvent.content.contains(it, true)
            }
        } else {
            false
        }

        val isSensitive = thisEvent.isSensitive()
        return isBoostedNoteHidden || isHiddenByWord ||
            accountChoices.hiddenUsers.contains(author?.pubkeyHex) ||
            accountChoices.spammers.contains(author?.pubkeyHex) ||
            (isSensitive && accountChoices.showSensitiveContent == false)
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

    val metadata = innerMetadata.map { it }
    val reactions = innerReactions.map { it }
    val boosts = innerBoosts.map { it }
    val replies = innerReplies.map { it }
    val reports = innerReports.map { it }
    val relays = innerRelays.map { it }
    val zaps = innerZaps.map { it }

    val authorChanges = innerMetadata.map {
        it.note.author
    }

    val hasEvent = innerMetadata.map {
        it.note.event != null
    }.distinctUntilChanged()

    val hasReactions = innerZaps.combineWith(innerBoosts, innerReactions) { zapState, boostState, reactionState ->
        zapState?.note?.zaps?.isNotEmpty() ?: false ||
            boostState?.note?.boosts?.isNotEmpty() ?: false ||
            reactionState?.note?.reactions?.isNotEmpty() ?: false
    }.distinctUntilChanged()

    val replyCount = innerReplies.map {
        it.note.replies.size
    }.distinctUntilChanged()

    val reactionCount = innerReactions.map {
        it.note.reactions.values.sumOf { it.size }
    }.distinctUntilChanged()

    val boostCount = innerBoosts.map {
        it.note.boosts.size
    }.distinctUntilChanged()

    val boostList = innerBoosts.map {
        it.note.boosts.toImmutableList()
    }.distinctUntilChanged()

    val relayInfo = innerRelays.map {
        it.note.relays.map {
            RelayBriefInfo(it)
        }.toImmutableList()
    }

    val content = innerMetadata.map {
        it.note.event?.content() ?: ""
    }

    fun isInUse(): Boolean {
        return metadata.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            reports.hasObservers() ||
            relays.hasObservers() ||
            zaps.hasObservers() ||
            authorChanges.hasObservers() ||
            hasEvent.hasObservers() ||
            hasReactions.hasObservers() ||
            replyCount.hasObservers() ||
            reactionCount.hasObservers() ||
            boostCount.hasObservers() ||
            boostList.hasObservers()
    }

    fun destroy() {
        innerMetadata.destroy()
        innerReactions.destroy()
        innerBoosts.destroy()
        innerReplies.destroy()
        innerReports.destroy()
        innerRelays.destroy()
        innerZaps.destroy()
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

        bundler.invalidate() {
            checkNotInMainThread()

            postValue(NoteState(note))
        }
    }

    fun <Y> map(
        transform: (NoteState) -> Y
    ): NoteLoadingLiveData<Y> {
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

@Immutable
class NoteState(val note: Note)

@Immutable
data class RelayBriefInfo(
    val url: String,
    val displayUrl: String = url.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/").intern(),
    val favIcon: String = "https://$displayUrl/favicon.ico".intern()
)
