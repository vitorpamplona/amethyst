/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send

import android.content.Context
import android.util.Log
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
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.UserSuggestionAnchor
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.EmojiSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip28PublicChat.base.notify
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.notify
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
open class ChannelNewMessageViewModel :
    ViewModel(),
    ILocationGrabber {
    val draftTag = DraftTagState()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                sendDraft()
            }
        }
    }

    var accountViewModel: AccountViewModel? = null
    var account: Account? = null
    var channel: Channel? = null

    val replyTo = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)
    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    var forwardZapTo by mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    var forwardZapToEditting by mutableStateOf(TextFieldValue(""))

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationState.LocationResult>? = null

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    var zapRaiserAmount by mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account?.userProfile()?.info?.lnAddress()

    fun hasLnAddress(): Boolean = account?.userProfile()?.info?.lnAddress() != null

    fun user(): User? = account?.userProfile()

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM)

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM)

        this.uploadState =
            ChatFileUploadState(
                account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0],
            )
    }

    open fun load(channel: Channel) {
        this.channel = channel
    }

    open fun reply(replyNote: Note) {
        replyTo.value = replyNote
        draftTag.newVersion()
    }

    fun clearReply() {
        replyTo.value = null
        draftTag.newVersion()
    }

    open fun editFromDraft(draft: Note) {
        val noteEvent = draft.event
        val noteAuthor = draft.author

        if (noteEvent is DraftEvent && noteAuthor != null) {
            viewModelScope.launch(Dispatchers.IO) {
                accountViewModel?.createTempDraftNote(noteEvent) { innerNote ->
                    if (innerNote != null) {
                        val oldTag = (draft.event as? AddressableEvent)?.dTag()
                        if (oldTag != null) {
                            draftTag.set(oldTag)
                        }
                        loadFromDraft(innerNote)
                    }
                }
            }
        }
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return

        val localfowardZapTo = draftEvent.tags.zapSplitSetup()
        val totalWeight = localfowardZapTo.sumOf { it.weight }
        forwardZapTo = SplitBuilder()
        localfowardZapTo.forEach {
            if (it is ZapSplitSetup) {
                val user = LocalCache.getOrCreateUser(it.pubKeyHex)
                forwardZapTo.addItem(user, (it.weight / totalWeight).toFloat())
            }
            // don't support edditing old-style splits.
        }
        forwardZapToEditting = TextFieldValue("")
        wantsForwardZapTo = localfowardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapraiser = zapraiser != null
        zapRaiserAmount = null
        if (zapraiser != null) {
            zapRaiserAmount = zapraiser
        }

        if (forwardZapTo.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        if (draftEvent as? ChannelMessageEvent != null) {
            val replyId = draftEvent.reply()?.eventId
            if (replyId != null) {
                accountViewModel?.checkGetOrCreateNote(replyId) {
                    replyTo.value = it
                }
            }
        } else if (draftEvent as? LiveActivitiesChatMessageEvent != null) {
            val replyId = draftEvent.reply()?.eventId
            if (replyId != null) {
                accountViewModel?.checkGetOrCreateNote(replyId) {
                    replyTo.value = it
                }
            }
        }

        message = TextFieldValue(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreview = findUrlInMessage()
    }

    fun sendPost(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            sendPostSync()
            onDone()
        }
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        val channelRelays = channel?.relays() ?: emptyList()

        accountViewModel?.account?.signAndSendPrivately(template, channelRelays)

        accountViewModel?.deleteDraft(draftTag.current)

        cancel()
    }

    fun sendDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            sendDraftSync()
        }
    }

    suspend fun sendDraftSync() {
        val accountViewModel = accountViewModel ?: return

        if (message.text.isBlank()) {
            account?.deleteDraft(draftTag.current)
        } else {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraft(draftTag.current, template)
        }
    }

    fun pickedMedia(list: ImmutableList<SelectedMedia>) {
        uploadState?.load(list)
    }

    fun upload(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val myAccount = account ?: return@launch
            val uploadState = uploadState ?: return@launch

            val myMultiOrchestrator = uploadState.multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    viewModelScope,
                    uploadState.caption,
                    uploadState.contentWarningReason,
                    MediaCompressor.intToCompressorQuality(uploadState.mediaQualitySlider),
                    uploadState.selectedServer,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach {
                    if (it.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        account?.createNip95(it.result.bytes, headerInfo = it.result.fileHeader, uploadState.caption, uploadState.contentWarningReason) { nip95 ->
                            nip95attachments = nip95attachments + nip95
                            val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                            note?.let {
                                message = message.insertUrlAtCursor(it.toNostrUri())
                            }

                            urlPreview = findUrlInMessage()
                        }
                    } else if (it.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        iMetaAttachments.add(it.result, uploadState.caption, uploadState.contentWarningReason)

                        message = message.insertUrlAtCursor(it.result.url)
                        urlPreview = findUrlInMessage()
                    }
                }

                uploadState.reset()
                onceUploaded()
                draftTag.newVersion()
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            isUploadingImage = false
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        val channel = channel ?: return null
        val accountViewModel = accountViewModel ?: return null

        val tagger =
            NewMessageTagger(
                message = message.text,
                pTags = listOfNotNull(replyTo.value?.author),
                eTags = listOfNotNull(replyTo.value),
                channelHex = channel.idHex,
                dao = accountViewModel,
            )
        tagger.run()

        val urls = findURLs(message.text)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())
        val emojis = findEmoji(message.text, accountViewModel.account.myEmojis.value)

        val channelRelays = channel.relays()
        val geoHash = (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString()

        return if (channel is PublicChatChannel) {
            val replyingToEvent = replyTo.value?.toEventHint<ChannelMessageEvent>()
            val channelEvent = channel.event

            if (replyingToEvent != null) {
                ChannelMessageEvent.reply(tagger.message, replyingToEvent) {
                    notify(replyingToEvent.toPTag())

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    geoHash?.let { geohash(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            } else if (channelEvent != null) {
                val hint = EventHintBundle(channelEvent, channelRelays.firstOrNull())
                ChannelMessageEvent.message(tagger.message, hint) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    geoHash?.let { geohash(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            } else {
                ChannelMessageEvent.message(tagger.message, ETag(channel.idHex, channelRelays.firstOrNull())) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    geoHash?.let { geohash(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }
        } else if (channel is LiveActivitiesChannel) {
            val replyingToEvent = replyTo.value?.toEventHint<LiveActivitiesChatMessageEvent>()
            val activity = channel.info

            if (replyingToEvent != null) {
                LiveActivitiesChatMessageEvent.reply(tagger.message, replyingToEvent) {
                    notify(replyingToEvent.toPTag())

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            } else if (activity != null) {
                val hint = EventHintBundle(activity, channelRelays.firstOrNull() ?: replyingToEvent?.relay)

                LiveActivitiesChatMessageEvent.message(tagger.message, hint) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            } else {
                LiveActivitiesChatMessageEvent.message(tagger.message, channel.toATag()) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }
        } else {
            null
        }
    }

    fun findEmoji(
        message: String,
        myEmojiSet: List<Account.EmojiMedia>?,
    ): List<EmojiUrlTag> {
        if (myEmojiSet == null) return emptyList()
        return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
            myEmojiSet.firstOrNull { it.code == possibleEmoji }?.let { EmojiUrlTag(it.code, it.url.url) }
        }
    }

    open fun cancel() {
        message = TextFieldValue("")

        replyTo.value = null

        urlPreview = null

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false

        wantsToAddGeoHash = false

        forwardZapTo = SplitBuilder()
        forwardZapToEditting = TextFieldValue("")

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()

        draftTag.rotate()
    }

    fun deleteDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            accountViewModel?.deleteDraft(draftTag.current)
        }
    }

    open fun findUrlInMessage(): String? = RichTextParser().parseValidUrls(message.text).firstOrNull()

    open fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    open fun updateMessage(newMessage: TextFieldValue) {
        message = newMessage
        urlPreview = findUrlInMessage()

        if (newMessage.selection.collapsed) {
            val lastWord = newMessage.currentWord()

            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            userSuggestions?.processCurrentWord(lastWord)

            emojiSuggestions?.processCurrentWord(lastWord)
        }

        draftTag.newVersion()
    }

    open fun updateZapForwardTo(newZapForwardTo: TextFieldValue) {
        forwardZapToEditting = newZapForwardTo
        if (newZapForwardTo.selection.collapsed) {
            val lastWord = newZapForwardTo.text
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                message = it.replaceCurrentWord(message, lastWord, item)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.addItem(item)
                forwardZapToEditting = TextFieldValue("")
            }

            userSuggestionsMainMessage = null
            it.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: Account.EmojiMedia) {
        val wordToInsert = ":${item.code}:"
        message = message.replaceCurrentWord(wordToInsert)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: Account.EmojiMedia) {
        val wordToInsert = item.url.url + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(
                item.url.url,
                { Amethyst.instance.okHttpClients.getHttpClient(accountViewModel?.account?.shouldUseTorForImageDownload() ?: false) },
            )
        }

        message = message.replaceCurrentWord(wordToInsert)

        emojiSuggestions?.reset()

        urlPreview = findUrlInMessage()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            uploadState?.isUploadingImage != true &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount != null) &&
            uploadState?.multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager

    override fun locationFlow(): StateFlow<LocationState.LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }

    fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.updatePercentage(index, sliderValue)
    }

    fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.Default) {
            val tagger = NewMessageTagger(message.text, emptyList(), emptyList(), null, accountViewModel!!)
            tagger.run()
            tagger.pTags?.forEach { taggedUser ->
                if (!forwardZapTo.items.any { it.key == taggedUser }) {
                    forwardZapTo.addItem(taggedUser)
                }
            }
        }
    }

    fun updateZapRaiserAmount(newAmount: Long?) {
        zapRaiserAmount = newAmount
        draftTag.newVersion()
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }
}
