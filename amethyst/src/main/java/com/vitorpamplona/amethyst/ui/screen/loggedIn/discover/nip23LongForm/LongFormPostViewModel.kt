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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.currentWord
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.compose.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.SuspendableConfirmation
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.MediaUploadTracker
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.EmojiSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.expiration.IExpiration
import com.vitorpamplona.amethyst.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.IZapRaiser
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.toZapSplitSetup
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi

@Stable
class LongFormPostViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IZapField,
    IZapRaiser,
    IExpiration {
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

    val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override var forwardZapToEditting = mutableStateOf(TextFieldValue(""))

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationResult>? = null
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

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
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account)
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
                    }
                    loadFromDraft(innerNote)
                }
            }
        } else {
            val noteEvent = version?.event

            if (noteEvent is LongTextNoteEvent) {
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
            multiOrchestrator = null
        }
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent is LongTextNoteEvent) {
            loadFromDraft(draftEvent)
        }
    }

    private fun loadFromDraft(draftEvent: LongTextNoteEvent) {
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
        multiOrchestrator = null

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.value = TextFieldValue("")
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.tags.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
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

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return

        val version = draftTag.current
        cancel()

        if (accountViewModel.settings.isCompleteUIMode()) {
            val (event, relays, extras) = accountViewModel.account.createPostEvent(template, emptyList())
            accountViewModel.viewModelScope.launch(Dispatchers.IO) {
                accountViewModel.broadcastTracker.trackBroadcast(
                    event = event,
                    relays = relays,
                    client = accountViewModel.account.client,
                )
                accountViewModel.account.consumePostEvent(event, relays, extras)
            }
        } else {
            accountViewModel.account.signAndComputeBroadcast(template, emptyList())
        }

        accountViewModel.launchSigner {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.toString().isBlank() && title.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, emptySet())
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun createTemplate(): EventTemplate<out Event>? {
        if (title.text.isBlank()) return null

        val tagger =
            NewMessageTagger(
                message.text.toString(),
                null,
                null,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = if (wantsToAddGeoHash) (location?.value as? LocationResult.Success)?.geoHash else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = findEmoji(tagger.message, account.emoji.myEmojis.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        return LongTextNoteEvent.build(
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

    private fun findEmoji(
        message: String,
        myEmojiSet: List<EmojiMedia>?,
    ): List<EmojiUrlTag> {
        if (myEmojiSet == null) return emptyList()
        return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
            myEmojiSet.firstOrNull { it.code == possibleEmoji }?.let { EmojiUrlTag(it.code, it.link) }
        }
    }

    fun uploadCoverImage(
        uri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            isUploadingCoverImage = true
            try {
                directUpload(
                    galleryUri = uri,
                    context = context,
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
        context: Context,
        onError: (String, String) -> Unit,
    ): String? {
        val compResult = MediaCompressor().compress(galleryUri.uri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

        return try {
            val result =
                if (account.settings.defaultFileServer.type == ServerType.NIP96) {
                    Nip96Uploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        onProgress = {},
                        httpAuth = account::createHTTPAuthorization,
                        context = context,
                    )
                } else {
                    BlossomUploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        httpAuth = account::createBlossomUploadAuth,
                        context = context,
                    )
                }

            if (result.url == null) {
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            }

            result.url
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
            null
        }
    }

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        useH265: Boolean,
        stripMetadata: Boolean = true,
        convertGifToMp4: Boolean = false,
    ) = try {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, context, useH265, stripMetadata, convertGifToMp4)
    } catch (_: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    private fun uploadUnsafe(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        useH265: Boolean,
        stripMetadata: Boolean = true,
        convertGifToMp4: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    account,
                    context,
                    useH265,
                    stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    convertGifToMp4 = convertGifToMp4,
                )

            if (results.allGood) {
                val urls =
                    results.successful.mapNotNull { state ->
                        if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            val iMeta =
                                IMetaTagBuilder(state.result.url)
                                    .apply {
                                        hash(state.result.fileHeader.hash)
                                        size(state.result.fileHeader.size)
                                        state.result.fileHeader.mimeType
                                            ?.let { mimeType(it) }
                                        state.result.fileHeader.dim
                                            ?.let { dims(it) }
                                        state.result.fileHeader.blurHash
                                            ?.let { blurhash(it.blurhash) }
                                        state.result.magnet?.let { magnet(it) }
                                        state.result.uploadedHash?.let { originalHash(it) }
                                        alt?.let { alt(it) }
                                        contentWarningReason?.let { sensitiveContent(contentWarningReason) }
                                    }.build()

                            iMetaAttachments.replace(iMeta.url, iMeta)

                            val markdownImage = "![${alt ?: ""}](${state.result.url})"
                            markdownImage
                        } else {
                            null
                        }
                    }

                message.insertUrlAtCursor(urls.joinToString(" "))

                multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            mediaUploadTracker.finishUpload()
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

        multiOrchestrator = null
        mediaUploadTracker.finishUpload()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        wantsExclusiveGeoPost = false
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.value = TextFieldValue("")

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
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
                userSuggestions.replaceCurrentWord(message, lastWord, item)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.value = TextFieldValue("")
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    fun autocompleteWithEmoji(item: EmojiMedia) {
        val wordToInsert = ":${item.code}:"
        message.replaceCurrentWord(wordToInsert)
        emojiSuggestions?.reset()
        draftTag.newVersion()
    }

    fun autocompleteWithEmojiUrl(item: EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link) {
                Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForImage(item.link)
            }
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

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init") { "OnCleared: ${this.javaClass.simpleName}" }
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
                NewMessageTagger(message.text.toString(), emptyList(), emptyList(), accountViewModel)
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

    fun toggleExpirationDate() {
        wantsExpirationDate = !wantsExpirationDate
        if (wantsExpirationDate) {
            expirationDate = TimeUtils.oneDayAhead()
        }
        draftTag.newVersion()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
