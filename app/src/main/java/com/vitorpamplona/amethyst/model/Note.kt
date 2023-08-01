package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.toNote
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.updated
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

val tagSearch = Pattern.compile("(?:\\s|\\A)\\#\\[([0-9]+)\\]")

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
}

@Stable
open class Note(val idHex: String) {
    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var event: EventInterface? = null
    var author: User? = null
    var replyTo: List<Note>? = null

    // These fields are updated every time an event related to this note is received.
    var replies = setOf<Note>()
        private set
    var reactions = mapOf<String, Set<Note>>()
        private set
    var boosts = setOf<Note>()
        private set
    var reports = mapOf<User, Set<Note>>()
        private set
    var zaps = mapOf<Note, Note?>()
        private set
    var zapPayments = mapOf<Note, Note?>()
        private set

    var relays = setOf<String>()
        private set

    var lastReactionsDownloadTime: Map<String, EOSETime> = emptyMap()

    fun id() = Hex.decode(idHex)
    open fun idNote() = id().toNote()

    open fun toNEvent(): String {
        return Nip19.createNEvent(idHex, author?.pubkeyHex, event?.kind(), relays.firstOrNull())
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
        this.event = event
        this.author = author
        this.replyTo = replyTo

        liveSet?.metadata?.invalidateData()
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HH:mm:ss"))
    }

    /**
     * This method caches signatures during each execution to avoid recalculation in longer threads
     */
    fun replyLevelSignature(cachedSignatures: MutableMap<Note, String> = mutableMapOf()): String {
        val replyTo = replyTo
        if (event is RepostEvent || event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()) {
            return "/" + formattedDateTime(createdAt() ?: 0) + ";"
        }

        return replyTo
            .map {
                cachedSignatures[it] ?: it.replyLevelSignature(cachedSignatures).apply { cachedSignatures.put(it, this) }
            }
            .maxBy { it.length }.removeSuffix(";") + "/" + formattedDateTime(createdAt() ?: 0) + ";"
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
            liveSet?.replies?.invalidateData()
        }
    }

    fun removeReply(note: Note) {
        replies = replies - note
        liveSet?.replies?.invalidateData()
    }
    fun removeBoost(note: Note) {
        boosts = boosts - note
        liveSet?.boosts?.invalidateData()
    }

    fun removeAllChildNotes(): Set<Note> {
        val toBeRemoved = replies +
            reactions.values.flatten() +
            boosts +
            reports.values.flatten() +
            zaps.keys +
            zaps.values.filterNotNull() +
            zapPayments.keys +
            zapPayments.values.filterNotNull()

        replies = setOf<Note>()
        reactions = mapOf<String, Set<Note>>()
        boosts = setOf<Note>()
        reports = mapOf<User, Set<Note>>()
        zaps = mapOf<Note, Note?>()
        zapPayments = mapOf<Note, Note?>()
        relays = setOf<String>()
        lastReactionsDownloadTime = emptyMap()

        liveSet?.replies?.invalidateData()
        liveSet?.reactions?.invalidateData()
        liveSet?.boosts?.invalidateData()
        liveSet?.reports?.invalidateData()
        liveSet?.zaps?.invalidateData()

        return toBeRemoved
    }

    fun removeReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyList()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        if (reaction in reactions.keys && reactions[reaction]?.contains(note) == true) {
            reactions[reaction]?.let {
                val newList = it.minus(note)
                if (newList.isEmpty()) {
                    reactions = reactions.minus(reaction)
                } else {
                    reactions = reactions + Pair(reaction, newList)
                }

                liveSet?.reactions?.invalidateData()
            }
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return

        if (author in reports.keys && reports[author]?.contains(deleteNote) == true) {
            reports[author]?.let {
                reports = reports + Pair(author, it.minus(deleteNote))
                liveSet?.reports?.invalidateData()
            }
        }
    }

    fun removeZap(note: Note) {
        if (zaps[note] != null) {
            zaps = zaps.minus(note)
            liveSet?.zaps?.invalidateData()
        } else if (zaps.containsValue(note)) {
            val toRemove = zaps.filterValues { it == note }
            zaps = zaps.minus(toRemove.keys)
            liveSet?.zaps?.invalidateData()
        }
    }

    fun removeZapPayment(note: Note) {
        if (zapPayments[note] != null) {
            zapPayments = zapPayments.minus(note)
            liveSet?.zaps?.invalidateData()
        } else if (zapPayments.containsValue(note)) {
            val toRemove = zapPayments.filterValues { it == note }
            zapPayments = zapPayments.minus(toRemove.keys)
            liveSet?.zaps?.invalidateData()
        }
    }

    fun addBoost(note: Note) {
        if (note !in boosts) {
            boosts = boosts + note
            liveSet?.boosts?.invalidateData()
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
                liveSet?.zaps?.invalidateData()
            }
        } else if (zaps[zapRequest] == null) {
            val inserted = innerAddZap(zapRequest, zap)
            if (inserted) {
                liveSet?.zaps?.invalidateData()
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
                liveSet?.zaps?.invalidateData()
            }
        } else if (zapPayments[zapPaymentRequest] == null) {
            val inserted = innerAddZapPayment(zapPaymentRequest, zapPayment)
            if (inserted) {
                liveSet?.zaps?.invalidateData()
            }
        }
    }

    fun addReaction(note: Note) {
        val tags = note.event?.tags() ?: emptyList()
        val reaction = note.event?.content()?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        if (reaction !in reactions.keys) {
            reactions = reactions + Pair(reaction, setOf(note))
            liveSet?.reactions?.invalidateData()
        } else if (reactions[reaction]?.contains(note) == false) {
            reactions = reactions + Pair(reaction, (reactions[reaction] ?: emptySet()) + note)
            liveSet?.reactions?.invalidateData()
        }
    }

    fun addReport(note: Note) {
        val author = note.author ?: return

        if (author !in reports.keys) {
            reports = reports + Pair(author, setOf(note))
            liveSet?.reports?.invalidateData()
        } else if (reports[author]?.contains(note) == false) {
            reports = reports + Pair(author, (reports[author] ?: emptySet()) + note)
            liveSet?.reports?.invalidateData()
        }
    }

    fun addRelay(relay: Relay) {
        if (relay.url !in relays) {
            relays = relays + relay.url
            liveSet?.relays?.invalidateData()
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

    fun publicZapAuthors(): Set<User> {
        // Zaps who the requester was the user
        return zaps.mapNotNull {
            it.key.author
        }.toSet()
    }

    fun publicZapAuthorHexes(): Set<HexKey> {
        // Zaps who the requester was the user
        return zaps.mapNotNull {
            it.key.author?.pubkeyHex
        }.toSet()
    }

    fun reactionAuthors(): Set<User> {
        // Zaps who the requester was the user
        return reactions.values.map {
            it.mapNotNull { it.author }
        }.flatten().toSet()
    }

    fun reactionAuthorHexes(): Set<HexKey> {
        // Zaps who the requester was the user
        return reactions.values.map {
            it.mapNotNull { it.author?.pubkeyHex }
        }.flatten().toSet()
    }

    fun replyAuthorHexes(): Set<HexKey> {
        // Zaps who the requester was the user
        return replies.mapNotNull {
            it.author?.pubkeyHex
        }.toSet()
    }

    fun replyAuthors(): Set<User> {
        // Zaps who the requester was the user
        return replies.mapNotNull {
            it.author
        }.toSet()
    }

    fun boostAuthors(): Set<User> {
        // Zaps who the requester was the user
        return boosts.mapNotNull {
            it.author
        }.toSet()
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

    fun reportsBy(user: User): Set<Note> {
        return reports[user] ?: emptySet()
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

    fun countReactions(): Int {
        return reactions.values.sumOf { it.size }
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
        replies = emptySet()
        reactions = emptyMap()
        boosts = emptySet()
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
            liveSet = null
        }
    }

    fun isHiddenFor(accountChoices: Account.LiveHiddenUsers): Boolean {
        if (event == null) return false

        val isBoostedNoteHidden = if (event is GenericRepostEvent || event is RepostEvent || event is CommunityPostApprovalEvent) {
            replyTo?.lastOrNull()?.isHiddenFor(accountChoices) ?: false
        } else {
            false
        }

        val isSensitive = event?.isSensitive() ?: false
        return isBoostedNoteHidden ||
            accountChoices.hiddenUsers.contains(author?.pubkeyHex) ||
            accountChoices.spammers.contains(author?.pubkeyHex) ||
            (isSensitive && accountChoices.showSensitiveContent == false)
    }
}

class NoteLiveSet(u: Note) {
    // Observers line up here.
    val metadata: NoteLiveData = NoteLiveData(u)

    val reactions: NoteLiveData = NoteLiveData(u)
    val boosts: NoteLiveData = NoteLiveData(u)
    val replies: NoteLiveData = NoteLiveData(u)
    val reports: NoteLiveData = NoteLiveData(u)
    val relays: NoteLiveData = NoteLiveData(u)
    val zaps: NoteLiveData = NoteLiveData(u)

    fun isInUse(): Boolean {
        return metadata.hasObservers() ||
            reactions.hasObservers() ||
            boosts.hasObservers() ||
            replies.hasObservers() ||
            reports.hasObservers() ||
            relays.hasObservers() ||
            zaps.hasObservers()
    }
}

class NoteLiveData(val note: Note) : LiveData<NoteState>(NoteState(note)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate() {
            checkNotInMainThread()

            if (hasActiveObservers()) {
                postValue(NoteState(note))
            }
        }
    }

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
