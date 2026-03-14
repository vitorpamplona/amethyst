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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.EmojiSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.IZapRaiser
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.toZapSplitSetup
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
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
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class MarkdownPostViewModel :
    ViewModel(),
    IMessageField,
    IZapField,
    IZapRaiser {
    val draftTag = DraftTagState()

    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
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

    override var message by mutableStateOf(TextFieldValue(""))

    var showPreview by mutableStateOf(false)

    val iMetaAttachments = IMetaAttachments()

    var isUploadingImage by mutableStateOf(false)
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null
    var emojiSuggestions: EmojiSuggestionState? = null

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override var forwardZapToEditting = mutableStateOf(TextFieldValue(""))

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    // Editing existing article
    var editingNote: Note? by mutableStateOf(null)
    var existingDTag: String? = null

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddZapRaiser = accountVM.userProfile().lnAddress() != null

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM.account, accountVM.nip05Client)

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account)
    }

    fun load(
        draft: Note?,
        version: Note?,
    ) {
        val noteEvent = version?.event ?: draft?.event

        if (noteEvent is LongTextNoteEvent) {
            title = TextFieldValue(noteEvent.title() ?: "")
            summary = TextFieldValue(noteEvent.summary() ?: "")
            coverImageUrl = noteEvent.image() ?: ""
            message = TextFieldValue(noteEvent.content)
            existingDTag = noteEvent.dTag()
            editingNote = version ?: draft
        }
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
        if (message.text.isBlank() && title.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, emptySet())
        }
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private suspend fun createTemplate(): EventTemplate<out Event>? {
        if (title.text.isBlank()) return null

        val tagger =
            NewMessageTagger(
                message.text,
                null,
                null,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null
        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null

        val emojis = findEmoji(tagger.message, account.emoji.myEmojis.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        return LongTextNoteEvent.build(
            description = tagger.message,
            title = title.text.trim(),
            summary = summary.text.trim().ifBlank { null },
            image = coverImageUrl.trim().ifBlank { null },
            publishedAt = TimeUtils.now(),
            dTag = existingDTag ?: kotlin.uuid.Uuid.random().toString(),
        ) {
            hashtags(findHashtags(tagger.message))
            references(findURLs(tagger.message))
            quotes(findNostrUris(tagger.message))

            localZapRaiserAmount?.let { zapraiser(it) }
            zapReceiver?.let { zapSplits(it) }
            contentWarningReason?.let { contentWarning(it) }

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

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        useH265: Boolean,
    ) = try {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, context, useH265)
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    account,
                    context,
                    useH265,
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val iMeta =
                            IMetaTagBuilder(state.result.url)
                                .apply {
                                    hash(state.result.fileHeader.hash)
                                    size(state.result.fileHeader.size)
                                    state.result.fileHeader.mimeType?.let { mimeType(it) }
                                    state.result.fileHeader.dim?.let { dims(it) }
                                    state.result.fileHeader.blurHash?.let { blurhash(it.blurhash) }
                                    state.result.magnet?.let { magnet(it) }
                                    state.result.uploadedHash?.let { originalHash(it) }
                                    alt?.let { alt(it) }
                                    contentWarningReason?.let { sensitiveContent(contentWarningReason) }
                                }.build()

                        iMetaAttachments.replace(iMeta.url, iMeta)

                        val markdownImage = "![${alt ?: ""}](${state.result.url})"
                        message = message.insertUrlAtCursor(markdownImage)
                    }
                }

                multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            isUploadingImage = false
        }
    }

    fun cancel() {
        draftTag.rotate()

        title = TextFieldValue("")
        summary = TextFieldValue("")
        coverImageUrl = ""
        message = TextFieldValue("")
        showPreview = false

        editingNote = null
        existingDTag = null

        multiOrchestrator = null
        isUploadingImage = false

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.value = TextFieldValue("")

        iMetaAttachments.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null
        emojiSuggestions?.reset()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
    }

    override fun updateMessage(newMessage: TextFieldValue) {
        message = newMessage

        if (message.selection.collapsed) {
            val lastWord = newMessage.currentWord()
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
                message = userSuggestions.replaceCurrentWord(message, lastWord, item)
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
        message = message.replaceCurrentWord(wordToInsert)
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

        message = message.replaceCurrentWord(wordToInsert)
        emojiSuggestions?.reset()
        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        title.text.isNotBlank() &&
            message.text.isNotBlank() &&
            !isUploadingImage &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null
}
