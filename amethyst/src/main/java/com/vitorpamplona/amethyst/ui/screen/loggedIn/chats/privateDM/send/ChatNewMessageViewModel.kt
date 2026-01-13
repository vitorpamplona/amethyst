/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.compose.currentWord
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.compose.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.EmojiSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.ui.note.creators.previews.PreviewState
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.IZapRaiser
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.toZapSplitSetup
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileSender
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileUploader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrEventUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class ChatNewMessageViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IZapField,
    IZapRaiser {
    val draftTag = DraftTagState()

    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                // don't save the first
                if (it > 0) {
                    accountViewModel.launchSigner {
                        sendDraftSync()
                    }
                }
            }
        }
    }

    var room: ChatroomKey? by mutableStateOf(null)

    var requiresNIP17: Boolean = false

    val replyTo = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)
    val iMetaAttachments = IMetaAttachments()

    var uploadsWaitingToBeSent by mutableStateOf<List<SuccessfulUploads>>(emptyList())

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    var toUsers by mutableStateOf(TextFieldValue(""))
    var subject by mutableStateOf(TextFieldValue(""))

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    override val forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override val forwardZapToEditting = mutableStateOf(TextFieldValue(""))

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationState.LocationResult>? = null

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    override var zapRaiserAmount = mutableStateOf<Long?>(null)

    var expirationDays by mutableStateOf<Int?>(null)

    // NIP17 Wrapped DMs / Group messages
    var nip17 by mutableStateOf(false)

    fun lnAddress(): String? = account.userProfile().info?.lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().info?.lnAddress() != null

    fun user(): User? = account.userProfile()

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM.account)

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account)

        this.uploadState =
            ChatFileUploadState(
                account.settings.defaultFileServer,
            )
    }

    fun load(room: ChatroomKey) {
        this.room = room
        this.toUsers =
            TextFieldValue(
                room.users.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
            )

        updateNIP17StatusFromRoom()
    }

    fun updateNIP17StatusFromRoom() {
        val room = this.room
        if (room != null) {
            this.requiresNIP17 = room.users.size > 1
            if (this.requiresNIP17) {
                this.nip17 = true
            }
        } else {
            this.requiresNIP17 = false
            this.nip17 = false
        }
    }

    fun reply(replyNote: Note) {
        replyTo.value = replyNote
        draftTag.newVersion()
    }

    fun clearReply() {
        replyTo.value = null
        draftTag.newVersion()
    }

    fun quote(quote: Note) {
        message = TextFieldValue(message.text + "\nnostr:${quote.toNEvent()}")
        urlPreviews.update(message)

        // creates a split with that author.
        val quotedAuthor = quote.author ?: return

        if (quotedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
            if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedAuthor.pubkeyHex }) {
                forwardZapTo.value.addItem(quotedAuthor)
            }
            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) {
                forwardZapTo.value.addItem(accountViewModel.userProfile())
            }

            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedAuthor.pubkeyHex }
            forwardZapTo.value.updatePercentage(pos, 0.9f)

            wantsForwardZapTo = true
        }
    }

    fun editFromDraft(draft: Note) {
        val noteEvent = draft.event
        val noteAuthor = draft.author

        if (noteEvent is DraftWrapEvent && noteAuthor != null) {
            viewModelScope.launch(Dispatchers.IO) {
                accountViewModel.createTempDraftNote(noteEvent)?.let { innerNote ->
                    val oldTag = (draft.event as? AddressableEvent)?.dTag()
                    if (oldTag != null) {
                        draftTag.set(oldTag)
                    }
                    loadFromDraft(innerNote)
                }
            }
        }
    }

    fun loadExpiration(expirationDays: Int) {
        this.expirationDays = expirationDays
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return

        val localForwardZapTo = draftEvent.tags.zapSplitSetup()
        val totalWeight = localForwardZapTo.sumOf { it.weight }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            if (it is ZapSplitSetup) {
                val user = LocalCache.getOrCreateUser(it.pubKeyHex)
                forwardZapTo.value.addItem(user, (it.weight / totalWeight).toFloat())
            }
            // don't support editing old-style splits.
        }
        forwardZapToEditting.value = TextFieldValue("")
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapraiser = zapraiser != null
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        draftEvent.subject()?.let {
            subject = TextFieldValue()
        }

        if (draftEvent is NIP17Group) {
            toUsers =
                TextFieldValue(
                    draftEvent.groupMembers().mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
                )

            val replyId =
                when (draftEvent) {
                    is ChatMessageEvent -> draftEvent.replyTo().lastOrNull()
                    is ChatMessageEncryptedFileHeaderEvent -> draftEvent.replyTo().lastOrNull()
                    else -> null
                }

            if (replyId != null) {
                replyTo.value = accountViewModel.checkGetOrCreateNote(replyId)
            }
        } else if (draftEvent is PrivateDmEvent) {
            val recipientNPub = draftEvent.verifiedRecipientPubKey()?.let { Hex.decode(it).toNpub() }
            toUsers = TextFieldValue("@$recipientNPub")

            val replyId = draftEvent.replyTo()
            if (replyId != null) {
                replyTo.value = accountViewModel.checkGetOrCreateNote(replyId)
            }
        }

        message =
            if (draftEvent is PrivateDmEvent) {
                TextFieldValue(accountViewModel.account.privateDMDecryptionCache.cachedDM(draftEvent) ?: "")
            } else {
                TextFieldValue(draftEvent.content)
            }
        urlPreviews.update(message)

        expirationDays = draftEvent.expiration()?.let { (it / 86_400).toInt() }

        iMetaAttachments.addAll(draftEvent.imetas())

        requiresNIP17 = draftEvent is NIP17Group
        nip17 = draftEvent is NIP17Group
    }

    suspend fun sendPostSync() {
        val version = draftTag.current
        innerSendPost(null)
        cancel()
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.isBlank()) {
            account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            innerSendPost(draftTag.current)
        }
    }

    fun pickedMedia(list: ImmutableList<SelectedMedia>) {
        uploadState?.load(list)
    }

    override fun locationFlow(): StateFlow<LocationState.LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    fun uploadAndHold(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        val uploadState = uploadState ?: return

        accountViewModel.launchSigner {
            if (nip17) {
                ChatFileUploader(account).justUploadNIP17(uploadState, onError, context) {
                    uploadsWaitingToBeSent += it
                    draftTag.newVersion()
                    onceUploaded()
                }
            } else {
                ChatFileUploader(account).justUploadNIP04(uploadState, onError, context) {
                    uploadsWaitingToBeSent += it
                    draftTag.newVersion()
                    onceUploaded()
                }
            }
        }
    }

    fun uploadAndSend(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        val room = room ?: return
        val uploadState = uploadState ?: return

        accountViewModel.launchSigner {
            if (nip17) {
                ChatFileUploader(account).justUploadNIP17(uploadState, onError, context) {
                    ChatFileSender(room, account).sendNIP17(it)
                    draftTag.newVersion()
                    onceUploaded()
                }
            } else {
                ChatFileUploader(account).justUploadNIP04(uploadState, onError, context) {
                    ChatFileSender(room, account).sendNIP04(it)
                    draftTag.newVersion()
                    onceUploaded()
                }
            }
        }
    }

    private suspend fun innerSendPost(draftTag: String?) {
        val room = room ?: return

        val urls = findURLs(message.text)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())
        val emojis = findEmoji(message.text, accountViewModel.account.emoji.myEmojis.value)
        val geoHash = if (wantsToAddGeoHash) (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString() else null
        val message = message.text

        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null
        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount.value else null
        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val expiration = expirationDays?.let { TimeUtils.now() + it.toLong() * 86_400 }

        if (nip17 || room.users.size > 1 || replyTo.value?.event is NIP17Group) {
            val replyHint = replyTo.value?.toEventHint<BaseDMGroupEvent>()

            val template =
                if (replyHint == null) {
                    ChatMessageEvent.build(message, room.users.map { LocalCache.getOrCreateUser(it).toPTag() }) {
                        hashtags(findHashtags(message))
                        references(findURLs(message))
                        quotes(findNostrEventUris(message))

                        geoHash?.let { geohash(it) }
                        localZapRaiserAmount?.let { zapraiser(it) }
                        zapReceiver?.let { zapSplits(it) }
                        contentWarningReason?.let { contentWarning(it) }
                        expiration?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    ChatMessageEvent.reply(message, replyHint) {
                        hashtags(findHashtags(message))
                        references(findURLs(message))
                        quotes(findNostrEventUris(message))

                        geoHash?.let { geohash(it) }
                        localZapRaiserAmount?.let { zapraiser(it) }
                        zapReceiver?.let { zapSplits(it) }
                        contentWarningReason?.let { contentWarning(it) }
                        expiration?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }

            if (draftTag != null) {
                accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag, template)
            } else {
                accountViewModel.account.sendNip17PrivateMessage(template)
            }
        } else {
            val toUser = room.users.first().let { LocalCache.getOrCreateUser(it).toPTag() }

            val template =
                PrivateDmEvent.build(
                    toUser = toUser,
                    message = message,
                    imetas = usedAttachments,
                    replyingTo = replyTo.value?.toEId(),
                    signer = accountViewModel.account.signer,
                ) {
                    expiration?.let { expiration(it) }
                }

            if (draftTag != null) {
                accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag, template)
            } else {
                accountViewModel.account.sendNip04PrivateMessage(template)
            }
        }

        if (draftTag == null) {
            ChatFileSender(room, accountViewModel.account).sendAll(uploadsWaitingToBeSent)
        }
    }

    fun findEmoji(
        message: String,
        myEmojiSet: List<EmojiPackState.EmojiMedia>?,
    ): List<EmojiUrlTag> {
        if (myEmojiSet == null) return emptyList()
        return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
            myEmojiSet.firstOrNull { it.code == possibleEmoji }?.let { EmojiUrlTag(it.code, it.link) }
        }
    }

    fun cancel() {
        draftTag.rotate()

        message = TextFieldValue("")
        subject = TextFieldValue("")

        replyTo.value = null
        expirationDays = null

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.value = TextFieldValue("")

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        uploadsWaitingToBeSent = emptyList()

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    override fun updateMessage(newMessage: TextFieldValue) {
        message = newMessage
        urlPreviews.update(newMessage)

        if (message.selection.collapsed) {
            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE

            val lastWord = message.currentWord()
            userSuggestions?.processCurrentWord(lastWord)
            emojiSuggestions?.processCurrentWord(lastWord)
        }

        draftTag.newVersion()
    }

    fun updateToUsers(newToUsersValue: TextFieldValue) {
        toUsers = newToUsersValue

        if (newToUsersValue.selection.collapsed) {
            val lastWord = newToUsersValue.currentWord()
            userSuggestionsMainMessage = UserSuggestionAnchor.TO_USERS
            userSuggestions?.processCurrentWord(lastWord)
        }

        // updateRoomFromUsersInput()

        draftTag.newVersion()
    }

    fun updateRoomFromUsersInput() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val toUsersTagger = NewMessageTagger(toUsers.text, null, null, accountViewModel)
            toUsersTagger.run()

            val users = toUsersTagger.pTags?.mapTo(mutableSetOf()) { it.pubkeyHex }
            if (users == null || users.isEmpty()) {
                room = null
                updateNIP17StatusFromRoom()
            } else {
                if (users != room?.users) {
                    room = ChatroomKey(users)
                    updateNIP17StatusFromRoom()
                }
            }
        }
    }

    fun updateSubject(it: TextFieldValue) {
        subject = it
        draftTag.newVersion()
    }

    override fun updateZapForwardTo(newZapForwardTo: TextFieldValue) {
        forwardZapToEditting.value = newZapForwardTo
        if (newZapForwardTo.selection.collapsed) {
            val lastWord = newZapForwardTo.text
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                message = userSuggestions.replaceCurrentWord(message, lastWord, item)
                urlPreviews.update(message)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.value = TextFieldValue("")
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.TO_USERS) {
                val lastWord = toUsers.currentWord()
                toUsers = userSuggestions.replaceCurrentWord(toUsers, lastWord, item)
                updateRoomFromUsersInput()

                val relayList = (LocalCache.getAddressableNoteIfExists(AdvertisedRelayListEvent.createAddressTag(item.pubkeyHex))?.event as? AdvertisedRelayListEvent)?.readRelaysNorm()
                nip17 = relayList != null
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = ":${item.code}:"

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link) {
                Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForImage(item.link)
            }
        }

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            uploadState?.isUploadingImage != true &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount.value != null) &&
            (toUsers.text.isNotBlank()) &&
            uploadState?.multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
        urlPreviews.update(message)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }

    fun toggleNIP04And24() {
        if (requiresNIP17) {
            nip17 = true
        } else {
            nip17 = !nip17
        }

        draftTag.newVersion()
    }

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)

        draftTag.newVersion()
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger = NewMessageTagger(message.text, emptyList(), emptyList(), accountViewModel)
            tagger.run()
            tagger.pTags?.forEach { taggedUser ->
                if (!forwardZapTo.value.items.any { it.key == taggedUser }) {
                    forwardZapTo.value.addItem(taggedUser)
                }
            }
        }
    }

    override fun updateZapRaiserAmount(newAmount: Long?) {
        zapRaiserAmount.value = newAmount
        draftTag.newVersion()
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
