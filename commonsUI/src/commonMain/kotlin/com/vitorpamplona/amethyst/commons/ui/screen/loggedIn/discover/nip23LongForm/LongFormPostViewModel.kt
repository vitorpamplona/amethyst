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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip23LongForm

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.model.composer.IZapRaiser
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import com.vitorpamplona.amethyst.commons.model.composer.toZapSplitSetup
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
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
import com.vitorpamplona.amethyst.commons.ui.text.appendSignature
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawingIntoText
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@Stable
class LongFormPostViewModel :
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
                    val tag = draftTag.current
                    draftNote = account.getOrCreateDraftNote(tag)
                    accountViewModel.launchSigner {
                        // Post rotates the tag and then clears the composer. A save still queued from
                        // before that would see the empty text and delete the draft Post just saved
                        // for the post that is still mining, so it's skipped once the tag has moved on.
                        if (draftTag.current == tag) sendDraftSync()
                    }
                }
            }
        }
    }

    // The signature pre-filled by applySignature(), so an untouched signature-only
    // body is treated as blank instead of auto-saved as a junk draft.
    private var appliedSignature: String? = null

    var title by mutableStateOf(TextFieldValue(""))
    var summary by mutableStateOf(TextFieldValue(""))
    var coverImageUrl by mutableStateOf("")
    var publishedAt by mutableLongStateOf(TimeUtils.now())
    var tags by mutableStateOf(listOf<String>())
    var slug by mutableStateOf("")

    var isUploadingCoverImage by mutableStateOf(false)

    override val message = TextFieldState()

    var showPreview by mutableStateOf(false)

    val iMetaAttachments = IMetaAttachments()

    override val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

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

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Invoices
    override var canAddInvoice by mutableStateOf(false)
    override var wantsInvoice by mutableStateOf(false)

    override var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    override var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
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
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    override var canAddZapRaiser by mutableStateOf(false)
    override var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    override fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    // Editing existing article
    var existingDTag: String? = null

    val isEditing: Boolean get() = existingDTag != null

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

    fun load(
        draft: Note?,
        version: Note?,
    ) {
        val noteEvent = draft?.event
        val noteAuthor = draft?.author

        if (draft != null && noteEvent is DraftWrapEvent && noteAuthor != null) {
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
        } else {
            val noteEvent = version?.event

            if (noteEvent is LongFormContentEvent) {
                title = TextFieldValue(noteEvent.title() ?: "")
                summary = TextFieldValue(noteEvent.summary() ?: "")
                publishedAt = noteEvent.publishedAt() ?: noteEvent.createdAt
                coverImageUrl = noteEvent.image() ?: ""
                message.setTextAndPlaceCursorAtEnd(noteEvent.content)
                existingDTag = noteEvent.dTag()
                tags = noteEvent.topics()
                slug = noteEvent.dTag()
            }

            val user = account.userProfile()

            canAddInvoice = user.lnAddress() != null
            canAddZapRaiser = user.lnAddress() != null
            media.clear()
        }
    }

    /**
     * Pre-fills the signature from Compose Settings at the end of the article body. Skipped
     * by callers when loading a draft or editing an existing version (the content already
     * carries — or deliberately omits — a signature).
     */
    fun applySignature() {
        val signature = accountViewModel.composeSignature()
        if (signature.isEmpty()) return

        appliedSignature = signature
        message.appendSignature(signature)
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent is LongFormContentEvent) {
            loadFromDraft(draftEvent)
        }
    }

    private fun loadFromDraft(draftEvent: LongFormContentEvent) {
        title = TextFieldValue(draftEvent.title() ?: "")
        summary = TextFieldValue(draftEvent.summary() ?: "")
        publishedAt = draftEvent.publishedAt() ?: draftEvent.createdAt
        coverImageUrl = draftEvent.image() ?: ""
        message.setTextAndPlaceCursorAtEnd(draftEvent.content)
        existingDTag = draftEvent.dTag()
        tags = draftEvent.topics()
        slug = draftEvent.dTag()

        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().lnAddress() != null
        media.clear()

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.tags.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapRaiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapRaiser != null
        zapRaiserAmount.value = null
        if (zapRaiser != null) {
            zapRaiserAmount.value = zapRaiser
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        iMetaAttachments.addAll(draftEvent.imetas())
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
        val template = createTemplate() ?: return

        // A mined post leaves the phone minutes after this returns — much later if the
        // app is backgrounded and frozen — and the draft is its only user-visible copy
        // until then. The auto-save is debounced and cancel() below drops the pending
        // save, so the last second of typing would never reach it: flush it now.
        if (accountViewModel.account.powDifficultyFor(template.kind) != null) {
            draftNote = account.getOrCreateDraftNote(draftTag.current)
            sendDraftSync()
        }

        val draftToDelete = draftNote
        onUiThread { cancel() }

        // Draft deletion runs INSIDE the publish continuation: when the
        // article is mined first, the draft must survive until the mined event
        // is actually signed and dispatched — a cancelled or process-killed
        // mining job would otherwise have destroyed the only copy of the text.
        accountViewModel.account.sendMined(template, PoWReplay.Broadcast()) { readyTemplate ->
            broadcastArticle(readyTemplate)
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    /**
     * The publish step shared by the direct and post-mining paths. Tracked
     * broadcasting is launched fire-and-forget on the account scope — the
     * composer must not wait for relay acks before navigating away, and a
     * mined job must not hold its queue entry while acks trickle in. Runs on
     * the mining queue's scope when PoW is on, so it must not touch
     * viewModelScope.
     */
    private suspend fun broadcastArticle(template: EventTemplate<out Event>) {
        if (accountViewModel.settings.useTrackedBroadcasts()) {
            val (event, relays, extras) = accountViewModel.account.createPostEvent(template, emptyList())
            accountViewModel.account.scope.launch {
                accountViewModel.broadcastTracker.trackBroadcast(
                    event = event,
                    relays = relays,
                    client = accountViewModel.account.client,
                )
                accountViewModel.account.consumePostEvent(event, relays, extras)
            }
        } else {
            accountViewModel.account.signAndComputeBroadcast(template)
        }
    }

    suspend fun sendDraftSync() {
        val text = AttachedMediaState.stripMarkers(message.text.toString())
        if ((text.isBlank() || text.trim() == appliedSignature) && title.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    /**
     * The tags the preview needs to draw the body as it will be published: the custom emoji it uses
     * and the `imeta` of the files attached to it.
     */
    suspend fun previewTags(): ImmutableListOfLists<String> {
        val text = AttachedMediaState.stripMarkers(message.text.toString())
        val emojis = account.emoji.findEmojiTags(text)
        val used = iMetaAttachments.filterIsIn(findURLs(text).toSet())
        return LongFormContentEvent
            .build(description = text, title = "", dTag = "preview") {
                emojis(emojis)
                imetas(used)
            }.tags
            .toImmutableListOfLists()
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun createTemplate(): EventTemplate<out Event>? {
        if (title.text.isBlank()) return null

        val tagger =
            NewMessageTagger(
                AttachedMediaState.stripMarkers(message.text.toString()),
                null,
                null,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = if (wantsToAddGeoHash) (pickedGeoHash ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()) else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        return LongFormContentEvent.build(
            description = tagger.message,
            title = title.text.trim(),
            summary = summary.text.trim().ifBlank { null },
            image = coverImageUrl.trim().ifBlank { null },
            publishedAt = publishedAt,
            dTag = existingDTag ?: slug.ifBlank { RandomInstance.randomChars(16) },
        ) {
            hashtags(findHashtags(tagger.message) + tags)
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

    fun uploadCoverImage(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            isUploadingCoverImage = true
            try {
                directUpload(
                    galleryUri = uri,
                    uploader = uploader,
                    onError = onError,
                )?.let {
                    coverImageUrl = it
                }
            } finally {
                isUploadingCoverImage = false
            }
        }
    }

    private suspend fun directUpload(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ): String? =
        // Cover images were never stripped: keep them as picked, only compressed.
        uploadToDefaultServer(galleryUri, account, uploader, onError, stripMetadata = false)

    /** Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the article as a markdown image. */
    override fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            uploadDrawingIntoText(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, onError, iMetaAttachments, message) { markdownImage(alt, it) }
        }
    }

    fun cancel() {
        draftTag.rotate()

        title = TextFieldValue("")
        summary = TextFieldValue("")
        coverImageUrl = ""
        message.setTextAndPlaceCursorAtEnd("")
        publishedAt = TimeUtils.now()
        showPreview = false

        existingDTag = null
        tags = emptyList()
        slug = ""

        media.clear()
        mediaUploadTracker.finishUpload()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null
        wantsExclusiveGeoPost = false
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.clearText()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    override fun onMessageChanged() {
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

    override fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                userSuggestions.replaceCurrentWord(message, lastWord, item)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.clearText()
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    fun autocompleteWithEmoji(item: EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
        draftTag.newVersion()
    }

    fun autocompleteWithEmojiUrl(item: EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)
        emojiSuggestions?.reset()
        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        title.text.isNotBlank() &&
            message.text.toString().isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) = media.removePicked(selected)

    override fun selectImage(uris: ImmutableList<SelectedMedia>) = media.pick(uris, stripMetadata = account.settings.stripLocationOnUpload)

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger =
                NewMessageTagger(AttachedMediaState.stripMarkers(message.text.toString()), emptyList(), emptyList(), accountViewModel)
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

/**
 * A markdown image of [url] described by [alt]. The alt text is the user's own words, so the
 * characters that would end or nest its brackets are escaped and line breaks become spaces: an
 * alt like `sketch](https://example.com)` stays text instead of turning into a link.
 */
fun markdownImage(
    alt: String?,
    url: String,
): String {
    val text =
        buildString {
            alt?.forEach { char ->
                when (char) {
                    '\\', '[', ']' -> append('\\').append(char)
                    '\n', '\r' -> append(' ')
                    else -> append(char)
                }
            }
        }
    return "![$text]($url)"
}
