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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.publicMessages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.model.composer.IZapRaiser
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.PreviewState
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import com.vitorpamplona.amethyst.commons.model.composer.toZapSplitSetup
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerPostOptions
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.AttachedMediaState
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawingIntoText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.tags.ReceiverTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class NewPublicMessageViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IComposerMediaTools,
    IComposerPostOptions,
    IZapField,
    IZapRaiser,
    IExpiration {
    val draftTag = DraftTagState()

    // Strong reference to the live cache note for the current draft tag (derived from the
    // versions flow below), so LocalCache cannot weakly collect it before a deletion needs it.
    var draftNote: AddressableNote? = null
        private set

    override lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                // don't save the first
                if (it > 0) {
                    draftNote = account.getOrCreateDraftNote(draftTag.current)
                    accountViewModel.launchSigner {
                        sendDraftSync()
                    }
                }
            }
        }
    }

    var replyingTo: Note? by mutableStateOf(null)

    val iMetaAttachments = IMetaAttachments()

    override val message = TextFieldState()

    val urlPreviews = PreviewState()

    override val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    val toUsers = TextFieldState()

    // Images and Videos

    /** The media attached to [message]: picked, in the text as markers, uploaded on Post. */
    val media = AttachedMediaState(message)

    /** The files just picked, open in the media studio. */
    var multiOrchestrator: MultiOrchestrator?
        get() = media.picking
        set(value) {
            media.picking = value
        }

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // Invoices
    override var canAddInvoice by mutableStateOf(false)
    override var wantsInvoice by mutableStateOf(false)

    override var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    override var wantsForwardZapTo by mutableStateOf(false)
    override val forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override val forwardZapToEditting = TextFieldState()

    // NSFW, Sensitive
    override var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    override var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    override var wantsToAddGeoHash by mutableStateOf(false)
    override var pickedGeoHash by mutableStateOf<String?>(null)
    var location: StateFlow<LocationResult>? = null

    // ZapRaiser
    override var canAddZapRaiser by mutableStateOf(false)
    override var wantsZapRaiser by mutableStateOf(false)
    override var zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    override fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM.account, accountVM.nip05ClientBuilder())

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)
    }

    fun load(users: Set<HexKey>) {
        val userSet = users - account.userProfile().pubkeyHex

        toUsers.setTextAndPlaceCursorAtEnd(
            userSet.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
        )
    }

    fun reply(post: Note) {
        this.replyingTo = post
    }

    fun quote(quote: Note) {
        message.setTextAndPlaceCursorAtEnd(message.text.toString() + "\nnostr:${quote.toNEvent()}")
        urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))

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
                        draftNote = account.getOrCreateDraftNote(oldTag)
                    }
                    onUiThread { loadFromDraft(innerNote) }
                }
            }
        }
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event as? PublicMessageEvent ?: return

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
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapraiser != null
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        val userSet = draftEvent.groupKeys() - account.userProfile().pubkeyHex

        toUsers.setTextAndPlaceCursorAtEnd(
            userSet.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
        )

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)
        urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))

        iMetaAttachments.addAll(draftEvent.imetas())
    }

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    /**
     * Uploads the attached files, then publishes. False when an upload failed: nothing was posted,
     * the text and files are as they were, and the failed file says why; Post tries again.
     */
    suspend fun sendPostSync(): Boolean {
        val uploaded =
            media.uploadAll(
                defaultServer = account.settings.defaultFileServer,
                account = account,
                uploader = accountViewModel.mediaUploader,
                tracker = mediaUploadTracker,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                forcedSigner = null,
            ) { result, options ->
                iMetaAttachments.add(result, options.altOrNull, options.contentWarningReason)
                result.url
            }
        if (!uploaded) return false
        publishPostSync()
        return true
    }

    private suspend fun publishPostSync() {
        val template = createTemplate()

        val draftToDelete = draftNote
        onUiThread { cancel() }

        accountViewModel.account.signAndComputeBroadcast(template)
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    suspend fun sendDraftSync() {
        if (AttachedMediaState.stripMarkers(message.text.toString()).isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val template = createTemplate()
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    private suspend fun createTemplate(): EventTemplate<PublicMessageEvent> {
        val toUsersTagger = NewMessageTagger(this@NewPublicMessageViewModel.toUsers.text.toString(), null, null, accountViewModel)
        toUsersTagger.run()

        val messageText = AttachedMediaState.stripMarkers(message.text.toString())
        val msg =
            replyingTo?.let {
                it.toNostrUri() + "\n" + messageText
            } ?: messageText

        val tagger = NewMessageTagger(msg, null, null, accountViewModel)
        tagger.run()

        val users = (toUsersTagger.pTags ?: emptyList()) + (tagger.pTags ?: emptyList())
        val toUsers = users.mapTo(mutableSetOf()) { ReceiverTag(it.pubkeyHex, it.bestRelayHint()) }

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = (if (wantsToAddGeoHash) pickedGeoHash else null) ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        iMetaAttachments.awaitLinkedImages()
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        return PublicMessageEvent.build(
            to = toUsers.toList(),
            msg = tagger.message,
        ) {
            hashtags(findHashtags(tagger.message))
            references(findURLs(tagger.message))
            quotes(findNostrUris(tagger.message))

            geoHash?.let { geohash(it) }
            localZapRaiserAmount?.let { zapraiser(it) }
            zapReceiver?.let { zapSplits(it) }
            contentWarningReason?.let { contentWarning(it) }
            localExpirationDate?.let { expiration(it) }

            emojis(emojis)
            imetas(usedAttachments)
        }
    }

    /** Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the message. */
    override fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            uploadDrawingIntoText(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, onError, iMetaAttachments, message, urlPreviews)
        }
    }

    fun cancel() {
        draftTag.rotate()

        toUsers.setTextAndPlaceCursorAtEnd("")
        message.setTextAndPlaceCursorAtEnd("")
        media.clear()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.clearText()

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        mediaUploadTracker.finishUpload()

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    override fun onMessageChanged() {
        urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestionsMainMessage = null
                userSuggestions?.reset()
            }

            emojiSuggestions?.processCurrentWord(lastWord)
        }

        draftTag.newVersion()
    }

    fun onToUsersChanged() {
        if (toUsers.selection.collapsed) {
            val lastWord = toUsers.currentWord()
            userSuggestionsMainMessage = UserSuggestionAnchor.TO_USERS
            userSuggestions?.processCurrentWord(lastWord)
        }

        draftTag.newVersion()
    }

    override fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            when (userSuggestionsMainMessage) {
                UserSuggestionAnchor.MAIN_MESSAGE -> {
                    val lastWord = message.currentWord()
                    userSuggestions.replaceCurrentWord(message, lastWord, item)
                    urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))
                }

                UserSuggestionAnchor.FORWARD_ZAPS -> {
                    forwardZapTo.value.addItem(item)
                    forwardZapToEditting.clearText()
                }

                UserSuggestionAnchor.TO_USERS -> {
                    val lastWord = toUsers.currentWord()
                    userSuggestions.replaceCurrentWord(toUsers, lastWord, item)
                }

                else -> {}
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
        urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))

        draftTag.newVersion()
    }

    fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(AttachedMediaState.stripMarkers(message.text.toString()))

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !mediaUploadTracker.isUploading &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            (toUsers.text.isNotBlank()) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) = media.removePicked(selected)

    override fun selectImage(uris: ImmutableList<SelectedMedia>) = media.pick(uris, stripMetadata = account.settings.stripLocationOnUpload)

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
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
            val tagger = NewMessageTagger(AttachedMediaState.stripMarkers(message.text.toString()), emptyList(), emptyList(), accountViewModel)
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

    override fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    override fun toggleExpirationDate() {
        wantsExpirationDate = !wantsExpirationDate
        if (wantsExpirationDate) {
            expirationDate = TimeUtils.oneDayAhead()
        }
        draftTag.newVersion()
    }

    override fun locationManager(): DeviceLocation = accountViewModel.host.deviceLocation
}
