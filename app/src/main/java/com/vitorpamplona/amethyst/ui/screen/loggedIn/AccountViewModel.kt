package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.Nip05Verifier
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.components.MarkdownParser
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.bottomNavigationItems
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.CombinedZap
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale
import kotlin.time.measureTimedValue

@Immutable
open class ToastMsg()

@Immutable
class StringToastMsg(val title: String, val msg: String) : ToastMsg()

@Immutable
class ResourceToastMsg(val titleResId: Int, val resourceId: Int) : ToastMsg()

@Stable
class AccountViewModel(val account: Account) : ViewModel(), Dao {
    val accountLiveData: LiveData<AccountState> = account.live.map { it }
    val accountLanguagesLiveData: LiveData<AccountState> = account.liveLanguages.map { it }
    val accountMarkAsReadUpdates = mutableStateOf(0)

    val userFollows: LiveData<UserState> = account.userProfile().live().follows.map { it }
    val userRelays: LiveData<UserState> = account.userProfile().live().relays.map { it }

    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val discoveryListLiveData = account.live.map {
        it.account.defaultDiscoveryFollowList
    }.distinctUntilChanged()

    val homeListLiveData = account.live.map {
        it.account.defaultHomeFollowList
    }.distinctUntilChanged()

    val notificationListLiveData = account.live.map {
        it.account.defaultNotificationFollowList
    }.distinctUntilChanged()

    val storiesListLiveData = account.live.map {
        it.account.defaultStoriesFollowList
    }.distinctUntilChanged()

    val showSensitiveContentChanges = account.live.map {
        it.account.showSensitiveContent
    }.distinctUntilChanged()

    fun clearToasts() {
        viewModelScope.launch {
            toasts.emit(null)
        }
    }

    fun toast(title: String, message: String) {
        viewModelScope.launch {
            toasts.emit(StringToastMsg(title, message))
        }
    }

    fun toast(titleResId: Int, resourceId: Int) {
        viewModelScope.launch {
            toasts.emit(ResourceToastMsg(titleResId, resourceId))
        }
    }

    fun updateAutomaticallyStartPlayback(
        automaticallyStartPlayback: ConnectivityType
    ) {
        account.updateAutomaticallyStartPlayback(automaticallyStartPlayback)
    }

    fun updateAutomaticallyShowUrlPreview(
        automaticallyShowUrlPreview: ConnectivityType
    ) {
        account.updateAutomaticallyShowUrlPreview(automaticallyShowUrlPreview)
    }

    fun updateAutomaticallyShowProfilePicture(
        automaticallyShowProfilePicture: ConnectivityType
    ) {
        account.updateAutomaticallyShowProfilePicture(automaticallyShowProfilePicture)
    }

    fun updateAutomaticallyHideNavBars(
        automaticallyHideHavBars: BooleanType
    ) {
        account.updateAutomaticallyHideHavBars(automaticallyHideHavBars)
    }

    fun updateAutomaticallyShowImages(
        automaticallyShowImages: ConnectivityType
    ) {
        account.updateAutomaticallyShowImages(automaticallyShowImages)
    }

    fun isWriteable(): Boolean {
        return account.isWriteable()
    }

    fun loggedInWithExternalSigner(): Boolean {
        return account.loginWithExternalSigner
    }

    fun userProfile(): User {
        return account.userProfile()
    }

    fun reactTo(note: Note, reaction: String) {
        account.reactTo(note, reaction)
    }

    fun reactToOrDelete(note: Note, reaction: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentReactions = account.reactionTo(note, reaction)
            if (currentReactions.isNotEmpty()) {
                account.delete(currentReactions)
            } else {
                account.reactTo(note, reaction)
            }
        }
    }

    fun reactToOrDelete(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            val reaction = account.reactionChoices.first()
            if (hasReactedTo(note, reaction)) {
                deleteReactionTo(note, reaction)
            } else {
                reactTo(note, reaction)
            }
        }
    }

    fun isNoteHidden(note: Note): Boolean {
        val isSensitive = note.event?.isSensitive() ?: false
        return account.isHidden(note.author!!) || (isSensitive && account.showSensitiveContent == false)
    }

    fun hasReactedTo(baseNote: Note, reaction: String): Boolean {
        return account.hasReacted(baseNote, reaction)
    }

    fun deleteReactionTo(note: Note, reaction: String) {
        account.delete(account.reactionTo(note, reaction))
    }

    fun hasBoosted(baseNote: Note): Boolean {
        return account.hasBoosted(baseNote)
    }

    fun deleteBoostsTo(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            account.delete(account.boostsTo(note))
        }
    }

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note, onWasZapped: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            onWasZapped(account.calculateIfNoteWasZappedByAccount(zappedNote))
        }
    }

    suspend fun calculateZapAmount(zappedNote: Note): BigDecimal {
        return account.calculateZappedAmount(zappedNote)
    }

    fun calculateZapAmount(zappedNote: Note, onZapAmount: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onZapAmount(showAmount(account.calculateZappedAmount(zappedNote)))
        }
    }

    fun calculateZapraiser(zappedNote: Note, onZapraiserStatus: (ZapraiserStatus) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
            val newZapAmount = calculateZapAmount(zappedNote)
            var percentage = newZapAmount.div(zapraiserAmount.toBigDecimal()).toFloat()

            if (percentage > 1) {
                percentage = 1f
            }

            val newZapraiserProgress = percentage
            val newZapraiserLeft = if (percentage > 0.99) {
                "0"
            } else {
                showAmount((zapraiserAmount * (1 - percentage)).toBigDecimal())
            }
            onZapraiserStatus(ZapraiserStatus(newZapraiserProgress, newZapraiserLeft))
        }
    }

    fun decryptAmountMessageInGroup(
        zaps: ImmutableList<CombinedZap>,
        onNewState: (ImmutableList<ZapAmountCommentNotification>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = ArrayList<ZapAmountCommentNotification>(zaps.size)
            zaps.forEach {
                innerDecryptAmountMessage(it.request, it.response)?.let {
                    list.add(it)
                }
            }

            onNewState(list.toImmutableList())
        }
    }

    fun decryptAmountMessageInGroup(
        baseNote: Note,
        onNewState: (ImmutableList<ZapAmountCommentNotification>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = ArrayList<ZapAmountCommentNotification>(baseNote.zaps.size)
            baseNote.zaps.forEach {
                innerDecryptAmountMessage(it.key, it.value)?.let {
                    list.add(it)
                }
            }

            onNewState(list.toImmutableList())
        }
    }

    fun decryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
        onNewState: (ZapAmountCommentNotification?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewState(innerDecryptAmountMessage(zapRequest, zapEvent))
        }
    }

    private suspend fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?
    ): ZapAmountCommentNotification? {
        checkNotInMainThread()

        (zapRequest.event as? LnZapRequestEvent)?.let {
            val decryptedContent = decryptZap(zapRequest)
            val amount = (zapEvent?.event as? LnZapEvent)?.amount
            if (decryptedContent != null) {
                val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                return ZapAmountCommentNotification(
                    newAuthor,
                    decryptedContent.content.ifBlank { null },
                    showAmountAxis(amount)
                )
            } else {
                if (!zapRequest.event?.content().isNullOrBlank() || amount != null) {
                    return ZapAmountCommentNotification(
                        zapRequest.author,
                        zapRequest.event?.content()?.ifBlank { null },
                        showAmountAxis(amount)
                    )
                }
            }
        }
        return null
    }

    fun zap(
        note: Note,
        amount: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
        zapType: LnZapEvent.ZapType
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ZapPaymentHandler(account).zap(note, amount, pollOption, message, context, onError, onProgress, onPayViaIntent, zapType)
        }
    }

    fun report(note: Note, type: ReportEvent.ReportType, content: String = "") {
        account.report(note, type, content)
    }

    fun report(user: User, type: ReportEvent.ReportType) {
        viewModelScope.launch(Dispatchers.IO) {
            account.report(user, type)
            account.hideUser(user.pubkeyHex)
        }
    }

    fun boost(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            account.boost(note)
        }
    }

    fun removeEmojiPack(usersEmojiList: Note, emojiList: Note) {
        account.removeEmojiPack(usersEmojiList, emojiList)
    }

    fun addEmojiPack(usersEmojiList: Note, emojiList: Note) {
        account.addEmojiPack(usersEmojiList, emojiList)
    }

    fun addPrivateBookmark(note: Note) {
        account.addPrivateBookmark(note)
    }

    fun addPrivateBookmark(note: Note, decryptedContent: String) {
        account.addPrivateBookmark(note, decryptedContent)
    }

    fun addPublicBookmark(note: Note, decryptedContent: String) {
        account.addPublicBookmark(note, decryptedContent)
    }

    fun removePublicBookmark(note: Note, decryptedContent: String) {
        account.removePublicBookmark(note, decryptedContent)
    }

    fun addPublicBookmark(note: Note) {
        account.addPublicBookmark(note)
    }

    fun removePrivateBookmark(note: Note, decryptedContent: String) {
        account.removePrivateBookmark(note, decryptedContent)
    }

    fun removePrivateBookmark(note: Note) {
        account.removePrivateBookmark(note)
    }

    fun removePublicBookmark(note: Note) {
        account.removePublicBookmark(note)
    }

    fun isInPrivateBookmarks(note: Note): Boolean {
        return account.isInPrivateBookmarks(note)
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        return account.isInPublicBookmarks(note)
    }

    fun broadcast(note: Note) {
        account.broadcast(note)
    }

    fun delete(note: Note) {
        account.delete(note)
    }

    fun decrypt(note: Note): String? {
        return account.decryptContent(note)
    }

    fun decryptZap(note: Note): Event? {
        return account.decryptZapContentAuthor(note)
    }

    fun translateTo(lang: Locale) {
        account.updateTranslateTo(lang.language)
    }

    fun dontTranslateFrom(lang: String) {
        account.addDontTranslateFrom(lang)
    }

    fun prefer(source: String, target: String, preference: String) {
        account.prefer(source, target, preference)
    }

    fun follow(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.follow(user)
        }
    }

    fun unfollow(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.unfollow(user)
        }
    }

    fun followGeohash(tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.followGeohash(tag)
        }
    }

    fun unfollowGeohash(tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.unfollowGeohash(tag)
        }
    }

    fun followHashtag(tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.followHashtag(tag)
        }
    }

    fun unfollowHashtag(tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.unfollowHashtag(tag)
        }
    }

    fun showWord(word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.showWord(word)
        }
    }

    fun hideWord(word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.hideWord(word)
        }
    }

    fun isLoggedUser(user: User?): Boolean {
        return account.userProfile().pubkeyHex == user?.pubkeyHex
    }

    fun isFollowing(user: User?): Boolean {
        if (user == null) return false
        return account.userProfile().isFollowingCached(user)
    }

    fun isFollowing(user: HexKey): Boolean {
        return account.userProfile().isFollowingCached(user)
    }

    val hideDeleteRequestDialog: Boolean
        get() = account.hideDeleteRequestDialog

    fun dontShowDeleteRequestDialog() {
        account.setHideDeleteRequestDialog()
    }

    val hideNIP24WarningDialog: Boolean
        get() = account.hideNIP24WarningDialog

    fun dontShowNIP24WarningDialog() {
        account.setHideNIP24WarningDialog()
    }

    val hideBlockAlertDialog: Boolean
        get() = account.hideBlockAlertDialog

    fun dontShowBlockAlertDialog() {
        account.setHideBlockAlertDialog()
    }

    fun hideSensitiveContent() {
        account.updateShowSensitiveContent(false)
    }

    fun disableContentWarnings() {
        account.updateShowSensitiveContent(true)
    }

    fun seeContentWarnings() {
        account.updateShowSensitiveContent(null)
    }

    fun defaultZapType(): LnZapEvent.ZapType {
        return account.defaultZapType
    }

    @Immutable
    data class NoteComposeReportState(
        val isAcceptable: Boolean = true,
        val canPreview: Boolean = true,
        val isHiddenAuthor: Boolean = false,
        val relevantReports: ImmutableSet<Note> = persistentSetOf()
    )

    fun isNoteAcceptable(note: Note, onReady: (NoteComposeReportState) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
            val isFromLoggedInFollow = note.author?.let { userProfile().isFollowingCached(it) } ?: true

            if (isFromLoggedIn || isFromLoggedInFollow) {
                // No need to process if from trusted people
                onReady(NoteComposeReportState(true, true, false, persistentSetOf()))
            } else if (note.author?.let { account.isHidden(it) } == true) {
                onReady(NoteComposeReportState(false, false, true, persistentSetOf()))
            } else {
                val newCanPreview = !note.hasAnyReports()

                val newIsAcceptable = account.isAcceptable(note)

                if (newCanPreview && newIsAcceptable) {
                    // No need to process reports if nothing is wrong
                    onReady(NoteComposeReportState(true, true, false, persistentSetOf()))
                } else {
                    val newRelevantReports = account.getRelevantReports(note)

                    onReady(
                        NoteComposeReportState(
                            newIsAcceptable,
                            newCanPreview,
                            false,
                            newRelevantReports.toImmutableSet()
                        )
                    )
                }
            }
        }
    }

    fun unwrap(event: GiftWrapEvent): Event? {
        return account.unwrap(event)
    }
    fun unseal(event: SealedGossipEvent): Event? {
        return account.unseal(event)
    }

    fun show(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.showUser(user.pubkeyHex)
        }
    }

    fun hide(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.hideUser(user.pubkeyHex)
        }
    }

    fun hide(word: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.hideWord(word)
        }
    }

    fun showUser(pubkeyHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.showUser(pubkeyHex)
        }
    }

    fun createStatus(newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.createStatus(newStatus)
        }
    }

    fun updateStatus(it: AddressableNote, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.updateStatus(it, newStatus)
        }
    }

    fun deleteStatus(it: AddressableNote) {
        viewModelScope.launch(Dispatchers.IO) {
            account.deleteStatus(it)
        }
    }

    fun urlPreview(url: String, onResult: suspend (UrlPreviewState) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            UrlCachedPreviewer.previewInfo(url, onResult)
        }
    }

    fun loadReactionTo(note: Note?, onNewReactionType: (String?) -> Unit) {
        if (note == null) return

        viewModelScope.launch(Dispatchers.Default) {
            onNewReactionType(note.getReactionBy(userProfile()))
        }
    }

    fun verifyNip05(userMetadata: UserMetadata, pubkeyHex: String, onResult: (Boolean) -> Unit) {
        val nip05 = userMetadata.nip05?.ifBlank { null } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            Nip05Verifier().verifyNip05(
                nip05,
                onSuccess = {
                    // Marks user as verified
                    if (it == pubkeyHex) {
                        userMetadata.nip05Verified = true
                        userMetadata.nip05LastVerificationTime = TimeUtils.now()

                        onResult(userMetadata.nip05Verified)
                    } else {
                        userMetadata.nip05Verified = false
                        userMetadata.nip05LastVerificationTime = 0

                        onResult(userMetadata.nip05Verified)
                    }
                },
                onError = {
                    userMetadata.nip05LastVerificationTime = 0
                    userMetadata.nip05Verified = false

                    onResult(userMetadata.nip05Verified)
                }
            )
        }
    }

    fun retrieveRelayDocument(
        dirtyUrl: String,
        onInfo: (RelayInformation) -> Unit,
        onError: (String, Nip11Retriever.ErrorCode, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Nip11CachedRetriever.loadRelayInfo(dirtyUrl, onInfo, onError)
        }
    }

    fun runOnIO(runOnIO: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runOnIO()
        }
    }

    suspend fun checkGetOrCreateUser(key: HexKey): User? {
        return LocalCache.checkGetOrCreateUser(key)
    }

    override suspend fun getOrCreateUser(key: HexKey): User {
        return LocalCache.getOrCreateUser(key)
    }

    fun checkGetOrCreateUser(key: HexKey, onResult: (User?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(checkGetOrCreateUser(key))
        }
    }

    fun getUserIfExists(hex: HexKey): User? {
        return LocalCache.getUserIfExists(hex)
    }

    private suspend fun checkGetOrCreateNote(key: HexKey): Note? {
        return LocalCache.checkGetOrCreateNote(key)
    }

    override suspend fun getOrCreateNote(key: HexKey): Note {
        return LocalCache.getOrCreateNote(key)
    }

    fun checkGetOrCreateNote(key: HexKey, onResult: (Note?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(checkGetOrCreateNote(key))
        }
    }

    fun getNoteIfExists(hex: HexKey): Note? {
        return LocalCache.getNoteIfExists(hex)
    }

    override suspend fun checkGetOrCreateAddressableNote(key: HexKey): AddressableNote? {
        return LocalCache.checkGetOrCreateAddressableNote(key)
    }

    fun checkGetOrCreateAddressableNote(key: HexKey, onResult: (AddressableNote?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(checkGetOrCreateAddressableNote(key))
        }
    }

    private suspend fun getOrCreateAddressableNote(key: ATag): AddressableNote? {
        return LocalCache.getOrCreateAddressableNote(key)
    }

    fun getOrCreateAddressableNote(key: ATag, onResult: (AddressableNote?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(getOrCreateAddressableNote(key))
        }
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? {
        return LocalCache.addressables[key]
    }

    fun findStatusesForUser(myUser: User, onResult: (ImmutableList<AddressableNote>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(LocalCache.findStatusesForUser(myUser))
        }
    }

    private suspend fun checkGetOrCreateChannel(key: HexKey): Channel? {
        return LocalCache.checkGetOrCreateChannel(key)
    }

    fun checkGetOrCreateChannel(key: HexKey, onResult: (Channel?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onResult(checkGetOrCreateChannel(key))
        }
    }

    fun getChannelIfExists(hex: HexKey): Channel? {
        return LocalCache.getChannelIfExists(hex)
    }

    fun loadParticipants(participants: List<Participant>, onReady: (ImmutableList<Pair<Participant, User>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val participantUsers = participants.mapNotNull { part ->
                checkGetOrCreateUser(part.key)?.let {
                    Pair(
                        part,
                        it
                    )
                }
            }.toImmutableList()

            onReady(participantUsers)
        }
    }

    fun loadUsers(hexList: List<String>, onReady: (ImmutableList<User>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onReady(
                hexList.mapNotNull { hex ->
                    checkGetOrCreateUser(hex)
                }.sortedBy { account.isFollowing(it) }.reversed().toImmutableList()
            )
        }
    }

    fun returnNIP19References(content: String, tags: ImmutableListOfLists<String>?, onNewReferences: (List<Nip19.Return>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewReferences(MarkdownParser().returnNIP19References(content, tags))
        }
    }

    fun returnMarkdownWithSpecialContent(content: String, tags: ImmutableListOfLists<String>?, onNewContent: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewContent(MarkdownParser().returnMarkdownWithSpecialContent(content, tags))
        }
    }

    fun parseNIP19(str: String, onNote: (LoadedBechLink) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            Nip19.uriToRoute(str)?.let {
                var returningNote: Note? = null
                if (it.type == Nip19.Type.NOTE || it.type == Nip19.Type.EVENT || it.type == Nip19.Type.ADDRESS) {
                    LocalCache.checkGetOrCreateNote(it.hex)?.let { note ->
                        returningNote = note
                    }
                }

                onNote(LoadedBechLink(returningNote, it))
            }
        }
    }

    fun checkIsOnline(media: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onDone(OnlineChecker.isOnline(media))
        }
    }

    fun refreshMarkAsReadObservers() {
        updateNotificationDots()
        accountMarkAsReadUpdates.value++
    }

    fun loadAndMarkAsRead(routeForLastRead: String, createdAt: Long?, onIsNew: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastTime = account.loadLastRead(routeForLastRead)

            if (createdAt != null) {
                if (account.markAsRead(routeForLastRead, createdAt)) {
                    refreshMarkAsReadObservers()
                }
                onIsNew(createdAt > lastTime)
            } else {
                onIsNew(false)
            }
        }
    }

    fun markAllAsRead(notes: ImmutableList<Note>, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var atLeastOne = false

            for (note in notes) {
                note.event?.let { noteEvent ->
                    val channelHex = note.channelHex()
                    val route = if (channelHex != null) {
                        "Channel/$channelHex"
                    } else if (note.event is ChatroomKeyable) {
                        val withKey =
                            (note.event as ChatroomKeyable).chatroomKey(userProfile().pubkeyHex)
                        "Room/${withKey.hashCode()}"
                    } else {
                        null
                    }

                    route?.let {
                        if (account.markAsRead(route, noteEvent.createdAt())) {
                            atLeastOne = true
                        }
                    }
                }
            }

            if (atLeastOne) {
                refreshMarkAsReadObservers()
            }

            onDone()
        }
    }

    fun createChatRoomFor(user: User, then: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val withKey = ChatroomKey(persistentSetOf(user.pubkeyHex))
            account.userProfile().createChatroom(withKey)
            then(withKey.hashCode())
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel {
            return AccountViewModel(account) as AccountViewModel
        }
    }

    private var collectorJob: Job? = null
    val notificationDots = HasNotificationDot(bottomNavigationItems, account)

    fun updateNotificationDots(newNotes: Set<Note> = emptySet()) {
        viewModelScope.launch(Dispatchers.Default) {
            val (value, elapsed) = measureTimedValue {
                notificationDots.update(newNotes)
            }
            Log.d("Rendering Metrics", "Notification Dots Calculation in $elapsed for ${newNotes.size} new notes")
        }
    }

    init {
        Log.d("Init", "AccountViewModel")
        collectorJob = viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                updateNotificationDots(newNotes)
            }
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        super.onCleared()
    }

    fun loadThumb(
        context: Context,
        thumbUri: String,
        onReady: (Drawable?) -> Unit,
        onError: (String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(thumbUri).build()
                val myCover = context.imageLoader.execute(request).drawable
                onReady(myCover)
            } catch (e: Exception) {
                Log.e("VideoView", "Fail to load cover $thumbUri", e)
                onError(e.message)
            }
        }
    }
}

class HasNotificationDot(bottomNavigationItems: ImmutableList<Route>, val account: Account) {
    val hasNewItems = bottomNavigationItems.associateWith { MutableStateFlow(false) }

    fun update(newNotes: Set<Note>) {
        hasNewItems.forEach {
            val newResult = it.key.hasNewItems(account, newNotes)
            if (newResult != it.value.value) {
                it.value.value = newResult
            }
        }
    }
}

@Immutable
data class LoadedBechLink(val baseNote: Note?, val nip19: Nip19.Return)
