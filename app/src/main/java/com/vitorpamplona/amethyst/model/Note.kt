package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.lnurl.LnInvoiceUtil
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.regex.Pattern

val tagSearch = Pattern.compile("(?:\\s|\\A)\\#\\[([0-9]+)\\]")

@Stable
class AddressableNote(val address: ATag) : Note(address.toTag()) {
    override fun idNote() = address.toNAddr()
    override fun toNEvent() = address.toNAddr()
    override fun idDisplayNote() = idNote().toShortenHex()
    override fun address() = address
    override fun createdAt() = (event as? LongTextNoteEvent)?.publishedAt() ?: event?.createdAt()
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
    var reactions = setOf<Note>()
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

    open fun idDisplayNote() = idNote().toShortenHex()

    fun channelHex(): HexKey? {
        return (event as? ChannelMessageEvent)?.channel()
            ?: (event as? ChannelMetadataEvent)?.channel()
            ?: (event as? ChannelCreateEvent)?.id
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
        if (replyTo == null || replyTo.isEmpty()) {
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
        if (replyTo == null || replyTo.isEmpty()) {
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
    fun removeReaction(note: Note) {
        reactions = reactions - note
        liveSet?.reactions?.invalidateData()
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
    fun addZap(zapRequest: Note, zap: Note?) {
        checkNotInMainThread()
        if (zapRequest !in zaps.keys) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        } else if (zapRequest in zaps.keys && zaps[zapRequest] == null) {
            zaps = zaps + Pair(zapRequest, zap)
            liveSet?.zaps?.invalidateData()
        }
    }

    @Synchronized
    fun addZapPayment(zapPaymentRequest: Note, zapPayment: Note?) {
        checkNotInMainThread()
        if (zapPaymentRequest !in zapPayments.keys) {
            zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
            liveSet?.zaps?.invalidateData()
        } else if (zapPaymentRequest in zapPayments.keys && zapPayments[zapPaymentRequest] == null) {
            zapPayments = zapPayments + Pair(zapPaymentRequest, zapPayment)
            liveSet?.zaps?.invalidateData()
        }
    }

    fun addReaction(note: Note) {
        if (note !in reactions) {
            reactions = reactions + note
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

    fun isReactedBy(user: User): Boolean {
        return reactions.any { it.author?.pubkeyHex == user.pubkeyHex }
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
        val dayAgo = Date().time / 1000 - 24 * 60 * 60
        return reports.isNotEmpty() ||
            (
                author?.reports?.values?.any {
                    it.firstOrNull { (it.createdAt() ?: 0) > dayAgo } != null
                } ?: false
                )
    }

    fun isNewThread(): Boolean {
        return event is RepostEvent || replyTo == null || replyTo?.size == 0
    }

    fun hasZapped(loggedIn: User): Boolean {
        return zaps.any { it.key.author == loggedIn }
    }

    fun hasReacted(loggedIn: User, content: String): Boolean {
        return reactedBy(loggedIn, content).isNotEmpty()
    }

    fun reactedBy(loggedIn: User, content: String): List<Note> {
        return reactions.filter { it.author == loggedIn && it.event?.content() == content }
    }

    fun hasBoostedInTheLast5Minutes(loggedIn: User): Boolean {
        val currentTime = Date().time / 1000
        return boosts.firstOrNull { it.author == loggedIn && (it.createdAt() ?: 0) > currentTime - (60 * 5) } != null // 5 minute protection
    }

    fun boostedBy(loggedIn: User): List<Note> {
        return boosts.filter { it.author == loggedIn }
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
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
            // if (hasObservers()) {
            postValue(NoteState(note))
            // }
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
