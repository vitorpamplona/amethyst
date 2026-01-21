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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.publicMessages

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
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.publicMessages.tags.ReceiverTag
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuoteIds
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
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
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class NewPublicMessageViewModel :
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

    var replyingTo: Note? by mutableStateOf(null)

    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    var toUsers by mutableStateOf(TextFieldValue(""))

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

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
    }

    fun load(users: Set<HexKey>) {
        val userSet = users - account.userProfile().pubkeyHex

        toUsers =
            TextFieldValue(
                userSet.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
            )
    }

    fun reply(post: Note) {
        this.replyingTo = post
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

        val userSet = draftEvent.groupKeys() - account.userProfile().pubkeyHex

        toUsers =
            TextFieldValue(
                userSet.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
            )

        message = TextFieldValue(draftEvent.content)
        urlPreviews.update(message)

        iMetaAttachments.addAll(draftEvent.imetas())
    }

    override fun locationFlow(): StateFlow<LocationState.LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        val extraNotesToBroadcast = mutableListOf<Event>()

        if (nip95attachments.isNotEmpty()) {
            val usedImages = template.tags.taggedQuoteIds().toSet()
            nip95attachments.forEach {
                if (usedImages.contains(it.second.id)) {
                    extraNotesToBroadcast.add(it.first)
                    extraNotesToBroadcast.add(it.second)
                }
            }
        }

        val version = draftTag.current
        cancel()

        accountViewModel.account.signAndComputeBroadcast(template, extraNotesToBroadcast)
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val broadcast = mutableSetOf<Event>()
            nip95attachments.forEach {
                broadcast.add(it.first)
                broadcast.add(it.second)
            }

            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, broadcast)
        }
    }

    private suspend fun createTemplate(): EventTemplate<PublicMessageEvent>? {
        val toUsersTagger = NewMessageTagger(this@NewPublicMessageViewModel.toUsers.text, null, null, accountViewModel)
        toUsersTagger.run()

        val tagger = NewMessageTagger(message.text, null, null, accountViewModel)
        tagger.run()

        val users = (toUsersTagger.pTags ?: emptyList()) + (tagger.pTags ?: emptyList())
        val toUsers = users.mapTo(mutableSetOf()) { ReceiverTag(it.pubkeyHex, it.bestRelayHint()) }

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString()
        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount.value else null

        val emojis = findEmoji(tagger.message, account.emoji.myEmojis.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null

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

            emojis(emojis)
            imetas(usedAttachments)
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

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
    ) = try {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, context)
    } catch (e: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    fun uploadUnsafe(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
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
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        val nip95 = account.createNip95(state.result.bytes, headerInfo = state.result.fileHeader, alt, contentWarningReason)
                        nip95attachments = nip95attachments + nip95
                        val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                        note?.let {
                            message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                            urlPreviews.update(message)
                        }
                    } else if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
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

                        message = message.insertUrlAtCursor(state.result.url)
                        urlPreviews.update(message)
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

        toUsers = TextFieldValue("")
        message = TextFieldValue("")
        multiOrchestrator = null

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

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
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
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount.value != null) &&
            (toUsers.text.isNotBlank()) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
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
