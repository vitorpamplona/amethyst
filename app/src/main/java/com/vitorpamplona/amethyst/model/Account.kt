package com.vitorpamplona.amethyst.model

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.OptOutFromFilters
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.Contact
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.EmojiUrl
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GeneralListEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.events.IdentityClaim
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.NIP24Factory
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.Response
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.StatusEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.events.ZapSplitSetup
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.net.Proxy
import java.util.Locale
import kotlin.coroutines.resume

val DefaultChannels = setOf(
    "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", // -> Anigma's Nostr
    "42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5" // -> Amethyst's Group
)

val DefaultReactions = listOf("\uD83D\uDE80", "\uD83E\uDEC2", "\uD83D\uDC40", "\uD83D\uDE02")

val DefaultZapAmounts = listOf(500L, 1000L, 5000L)

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

val GLOBAL_FOLLOWS = " Global " // This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val KIND3_FOLLOWS = " All Follows " // This has spaces to avoid mixing with a potential NIP-51 list with the same name.

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val keyPair: KeyPair,
    val signer: NostrSigner = NostrSignerInternal(keyPair),

    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
    var zapAmountChoices: List<Long> = DefaultZapAmounts,
    var reactionChoices: List<String> = DefaultReactions,
    var defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PRIVATE,
    var defaultFileServer: ServersAvailable = ServersAvailable.NOSTR_BUILD,
    var defaultHomeFollowList: MutableStateFlow<String> = MutableStateFlow(KIND3_FOLLOWS),
    var defaultStoriesFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var defaultNotificationFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var defaultDiscoveryFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var zapPaymentRequest: Nip47URI? = null,
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP24WarningDialog: Boolean = false,
    var backupContactList: ContactListEvent? = null,
    var proxy: Proxy? = null,
    var proxyPort: Int = 9050,
    var showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
    var lastReadPerRoute: Map<String, Long> = mapOf<String, Long>()
) {
    // Uses a single scope for the entire application.
    val scope = Amethyst.instance.applicationIOScope

    var transientHiddenUsers: ImmutableSet<String> = persistentSetOf()

    data class PaymentRequest(
        val relayUrl: String,
        val lnInvoice: String?,
        val description: String?,
        val otherOptionsUrl: String?
    )
    var transientPaymentRequestDismissals: Set<PaymentRequest> = emptySet()
    val transientPaymentRequests: MutableStateFlow<Set<PaymentRequest>> = MutableStateFlow(emptySet())

    // Observers line up here.
    val live: AccountLiveData = AccountLiveData(this)
    val liveLanguages: AccountLiveData = AccountLiveData(this)
    val saveable: AccountLiveData = AccountLiveData(this)

    @Immutable
    data class LiveFollowLists(
        val users: ImmutableSet<String> = persistentSetOf(),
        val hashtags: ImmutableSet<String> = persistentSetOf(),
        val geotags: ImmutableSet<String> = persistentSetOf(),
        val communities: ImmutableSet<String> = persistentSetOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveKind3Follows: StateFlow<LiveFollowLists> by lazy {
        userProfile().live().follows.asFlow().transformLatest {
            emit(
                LiveFollowLists(
                    userProfile().cachedFollowingKeySet().toImmutableSet(),
                    userProfile().cachedFollowingTagSet().toImmutableSet(),
                    userProfile().cachedFollowingGeohashSet().toImmutableSet(),
                    userProfile().cachedFollowingCommunitiesSet().toImmutableSet()
                )
            )
        }.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveHomeList: StateFlow<NoteState?> by lazy {
        defaultHomeFollowList.transformLatest {
            LocalCache.checkGetOrCreateAddressableNote(it)?.flow()?.metadata?.stateFlow?.let {
                emit(it)
            }
        }.flattenMerge()
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    val liveHomeFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combineTransform(defaultHomeFollowList, liveKind3Follows, liveHomeList) { listName, kind3Follows, peopleListFollows ->
            if (listName == GLOBAL_FOLLOWS) {
                emit(null)
            } else if (listName == KIND3_FOLLOWS) {
                emit(kind3Follows)
            } else {
                val result = withTimeoutOrNull(1000) {
                    suspendCancellableCoroutine { continuation ->
                        decryptLiveFollows(peopleListFollows) {
                            continuation.resume(it)
                        }
                    }
                }
                result?.let {
                    emit(it)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveNotificationList: StateFlow<NoteState?> by lazy {
        defaultNotificationFollowList.transformLatest {
            LocalCache.checkGetOrCreateAddressableNote(it)?.flow()?.metadata?.stateFlow?.let {
                emit(it)
            }
        }.flattenMerge()
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    val liveNotificationFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combineTransform(defaultNotificationFollowList, liveKind3Follows, liveNotificationList) { listName, kind3Follows, peopleListFollows ->
            if (listName == GLOBAL_FOLLOWS) {
                emit(null)
            } else if (listName == KIND3_FOLLOWS) {
                emit(kind3Follows)
            } else {
                val result = withTimeoutOrNull(1000) {
                    suspendCancellableCoroutine { continuation ->
                        decryptLiveFollows(peopleListFollows) {
                            continuation.resume(it)
                        }
                    }
                }
                result?.let {
                    emit(it)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveStoriesList: StateFlow<NoteState?> by lazy {
        defaultStoriesFollowList.transformLatest {
            LocalCache.checkGetOrCreateAddressableNote(it)?.flow()?.metadata?.stateFlow?.let {
                emit(it)
            }
        }.flattenMerge()
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    val liveStoriesFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combineTransform(defaultStoriesFollowList, liveKind3Follows, liveStoriesList) { listName, kind3Follows, peopleListFollows ->
            if (listName == GLOBAL_FOLLOWS) {
                emit(null)
            } else if (listName == KIND3_FOLLOWS) {
                emit(kind3Follows)
            } else {
                val result = withTimeoutOrNull(1000) {
                    suspendCancellableCoroutine { continuation ->
                        decryptLiveFollows(peopleListFollows) {
                            continuation.resume(it)
                        }
                    }
                }
                result?.let {
                    emit(it)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveDiscoveryList: StateFlow<NoteState?> by lazy {
        defaultDiscoveryFollowList.transformLatest {
            LocalCache.checkGetOrCreateAddressableNote(it)?.flow()?.metadata?.stateFlow?.let {
                emit(it)
            }
        }.flattenMerge()
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    val liveDiscoveryFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combineTransform(defaultDiscoveryFollowList, liveKind3Follows, liveDiscoveryList) { listName, kind3Follows, peopleListFollows ->
            if (listName == GLOBAL_FOLLOWS) {
                emit(null)
            } else if (listName == KIND3_FOLLOWS) {
                emit(kind3Follows)
            } else {
                val result = withTimeoutOrNull(1000) {
                    suspendCancellableCoroutine { continuation ->
                        decryptLiveFollows(peopleListFollows) {
                            continuation.resume(it)
                        }
                    }
                }
                result?.let {
                    emit(it)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    private fun decryptLiveFollows(peopleListFollows: NoteState?, onReady: (LiveFollowLists) -> Unit) {
        val listEvent = (peopleListFollows?.note?.event as? GeneralListEvent)
        listEvent?.privateTags(signer) { privateTagList ->
            onReady(
                LiveFollowLists(
                    users = (listEvent.bookmarkedPeople() + listEvent.filterUsers(privateTagList)).toImmutableSet(),
                    hashtags = (listEvent.hashtags() + listEvent.filterHashtags(privateTagList)).toImmutableSet(),
                    geotags = (listEvent.geohashes() + listEvent.filterGeohashes(privateTagList)).toImmutableSet(),
                    communities = (listEvent.taggedAddresses() + listEvent.filterAddresses(privateTagList)).map { it.toTag() }.toImmutableSet()
                )
            )
        }
    }

    @Immutable
    data class LiveHiddenUsers(
        val hiddenUsers: ImmutableSet<String>,
        val spammers: ImmutableSet<String>,
        val hiddenWords: ImmutableSet<String>,
        val showSensitiveContent: Boolean?
    )

    val flowHiddenUsers: StateFlow<LiveHiddenUsers> by lazy {
        combineTransform(
            live.asFlow(),
            getBlockListNote().flow().metadata.stateFlow,
            getMuteListNote().flow().metadata.stateFlow
        ) { localLive, blockList, muteList ->
            checkNotInMainThread()

            val resultBlockList = withTimeoutOrNull(1000) {
                suspendCancellableCoroutine { continuation ->
                    (blockList.note.event as? PeopleListEvent)?.publicAndPrivateUsersAndWords(signer) {
                        continuation.resume(it)
                    }
                }
            } ?: PeopleListEvent.UsersAndWords()

            val resultMuteList = withTimeoutOrNull(1000) {
                suspendCancellableCoroutine { continuation ->
                    (muteList.note.event as? MuteListEvent)?.publicAndPrivateUsersAndWords(signer) {
                        continuation.resume(it)
                    }
                }
            } ?: PeopleListEvent.UsersAndWords()

            emit(
                LiveHiddenUsers(
                    hiddenUsers = (resultBlockList.users + resultMuteList.users).toPersistentSet(),
                    hiddenWords = (resultBlockList.words + resultMuteList.words).toPersistentSet(),
                    spammers = localLive.account.transientHiddenUsers,
                    showSensitiveContent = localLive.account.showSensitiveContent
                )
            )
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            LiveHiddenUsers(
                hiddenUsers = persistentSetOf(),
                hiddenWords = persistentSetOf(),
                spammers = transientHiddenUsers,
                showSensitiveContent = showSensitiveContent
            )
        )
    }

    val liveHiddenUsers = flowHiddenUsers.asLiveData()

    val decryptBookmarks: LiveData<BookmarkListEvent> by lazy {
        userProfile().live().innerBookmarks.switchMap { userState ->
            liveData(Dispatchers.IO) {
                userState.user.latestBookmarkList?.privateTags(signer) {
                    scope.launch(Dispatchers.IO) {
                        userState.user.latestBookmarkList?.let {
                            emit(it)
                        }
                    }
                }
            }
        }
    }

    fun addPaymentRequestIfNew(paymentRequest: PaymentRequest) {
        if (!this.transientPaymentRequests.value.contains(paymentRequest) &&
            !this.transientPaymentRequestDismissals.contains(paymentRequest)
        ) {
            this.transientPaymentRequests.value = transientPaymentRequests.value + paymentRequest
        }
    }

    fun dismissPaymentRequest(request: PaymentRequest) {
        if (this.transientPaymentRequests.value.contains(request)) {
            this.transientPaymentRequests.value = transientPaymentRequests.value - request
            this.transientPaymentRequestDismissals = transientPaymentRequestDismissals + request
        }
    }

    var userProfileCache: User? = null

    fun updateOptOutOptions(warnReports: Boolean, filterSpam: Boolean) {
        warnAboutPostsWithReports = warnReports
        filterSpamFromStrangers = filterSpam
        OptOutFromFilters.start(warnAboutPostsWithReports, filterSpamFromStrangers)
        if (!filterSpamFromStrangers) {
            transientHiddenUsers = persistentSetOf()
        }
        live.invalidateData()
        saveable.invalidateData()
    }

    fun userProfile(): User {
        return userProfileCache ?: run {
            val myUser: User = LocalCache.getOrCreateUser(keyPair.pubKey.toHexKey())
            userProfileCache = myUser
            myUser
        }
    }

    fun isWriteable(): Boolean {
        return keyPair.privKey != null || signer is NostrSignerExternal
    }

    fun sendNewRelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            val event = ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            val event = ContactListEvent.createFromScratch(
                followUsers = listOf(),
                followTags = listOf(),
                followGeohashes = listOf(),
                followCommunities = listOf(),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                signer = signer
            ) {
                // Keep this local to avoid erasing a good contact list.
                // Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    suspend fun sendNewUserMetadata(toString: String, identities: List<IdentityClaim>) {
        if (!isWriteable()) return

        MetadataEvent.create(toString, identities, signer) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }

        return
    }

    fun reactionTo(note: Note, reaction: String): List<Note> {
        return note.reactedBy(userProfile(), reaction)
    }

    fun hasBoosted(note: Note): Boolean {
        return boostsTo(note).isNotEmpty()
    }

    fun boostsTo(note: Note): List<Note> {
        return note.boostedBy(userProfile())
    }

    fun hasReacted(note: Note, reaction: String): Boolean {
        return note.hasReacted(userProfile(), reaction)
    }

    suspend fun reactTo(note: Note, reaction: String) {
        if (!isWriteable()) return

        if (hasReacted(note, reaction)) {
            // has already liked this note
            return
        }

        if (note.event is ChatMessageEvent) {
            val event = note.event as ChatMessageEvent
            val users = event.recipientsPubKey().plus(event.pubKey).toSet().toList()

            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        NIP24Factory().createReactionWithinGroup(
                            emojiUrl = emojiUrl,
                            originalNote = it,
                            to = users,
                            signer = signer
                        ) {
                            broadcastPrivately(it)
                        }
                    }

                    return
                }
            }

            note.event?.let {
                NIP24Factory().createReactionWithinGroup(
                    content = reaction,
                    originalNote = it,
                    to = users,
                    signer = signer
                ) {
                    broadcastPrivately(it)
                }
            }
            return
        } else {
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        ReactionEvent.create(emojiUrl, it, signer) {
                            Client.send(it)
                            LocalCache.consume(it)
                        }
                    }

                    return
                }
            }

            note.event?.let {
                ReactionEvent.create(reaction, it, signer) {
                    Client.send(it)
                    LocalCache.consume(it)
                }
            }
        }
    }

    fun createZapRequestFor(note: Note, pollOption: Int?, message: String = "", zapType: LnZapEvent.ZapType, toUser: User?, onReady: (LnZapRequestEvent) -> Unit) {
        if (!isWriteable()) return

        note.event?.let { event ->
            LnZapRequestEvent.create(
                event,
                userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                    ?: localRelays.map { it.url }.toSet(),
                signer,
                pollOption,
                message,
                zapType,
                toUser?.pubkeyHex,
                onReady = onReady
            )
        }
    }

    fun hasWalletConnectSetup(): Boolean {
        return zapPaymentRequest != null
    }

    fun isNIP47Author(pubkeyHex: String?): Boolean {
        return (getNIP47Signer().pubKey == pubkeyHex)
    }

    fun getNIP47Signer(): NostrSigner {
        return zapPaymentRequest?.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) } ?: signer
    }

    fun decryptZapPaymentResponseEvent(zapResponseEvent: LnZapPaymentResponseEvent, onReady: (Response) -> Unit) {
        val myNip47 = zapPaymentRequest ?: return

        val signer = myNip47.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) } ?: signer

        zapResponseEvent.response(signer, onReady)
    }

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note?, onWasZapped: () -> Unit) {
        zappedNote?.isZappedBy(userProfile(), this, onWasZapped)
    }

    fun calculateZappedAmount(zappedNote: Note?, onReady: (BigDecimal) -> Unit) {
        zappedNote?.zappedAmountWithNWCPayments(getNIP47Signer(), onReady)
    }

    fun sendZapPaymentRequestFor(bolt11: String, zappedNote: Note?, onResponse: (Response?) -> Unit) {
        if (!isWriteable()) return

        zapPaymentRequest?.let { nip47 ->
            val signer = nip47.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) } ?: signer

            LnZapPaymentRequestEvent.create(bolt11, nip47.pubKeyHex, signer) { event ->
                val wcListener = NostrLnZapPaymentResponseDataSource(
                    fromServiceHex = nip47.pubKeyHex,
                    toUserHex = event.pubKey,
                    replyingToHex = event.id,
                    authSigner = signer
                )
                wcListener.start()

                LocalCache.consume(event, zappedNote) {
                    it.response(signer) {
                        onResponse(it)
                    }
                }

                Client.send(event, nip47.relayUri, wcListener.feedTypes) {
                    wcListener.destroy()
                }
            }
        }
    }

    fun createZapRequestFor(userPubKeyHex: String, message: String = "", zapType: LnZapEvent.ZapType, onReady: (LnZapRequestEvent) -> Unit) {
        LnZapRequestEvent.create(
            userPubKeyHex,
            userProfile().latestContactList?.relays()?.keys?.ifEmpty { null }
                ?: localRelays.map { it.url }.toSet(),
            signer,
            message,
            zapType,
            onReady = onReady
        )
    }

    suspend fun report(note: Note, type: ReportEvent.ReportType, content: String = "") {
        if (!isWriteable()) return

        if (note.hasReacted(userProfile(), "⚠️")) {
            // has already liked this note
            return
        }

        note.event?.let {
            ReactionEvent.createWarning(it, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }

        note.event?.let {
            ReportEvent.create(it, type, signer, content) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    suspend fun report(user: User, type: ReportEvent.ReportType) {
        if (!isWriteable()) return

        if (user.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        ReportEvent.create(user.pubkeyHex, type, signer) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    suspend fun delete(note: Note) {
        return delete(listOf(note))
    }

    suspend fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myNotes = notes.filter { it.author == userProfile() }.map { it.idHex }

        if (myNotes.isNotEmpty()) {
            DeletionEvent.create(myNotes, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun createHTTPAuthorization(url: String, method: String, body: String? = null, onReady: (HTTPAuthorizationEvent) -> Unit) {
        if (!isWriteable()) return

        HTTPAuthorizationEvent.create(url, method, body, signer, onReady = onReady)
    }

    suspend fun boost(note: Note) {
        if (!isWriteable()) return

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        note.event?.let {
            if (it.kind() == 1) {
                RepostEvent.create(it, signer) {
                    Client.send(it)
                    LocalCache.justConsume(it, null)
                }
            } else {
                GenericRepostEvent.create(it, signer) {
                    Client.send(it)
                    LocalCache.justConsume(it, null)
                }
            }
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
                it.host?.let { hostEvent ->
                    Client.send(hostEvent)
                }
            } else {
                Client.send(it)
            }
        }
    }

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(Contact(user.pubkeyHex, null)),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                signer = signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followEvent(contactList, channel.idHex, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList().plus(channel.idHex),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                signer = signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.address, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = listOf(community.address),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun followHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followHashtag(
                contactList,
                tag,
                signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = listOf(tag),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                signer = signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun followGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followGeohash(
                contactList,
                geohash,
                signer,
                onReady = this::onNewEventCreated
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = listOf(geohash),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
                signer = signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    fun onNewEventCreated(event: Event) {
        Client.send(event)
        LocalCache.justConsume(event, null)
    }

    fun unfollow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowUser(
                contactList,
                user.pubkeyHex,
                signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    suspend fun unfollowHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowHashtag(
                contactList,
                tag,
                signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    suspend fun unfollowGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowGeohash(
                contactList,
                geohash,
                signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    suspend fun unfollow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowEvent(
                contactList,
                channel.idHex,
                signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    suspend fun unfollow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowAddressableEvent(
                contactList,
                community.address,
                signer,
                onReady = this::onNewEventCreated
            )
        }
    }

    fun createNip95(byteArray: ByteArray, headerInfo: FileHeader, onReady: (Pair<FileStorageEvent, FileStorageHeaderEvent>) -> Unit) {
        if (!isWriteable()) return

        FileStorageEvent.create(
            mimeType = headerInfo.mimeType ?: "",
            data = byteArray,
            signer = signer
        ) { data ->
            FileStorageHeaderEvent.create(
                data,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash,
                alt = headerInfo.alt,
                sensitiveContent = headerInfo.sensitiveContent,
                signer = signer
            ) { signedEvent ->
                onReady(
                    Pair(data, signedEvent)
                )
            }
        }
    }

    fun sendNip95(data: FileStorageEvent, signedEvent: FileStorageHeaderEvent, relayList: List<Relay>? = null): Note? {
        if (!isWriteable()) return null

        Client.send(data, relayList = relayList)
        LocalCache.consume(data, null)

        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.notes[signedEvent.id]
    }

    private fun sendHeader(signedEvent: FileHeaderEvent, relayList: List<Relay>? = null, onReady: (Note) -> Unit) {
        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        LocalCache.notes[signedEvent.id]?.let {
            onReady(it)
        }
    }

    fun sendHeader(headerInfo: FileHeader, relayList: List<Relay>? = null, onReady: (Note) -> Unit) {
        if (!isWriteable()) return

        FileHeaderEvent.create(
            url = headerInfo.url,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash,
            alt = headerInfo.alt,
            sensitiveContent = headerInfo.sensitiveContent,
            signer = signer
        ) { event ->
            sendHeader(event, relayList = relayList, onReady)
        }
    }

    fun sendPost(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        tags: List<String>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        TextNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            extraTags = tags,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            replyingTo = replyingTo,
            root = root,
            directMentions = directMentions,
            geohash = geohash,
            signer = signer
        ) {
            Client.send(it, relayList = relayList)
            LocalCache.justConsume(it, null)

            // broadcast replied notes
            replyingTo?.let {
                LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                    Client.send(it, relayList = relayList)
                }
            }
            replyTo?.forEach {
                it.event?.let {
                    Client.send(it, relayList = relayList)
                }
            }
            addresses?.forEach {
                LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                    Client.send(it, relayList = relayList)
                }
            }
        }
    }

    fun sendPoll(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        pollOptions: Map<Int, String>,
        valueMaximum: Int?,
        valueMinimum: Int?,
        consensusThreshold: Int?,
        closedAt: Int?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        relayList: List<Relay>? = null,
        geohash: String? = null
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        PollNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            signer = signer,
            pollOptions = pollOptions,
            valueMaximum = valueMaximum,
            valueMinimum = valueMinimum,
            consensusThreshold = consensusThreshold,
            closedAt = closedAt,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash
        ) {
            Client.send(it, relayList = relayList)
            LocalCache.justConsume(it, null)

            // Rebroadcast replies and tags to the current relay set
            replyTo?.forEach {
                it.event?.let {
                    Client.send(it, relayList = relayList)
                }
            }
            addresses?.forEach {
                LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                    Client.send(it, relayList = relayList)
                }
            }
        }
    }

    fun sendChannelMessage(message: String, toChannel: String, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        ChannelMessageEvent.create(
            message = message,
            channel = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            signer = signer
        ) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun sendLiveMessage(message: String, toChannel: ATag, replyTo: List<Note>?, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        LiveActivitiesChatMessageEvent.create(
            message = message,
            activity = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            signer = signer
        ) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun sendPrivateMessage(message: String, toUser: User, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        sendPrivateMessage(message, toUser.pubkeyHex, replyingTo, mentions, zapReceiver, wantsToMarkAsSensitive, zapRaiserAmount, geohash)
    }

    fun sendPrivateMessage(message: String, toUser: HexKey, replyingTo: Note? = null, mentions: List<User>?, zapReceiver: List<ZapSplitSetup>? = null, wantsToMarkAsSensitive: Boolean, zapRaiserAmount: Long? = null, geohash: String? = null) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        PrivateDmEvent.create(
            recipientPubKey = toUser,
            publishedRecipientPubKey = toUser,
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            signer = signer,
            advertiseNip18 = false
        ) {
            Client.send(it)
            LocalCache.consume(it, null)
        }
    }

    fun sendNIP24PrivateMessage(
        message: String,
        toUsers: List<HexKey>,
        subject: String? = null,
        replyingTo: Note? = null,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null
    ) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        NIP24Factory().createMsgNIP24(
            msg = message,
            to = toUsers,
            subject = subject,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            signer = signer
        ) {
            broadcastPrivately(it)
        }
    }

    fun broadcastPrivately(signedEvents: NIP24Factory.Result) {
        val mine = signedEvents.wraps.filter {
            (it.recipientPubKey() == signer.pubKey)
        }

        mine.forEach { giftWrap ->
            giftWrap.cachedGift(signer) { gift ->
                if (gift is SealedGossipEvent) {
                    gift.cachedGossip(signer) { gossip ->
                        LocalCache.justConsume(gossip, null)
                    }
                } else {
                    LocalCache.justConsume(gift, null)
                }
            }

            LocalCache.consume(giftWrap, null)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else LocalCache.getNoteIfExists(id)

        signedEvents.wraps.forEach {
            // Creates an alias
            if (mineNote != null && it.recipientPubKey() != keyPair.pubKey.toHexKey()) {
                LocalCache.getOrAddAliasNote(it.id, mineNote)
            }

            Client.send(it)
        }
    }

    fun sendCreateNewChannel(name: String, about: String, picture: String) {
        if (!isWriteable()) return

        ChannelCreateEvent.create(
            name = name,
            about = about,
            picture = picture,
            signer = signer
        ) {
            Client.send(it)
            LocalCache.justConsume(it, null)

            LocalCache.getChannelIfExists(it.id)?.let {
                follow(it)
            }
        }
    }

    fun updateStatus(oldStatus: AddressableNote, newStatus: String) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.update(oldEvent, newStatus, signer) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun createStatus(newStatus: String) {
        if (!isWriteable()) return

        StatusEvent.create(newStatus, "general", expiration = null, signer) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun deleteStatus(oldStatus: AddressableNote) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.clear(oldEvent, signer) { event ->
            Client.send(event)
            LocalCache.justConsume(event, null)

            DeletionEvent.create(listOf(event.id), signer) { event2 ->
                Client.send(event2)
                LocalCache.justConsume(event2, null)
            }
        }
    }

    fun removeEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable()) return

        val noteEvent = usersEmojiList.event
        if (noteEvent !is EmojiPackSelectionEvent) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        EmojiPackSelectionEvent.create(
            noteEvent.taggedAddresses().filter { it != emojiListEvent.address() },
            signer
        ) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun addEmojiPack(usersEmojiList: Note, emojiList: Note) {
        if (!isWriteable()) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        if (usersEmojiList.event == null) {
            EmojiPackSelectionEvent.create(
                listOf(emojiListEvent.address()),
                signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            val noteEvent = usersEmojiList.event
            if (noteEvent !is EmojiPackSelectionEvent) return

            if (noteEvent.taggedAddresses().any { it == emojiListEvent.address() }) {
                return
            }

            EmojiPackSelectionEvent.create(
                noteEvent.taggedAddresses().plus(emojiListEvent.address()),
                signer
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun addBookmark(note: Note, isPrivate: Boolean) {
        if (!isWriteable()) return

        if (note is AddressableNote) {
            BookmarkListEvent.addReplaceable(
                userProfile().latestBookmarkList,
                note.address,
                isPrivate,
                signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.addEvent(
                userProfile().latestBookmarkList,
                note.idHex,
                isPrivate,
                signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun removeBookmark(note: Note, isPrivate: Boolean) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList ?: return

        if (note is AddressableNote) {
            BookmarkListEvent.removeReplaceable(
                bookmarks,
                note.address,
                isPrivate,
                signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.removeEvent(
                bookmarks,
                note.idHex,
                isPrivate,
                signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun createAuthEvent(relay: Relay, challenge: String, onReady: (RelayAuthEvent) -> Unit) {
        return createAuthEvent(relay.url, challenge, onReady = onReady)
    }

    fun createAuthEvent(relayUrl: String, challenge: String, onReady: (RelayAuthEvent) -> Unit) {
        if (!isWriteable()) return

        RelayAuthEvent.create(relayUrl, challenge, signer, onReady = onReady)
    }

    fun isInPrivateBookmarks(note: Note, onReady: (Boolean) -> Unit) {
        if (!isWriteable()) return

        if (note is AddressableNote) {
            userProfile().latestBookmarkList?.privateTaggedAddresses(signer) {
                onReady(it.contains(note.address))
            }
        } else {
            userProfile().latestBookmarkList?.privateTaggedEvents(signer) {
                onReady(it.contains(note.idHex))
            }
        }
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        if (!isWriteable()) return false

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.taggedAddresses()?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.taggedEvents()?.contains(note.idHex) == true
        }
    }

    fun getBlockListNote(): AddressableNote {
        val aTag = ATag(
            PeopleListEvent.kind,
            userProfile().pubkeyHex,
            PeopleListEvent.blockList,
            null
        )
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    fun getMuteListNote(): AddressableNote {
        val aTag = ATag(
            MuteListEvent.kind,
            userProfile().pubkeyHex,
            "",
            null
        )
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    fun getBlockList(): PeopleListEvent? {
        return getBlockListNote().event as? PeopleListEvent
    }

    fun getMuteList(): MuteListEvent? {
        return getMuteListNote().event as? MuteListEvent
    }

    fun hideWord(word: String) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addWord(
                earlierVersion = muteList,
                word = word,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            MuteListEvent.createListWithWord(
                word = word,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun showWord(word: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeWord(
                earlierVersion = blockList,
                word = word,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeWord(
                earlierVersion = muteList,
                word = word,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun hideUser(pubkeyHex: String) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            MuteListEvent.createListWithUser(
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun showUser(pubkeyHex: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }

        transientHiddenUsers = (transientHiddenUsers - pubkeyHex).toImmutableSet()
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultZapType(zapType: LnZapEvent.ZapType) {
        defaultZapType = zapType
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultFileServer(server: ServersAvailable) {
        defaultFileServer = server
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultHomeFollowList(name: String) {
        defaultHomeFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultStoriesFollowList(name: String) {
        defaultStoriesFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultNotificationFollowList(name: String) {
        defaultNotificationFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultDiscoveryFollowList(name: String) {
        defaultDiscoveryFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapAmounts(newAmounts: List<Long>) {
        zapAmountChoices = newAmounts
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeReactionTypes(newTypes: List<String>) {
        reactionChoices = newTypes
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapPaymentRequest(newServer: Nip47URI?) {
        zapPaymentRequest = newServer
        live.invalidateData()
        saveable.invalidateData()
    }

    fun selectedChatsFollowList(): Set<String> {
        val contactList = userProfile().latestContactList
        return contactList?.taggedEvents()?.toSet() ?: DefaultChannels
    }

    fun sendChangeChannel(name: String, about: String, picture: String, channel: Channel) {
        if (!isWriteable()) return

        ChannelMetadataEvent.create(
            name,
            about,
            picture,
            originalChannelIdHex = channel.idHex,
            signer = signer
        ) {
            Client.send(it)
            LocalCache.justConsume(it, null)

            follow(channel)
        }
    }

    fun unwrap(event: GiftWrapEvent, onReady: (Event) -> Unit) {
        if (!isWriteable()) return

        return event.cachedGift(signer, onReady)
    }

    fun unseal(event: SealedGossipEvent, onReady: (Event) -> Unit) {
        if (!isWriteable()) return

        return event.cachedGossip(signer, onReady)
    }

    fun cachedDecryptContent(note: Note): String? {
        val event = note.event
        return if (event is PrivateDmEvent && isWriteable()) {
            event.cachedContentFor(signer)
        } else if (event is LnZapRequestEvent && event.isPrivateZap() && isWriteable()) {
            event.cachedPrivateZap()?.content
        } else {
            event?.content()
        }
    }

    fun decryptContent(note: Note, onReady: (String) -> Unit) {
        val event = note.event
        if (event is PrivateDmEvent && isWriteable()) {
            event.plainContent(signer, onReady)
        } else if (event is LnZapRequestEvent) {
            decryptZapContentAuthor(note) {
                onReady(it.content)
            }
        } else {
            event?.content()?.let {
                onReady(it)
            }
        }
    }

    fun decryptZapContentAuthor(note: Note, onReady: (Event) -> Unit) {
        val event = note.event
        if (event is LnZapRequestEvent) {
            if (event.isPrivateZap()) {
                if (isWriteable()) {
                    event.decryptPrivateZap(signer) {
                        onReady(it)
                    }
                }
            } else {
                onReady(event)
            }
        }
    }

    fun addDontTranslateFrom(languageCode: String) {
        dontTranslateFrom = dontTranslateFrom.plus(languageCode)
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun updateTranslateTo(languageCode: String) {
        translateTo = languageCode
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun prefer(source: String, target: String, preference: String) {
        languagePreferences = languagePreferences + Pair("$source,$target", preference)
        saveable.invalidateData()
    }

    fun preferenceBetween(source: String, target: String): String? {
        return languagePreferences.get("$source,$target")
    }

    private fun updateContactListTo(newContactList: ContactListEvent?) {
        if (newContactList == null || newContactList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupContactList?.id != newContactList.id) {
            backupContactList = newContactList
            saveable.invalidateData()
        }
    }

    // Takes a User's relay list and adds the types of feeds they are active for.
    fun activeRelays(): Array<Relay>? {
        var usersRelayList = userProfile().latestContactList?.relays()?.map {
            val localFeedTypes = localRelays.firstOrNull() { localRelay -> localRelay.url == it.key }?.feedTypes
                ?: Constants.defaultRelays.filter { defaultRelay -> defaultRelay.url == it.key }.firstOrNull()?.feedTypes
                ?: FeedType.values().toSet()

            Relay(it.key, it.value.read, it.value.write, localFeedTypes, proxy)
        } ?: return null

        // Ugly, but forces nostr.band as the only search-supporting relay today.
        // TODO: Remove when search becomes more available.
        val searchRelays = usersRelayList.filter { it.url.removeSuffix("/") in Constants.forcedRelaysForSearchSet }
        val hasSearchRelay = usersRelayList.any { it.activeTypes.contains(FeedType.SEARCH) }
        if (!hasSearchRelay && searchRelays.isEmpty()) {
            usersRelayList = usersRelayList + Constants.forcedRelayForSearch.map {
                Relay(
                    it.url,
                    it.read,
                    it.write,
                    it.feedTypes,
                    proxy
                )
            }
        }

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<Relay> {
        return localRelays.map {
            Relay(it.url, it.read, it.write, it.feedTypes, proxy)
        }.toTypedArray()
    }

    fun activeGlobalRelays(): Array<String> {
        return (activeRelays() ?: convertLocalRelays()).filter { it.activeTypes.contains(FeedType.GLOBAL) }
            .map { it.url }
            .toTypedArray()
    }

    fun activeWriteRelays(): List<Relay> {
        return (activeRelays() ?: convertLocalRelays()).filter { it.write }
    }

    fun reconnectIfRelaysHaveChanged() {
        val newRelaySet = activeRelays() ?: convertLocalRelays()
        if (!Client.isSameRelaySetConfig(newRelaySet)) {
            Client.disconnect()
            Client.connect(newRelaySet)
            RelayPool.requestAndWatch()
        }
    }

    fun isAllHidden(users: Set<HexKey>): Boolean {
        return users.all { isHidden(it) }
    }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean {
        return flowHiddenUsers.value.hiddenUsers.contains(userHex) || flowHiddenUsers.value.spammers.contains(userHex)
    }

    fun followingKeySet(): Set<HexKey> {
        return userProfile().cachedFollowingKeySet()
    }

    fun followingTagSet(): Set<HexKey> {
        return userProfile().cachedFollowingTagSet()
    }

    fun isAcceptable(user: User): Boolean {
        if (userProfile().pubkeyHex == user.pubkeyHex) {
            return true
        }

        if (user.pubkeyHex in followingKeySet()) {
            return true
        }

        if (!warnAboutPostsWithReports) {
            return !isHidden(user) && // if user hasn't hided this author
                user.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }
        return !isHidden(user) && // if user hasn't hided this author
            user.reportsBy(userProfile()).isEmpty() && // if user has not reported this post
            user.countReportAuthorsBy(followingKeySet()) < 5
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!warnAboutPostsWithReports) {
            return !note.hasReportsBy(userProfile())
        }
        return !note.hasReportsBy(userProfile()) && // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < 5 // if it has 5 reports by reliable users
    }

    fun isFollowing(user: User): Boolean {
        return user.pubkeyHex in followingKeySet()
    }

    fun isFollowing(user: HexKey): Boolean {
        return user in followingKeySet()
    }

    fun isAcceptable(note: Note): Boolean {
        return note.author?.let { isAcceptable(it) } ?: true && // if user hasn't hided this author
            isAcceptableDirect(note) &&
            (
                (note.event !is RepostEvent && note.event !is GenericRepostEvent) ||
                    (note.replyTo?.firstOrNull { isAcceptableDirect(it) } != null)
                ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val followsPlusMe = userProfile().latestContactList?.verifiedFollowKeySetAndMe ?: emptySet()

        val innerReports = if (note.event is RepostEvent || note.event is GenericRepostEvent) {
            note.replyTo?.map { getRelevantReports(it) }?.flatten() ?: emptyList()
        } else {
            emptyList()
        }

        return (
            note.reportsBy(followsPlusMe) +
                (
                    note.author?.reportsBy(followsPlusMe) ?: emptyList()
                    ) + innerReports
            ).toSet()
    }

    suspend fun saveRelayList(value: List<RelaySetupInfo>) {
        try {
            localRelays = value.toSet()
            return sendNewRelayList(value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) })
        } finally {
            saveable.invalidateData()
        }
    }

    fun setHideDeleteRequestDialog() {
        hideDeleteRequestDialog = true
        saveable.invalidateData()
    }

    fun setHideNIP24WarningDialog() {
        hideNIP24WarningDialog = true
        saveable.invalidateData()
    }

    fun setHideBlockAlertDialog() {
        hideBlockAlertDialog = true
        saveable.invalidateData()
    }

    fun updateShowSensitiveContent(show: Boolean?) {
        showSensitiveContent = show
        saveable.invalidateData()
        live.invalidateData()
    }

    fun markAsRead(route: String, timestampInSecs: Long): Boolean {
        val lastTime = lastReadPerRoute[route]
        return if (lastTime == null || timestampInSecs > lastTime) {
            lastReadPerRoute = lastReadPerRoute + Pair(route, timestampInSecs)
            saveable.invalidateData()
            true
        } else {
            false
        }
    }

    fun loadLastRead(route: String): Long {
        return lastReadPerRoute[route] ?: 0
    }

    suspend fun registerObservers() = withContext(Dispatchers.Main) {
        // Observes relays to restart connections
        userProfile().live().relays.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                reconnectIfRelaysHaveChanged()
            }
        }

        // saves contact list for the next time.
        userProfile().live().follows.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                updateContactListTo(userProfile().latestContactList)
            }
        }

        // imports transient blocks due to spam.
        LocalCache.antiSpam.liveSpam.observeForever {
            GlobalScope.launch(Dispatchers.IO) {
                it.cache.spamMessages.snapshot().values.forEach {
                    if (it.pubkeyHex !in transientHiddenUsers && it.duplicatedMessages.size >= 5) {
                        if (it.pubkeyHex != userProfile().pubkeyHex && it.pubkeyHex !in followingKeySet()) {
                            transientHiddenUsers = (transientHiddenUsers + it.pubkeyHex).toImmutableSet()
                            live.invalidateData()
                        }
                    }
                }
            }
        }
    }

    init {
        Log.d("Init", "Account")
        backupContactList?.let {
            println("Loading saved contacts ${it.toJson()}")

            if (userProfile().latestContactList == null) {
                GlobalScope.launch(Dispatchers.IO) {
                    LocalCache.consume(it)
                }
            }
        }
    }
}

class AccountLiveData(private val account: Account) : LiveData<AccountState>(AccountState(account)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    fun invalidateData() {
        bundler.invalidate() {
            if (hasActiveObservers()) {
                refresh()
            }
        }
    }

    fun refresh() {
        postValue(AccountState(account))
    }
}

@Immutable
class AccountState(val account: Account)
