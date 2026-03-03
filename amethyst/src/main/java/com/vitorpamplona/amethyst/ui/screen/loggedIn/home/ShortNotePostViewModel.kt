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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceAnonymizationController
import com.vitorpamplona.amethyst.ui.actions.uploads.VoicePreset
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
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuoteIds
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
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
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    TO_USERS,
}

@Stable
open class ShortNotePostViewModel :
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

    var originalNote: Note? by mutableStateOf(null)
    var forkedFromNote: Note? by mutableStateOf(null)

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Voice Messages
    var voiceRecording by mutableStateOf<RecordingResult?>(null)
    var voiceLocalFile by mutableStateOf<java.io.File?>(null)
    var isUploadingVoice by mutableStateOf(false)
    var voiceMetadata by mutableStateOf<AudioMeta?>(null)
    var voiceSelectedServer by mutableStateOf<ServerName?>(null)
    var voiceOrchestrator by mutableStateOf<UploadOrchestrator?>(null)

    // Voice Anonymization
    private val voiceAnonymization =
        VoiceAnonymizationController(
            scope = viewModelScope,
            logTag = "ShortNotePostViewModel",
            onError = { error ->
                accountViewModel.toastManager.toast(
                    stringRes(Amethyst.instance.appContext, R.string.error),
                    error.message ?: "Voice anonymization failed",
                )
            },
        )

    val activeFile: java.io.File?
        get() = voiceAnonymization.activeFile(voiceLocalFile)

    val activeWaveform: List<Float>?
        get() = voiceAnonymization.activeWaveform(voiceRecording?.amplitudes)

    val selectedPreset: VoicePreset
        get() = voiceAnonymization.selectedPreset

    val processingPreset: VoicePreset?
        get() = voiceAnonymization.processingPreset

    // Polls
    var canUsePoll by mutableStateOf(false)
    var wantsPoll by mutableStateOf(false)
    var pollOptions: SnapshotStateMap<Int, OptionTag> = newStateMapPollOptions()
    var closedAt by mutableStateOf(TimeUtils.oneDayAhead())

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

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationState.LocationResult>? = null
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM.account)

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account)
    }

    open fun load(
        replyingTo: Note?,
        quote: Note?,
        fork: Note?,
        version: Note?,
        draft: Note?,
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
            originalNote = replyingTo
            replyingTo?.let { replyNote ->
                if (replyNote.event is BaseThreadedEvent) {
                    this.eTags = (replyNote.replyTo ?: emptyList()).plus(replyNote)
                } else {
                    this.eTags = listOf(replyNote)
                }

                if (replyNote.event !is CommunityDefinitionEvent) {
                    replyNote.author?.let { replyUser ->
                        val currentMentions =
                            (replyNote.event as? TextNoteEvent)
                                ?.mentions()
                                ?.map { LocalCache.getOrCreateUser(it.pubKey) }
                                ?: emptyList()

                        if (currentMentions.contains(replyUser)) {
                            this.pTags = currentMentions
                        } else {
                            this.pTags = currentMentions.plus(replyUser)
                        }
                    }
                }
            }
                ?: run {
                    eTags = null
                    pTags = null
                }

            val user = account.userProfile()

            canAddInvoice = user.lnAddress() != null
            canAddZapRaiser = user.lnAddress() != null
            canUsePoll = originalNote == null
            multiOrchestrator = null

            quote?.let { quotedNote ->
                message = TextFieldValue(message.text + "\nnostr:${quotedNote.toNEvent()}")

                quotedNote.author?.let { quotedUser ->
                    if (quotedUser.pubkeyHex != user.pubkeyHex) {
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                            forwardZapTo.value.addItem(quotedUser)
                        }
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == user.pubkeyHex }) {
                            forwardZapTo.value.addItem(user)
                        }

                        val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                        forwardZapTo.value.updatePercentage(pos, 0.9f)
                    }
                }
            }

            fork?.let { forkedNoted ->
                message = TextFieldValue(version?.event?.content ?: forkedNoted.event?.content ?: "")

                forkedNoted.event?.isSensitiveOrNSFW()?.let {
                    if (it) wantsToMarkAsSensitive = true
                }

                forkedNoted.event?.zapraiserAmount()?.let {
                    zapRaiserAmount.value = it
                }

                forkedNoted.event?.zapSplitSetup()?.let { setup ->
                    val totalWeight = setup.sumOf { if (it is ZapSplitSetupLnAddress) 0.0 else it.weight }

                    setup.forEach {
                        if (it is ZapSplitSetup) {
                            forwardZapTo.value.addItem(LocalCache.getOrCreateUser(it.pubKeyHex), (it.weight / totalWeight).toFloat())
                        }
                    }
                }

                // Only adds if it is not already set up.
                if (forwardZapTo.value.items.isEmpty()) {
                    forkedNoted.author?.let { forkedAuthor ->
                        if (forkedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == forkedAuthor.pubkeyHex }) forwardZapTo.value.addItem(forkedAuthor)
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) forwardZapTo.value.addItem(accountViewModel.userProfile())

                            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == forkedAuthor.pubkeyHex }
                            forwardZapTo.value.updatePercentage(pos, 0.8f)
                        }
                    }
                }

                forkedNoted.author?.let {
                    if (this.pTags == null) {
                        this.pTags = listOf(it)
                    } else if (this.pTags?.contains(it) != true) {
                        this.pTags = listOf(it) + (this.pTags ?: emptyList())
                    }
                }

                forkedFromNote = forkedNoted
            } ?: run {
                forkedFromNote = null
            }

            if (!forwardZapTo.value.items.isEmpty()) {
                wantsForwardZapTo = true
            }
        }

        urlPreviews.update(message)
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent is TextNoteEvent) {
            loadFromDraft(draftEvent)
        }

        if (draftEvent is PollEvent) {
            loadFromDraft(draftEvent)
        }
    }

    private fun loadFromDraft(draftEvent: TextNoteEvent) {
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

        eTags =
            draftEvent.tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) != "fork" }.mapNotNull {
                val note = LocalCache.checkGetOrCreateNote(it[1])
                note
            }

        pTags =
            draftEvent.tags.filter { it.size > 1 && it[0] == "p" }.map {
                LocalCache.getOrCreateUser(it[1])
            }

        draftEvent.tags.filter { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "fork" }.forEach {
            val note = LocalCache.checkGetOrCreateNote(it[1])
            forkedFromNote = note
        }

        originalNote =
            draftEvent
                .tags
                .filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) == "reply" }
                .map {
                    LocalCache.checkGetOrCreateNote(it[1])
                }.firstOrNull()

        if (originalNote == null) {
            originalNote =
                draftEvent
                    .tags
                    .filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) == "root" }
                    .map {
                        LocalCache.checkGetOrCreateNote(it[1])
                    }.firstOrNull()
        }

        canUsePoll = originalNote == null

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        wantsPoll = false

        message = TextFieldValue(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message)
    }

    private fun loadFromDraft(draftEvent: PollEvent) {
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

        eTags =
            draftEvent.tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) != "fork" }.mapNotNull {
                val note = LocalCache.checkGetOrCreateNote(it[1])
                note
            }

        pTags =
            draftEvent.tags.filter { it.size > 1 && it[0] == "p" }.map {
                LocalCache.getOrCreateUser(it[1])
            }

        canUsePoll = originalNote == null

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        val polls = draftEvent.options()
        wantsPoll = polls.isNotEmpty()

        polls.forEachIndexed { index, tag ->
            pollOptions[index] = tag
        }

        closedAt = draftEvent.endsAt() ?: TimeUtils.oneDayAhead()

        message = TextFieldValue(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message)
    }

    suspend fun sendPostSync() {
        // Upload voice message first if it hasn't been uploaded yet
        if (voiceRecording != null && voiceMetadata == null) {
            val serverToUse = voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer
            uploadVoiceMessageSync(
                serverToUse,
                { _, _ -> }, // Error handling is done by checking voiceMetadata below
            )
            // Abort if upload failed - don't post without voice data
            if (voiceMetadata == null) {
                Log.w("ShortNotePostViewModel", "Voice upload failed, aborting post")
                return
            }
            // Update default server if voice message was successfully uploaded
            voiceSelectedServer?.let {
                account.settings.changeDefaultFileServer(it)
            }
        }

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

        if (accountViewModel.settings.isCompleteUIMode()) {
            // Tracked broadcasting with progress feedback (non-blocking)
            val (event, relays, extras) = accountViewModel.account.createPostEvent(template, extraNotesToBroadcast)

            // Launch broadcast in background - don't wait for completion
            accountViewModel.viewModelScope.launch {
                accountViewModel.broadcastTracker.trackBroadcast(
                    event = event,
                    relays = relays,
                    client = accountViewModel.account.client,
                )
                accountViewModel.account.consumePostEvent(event, relays, extras)
            }
        } else {
            // Fire-and-forget (original behavior)
            accountViewModel.account.signAndComputeBroadcast(template, extraNotesToBroadcast)
        }

        accountViewModel.launchSigner {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val attachments = mutableSetOf<Event>()
            nip95attachments.forEach {
                attachments.add(it.first)
                attachments.add(it.second)
            }

            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, attachments)
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        // Check if this is a voice message
        voiceMetadata?.let { audioMeta ->
            // Only create voice reply if original note is also a voice message
            val originalVoiceHint = originalNote?.toEventHint<BaseVoiceEvent>()
            if (originalVoiceHint != null) {
                // Create voice reply event (KIND 1244)
                return VoiceReplyEvent.build(
                    voiceMessage = audioMeta,
                    replyingTo = originalVoiceHint,
                )
            }
            // If no original note, create a standalone voice event (KIND 1222)
            if (originalNote == null) {
                return VoiceEvent.build(
                    voiceMessage = audioMeta,
                )
            }
            // Otherwise, original note exists but is not a voice message
            // Create a TextNoteEvent (KIND 1) with audio as IMeta attachment
            return TextNoteEvent.build(audioMeta.url) {
                val replyingTo = originalNote?.toEventHint<TextNoteEvent>()
                if (replyingTo != null) {
                    val tags = prepareETagsAsReplyTo(replyingTo, null)
                    accountViewModel.fixReplyTagHints(tags)
                    markedETags(tags)
                    notify(replyingTo.toPTag())
                }
                pTags?.let { userList ->
                    val tags =
                        userList.map {
                            val tag = it.toPTag()
                            if (tag.relayHint == null) {
                                tag.copy(relayHint = LocalCache.relayHints.hintsForKey(it.pubkeyHex).firstOrNull())
                            } else {
                                tag
                            }
                        }
                    notify(tags)
                }
                // Add audio as IMeta attachment
                add(audioMeta.toIMetaArray())
            }
        }

        val tagger =
            NewMessageTagger(
                message.text,
                pTags,
                eTags,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = if (wantsToAddGeoHash) (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString() else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = findEmoji(tagger.message, account.emoji.myEmojis.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null

        return if (wantsPoll) {
            val options = pollOptions.map { it.value }

            if (options.isEmpty()) return null

            val quotes = findNostrUris(tagger.message)
            val relays =
                accountViewModel.account.nip65RelayList.outboxFlow.value
                    .toList()

            PollEvent.build(tagger.message, options, closedAt, relays) {
                pTags(tagger.directMentionsUsers.map { it.toPTag() })
                quotes(quotes)
                hashtags(findHashtags(tagger.message))

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        } else {
            TextNoteEvent.build(tagger.message) {
                val replyingTo = originalNote?.toEventHint<TextNoteEvent>()
                val forkingFrom = forkedFromNote?.toEventHint<TextNoteEvent>()

                if (replyingTo != null || forkingFrom != null) {
                    val tags = prepareETagsAsReplyTo(replyingTo, forkingFrom)
                    // fixes wrong tags from previous clients
                    tags.forEach {
                        val note = accountViewModel.getNoteIfExists(it.eventId)
                        val ourAuthor = note?.author?.pubkeyHex
                        val ourHint = note?.relayHintUrl()
                        if (it.author == null || it.author?.isBlank() == true) {
                            it.author = ourAuthor
                        } else {
                            if (ourAuthor != null && it.author != ourAuthor) {
                                it.author = ourAuthor
                            }
                        }
                        if (it.relay == null) {
                            it.relay = ourHint
                        } else {
                            if (ourHint != null && it.relay != ourHint) {
                                it.relay = ourHint
                            }
                        }
                    }
                    markedETags(tags)
                }

                tagger.pTags?.let { userList ->
                    val tags =
                        userList.map {
                            val tag = it.toPTag()
                            if (tag.relayHint == null) {
                                tag.copy(relayHint = LocalCache.relayHints.hintsForKey(it.pubkeyHex).firstOrNull())
                            } else {
                                tag
                            }
                        }
                    notify(tags)
                }

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
    }

    fun findEmoji(
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

    fun uploadUnsafe(
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
                    if (state.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        val nip95 = account.createNip95(state.result.bytes, headerInfo = state.result.fileHeader, alt, contentWarningReason)
                        nip95attachments = nip95attachments + nip95
                        val note = nip95.let { it1 -> account.consumeNip95(it1.first, it1.second) }

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

    open fun cancel() {
        draftTag.rotate()

        message = TextFieldValue("")

        forkedFromNote = null

        multiOrchestrator = null
        isUploadingImage = false
        voiceAnonymization.clear()
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        isUploadingVoice = false
        voiceMetadata = null
        voiceSelectedServer = null
        voiceOrchestrator = null
        pTags = null

        wantsPoll = false
        pollOptions = newStateMapPollOptions()
        closedAt = TimeUtils.oneDayAhead()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsExclusiveGeoPost = false
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

    open fun removeFromReplyList(userToRemove: User) {
        pTags = pTags?.filter { it != userToRemove }
    }

    open fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    override fun updateMessage(newMessage: TextFieldValue) {
        message = newMessage
        urlPreviews.update(message)

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()

            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            userSuggestions?.processCurrentWord(lastWord)

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

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                message = userSuggestions.replaceCurrentWord(message, lastWord, item)
                urlPreviews.update(message)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.value = TextFieldValue("")
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiMedia) {
        val wordToInsert = ":${item.code}:"

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiMedia) {
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

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, OptionTag> =
        mutableStateMapOf(
            0 to OptionTag(RandomInstance.randomChars(6), ""),
            1 to OptionTag(RandomInstance.randomChars(6), ""),
        )

    fun canPost(): Boolean {
        // Voice messages can be posted without text (with either uploaded or pending recording)
        if (voiceMetadata != null || voiceRecording != null) {
            return !isUploadingVoice && !isUploadingImage && processingPreset == null
        }

        // Regular text/media posts require text
        return message.text.isNotBlank() &&
            !isUploadingImage &&
            !isUploadingVoice &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            (
                !wantsPoll ||
                    (
                        pollOptions.isNotEmpty() && pollOptions.all { it.value.label.isNotEmpty() } &&
                            closedAt > TimeUtils.oneMinuteFromNow()
                    )
            ) &&
            multiOrchestrator == null
    }

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    fun selectVoiceRecording(recording: RecordingResult) {
        // Cancel any ongoing processing and delete existing files
        voiceAnonymization.clear()
        deleteVoiceLocalFile()
        voiceRecording = recording
        voiceLocalFile = recording.file
        voiceMetadata = null
    }

    fun getVoicePreviewMetadata(): AudioMeta? =
        voiceRecording?.let { recording ->
            AudioMeta(
                url = "", // Empty URL for preview (local file will be used)
                mimeType = recording.mimeType,
                duration = recording.duration,
                waveform = recording.amplitudes,
            )
        }

    fun selectPreset(preset: VoicePreset) {
        voiceAnonymization.selectPreset(preset, voiceLocalFile)
    }

    fun removeVoiceMessage() {
        voiceAnonymization.clear()
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        voiceMetadata = null
        voiceSelectedServer = null
        isUploadingVoice = false
        voiceOrchestrator = null
    }

    private fun deleteVoiceLocalFile() {
        voiceLocalFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d("ShortNotePostViewModel", "Deleted voice file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w("ShortNotePostViewModel", "Failed to delete voice file: ${file.absolutePath}", e)
            }
        }
    }

    suspend fun uploadVoiceMessageSync(
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
    ) {
        val recording = voiceRecording ?: return
        val fileToUpload = activeFile ?: recording.file
        val waveform = activeWaveform ?: recording.amplitudes
        val appContext = Amethyst.instance.appContext
        val uploadErrorTitle = stringRes(appContext, R.string.upload_error_title)
        val uploadVoiceNip95NotSupported = stringRes(appContext, R.string.upload_error_voice_message_nip95_not_supported)
        val uploadVoiceFailed = stringRes(appContext, R.string.upload_error_voice_message_failed)
        val uploadVoiceExceptionMessage: (String) -> String = { detail ->
            stringRes(appContext, R.string.upload_error_voice_message_exception, detail)
        }

        isUploadingVoice = true

        try {
            val uri = android.net.Uri.fromFile(fileToUpload)
            val orchestrator = UploadOrchestrator()
            voiceOrchestrator = orchestrator

            val result =
                orchestrator.upload(
                    uri = uri,
                    mimeType = recording.mimeType,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.UNCOMPRESSED,
                    server = server,
                    account = account,
                    context = appContext,
                    useH265 = false,
                )

            when (result) {
                is UploadingState.Finished -> {
                    when (val orchestratorResult = result.result) {
                        is UploadOrchestrator.OrchestratorResult.ServerResult -> {
                            voiceMetadata =
                                AudioMeta(
                                    url = orchestratorResult.url,
                                    mimeType = recording.mimeType,
                                    hash = orchestratorResult.fileHeader.hash,
                                    duration = recording.duration,
                                    waveform = waveform,
                                )
                            // Delete the local file after successful upload
                            deleteVoiceLocalFile()
                            voiceAnonymization.deleteDistortedFiles()
                            voiceLocalFile = null
                            voiceRecording = null
                        }

                        is UploadOrchestrator.OrchestratorResult.NIP95Result -> {
                            // For NIP95, we need to create the event and get the nevent URL
                            // This is handled differently - skip for now
                            onError(uploadErrorTitle, uploadVoiceNip95NotSupported)
                        }
                    }
                }

                is UploadingState.Error -> {
                    onError(uploadErrorTitle, uploadVoiceFailed)
                    voiceRecording = null
                }
            }
        } catch (e: Exception) {
            onError(uploadErrorTitle, uploadVoiceExceptionMessage(e.message ?: e.javaClass.simpleName))
            voiceRecording = null
        } finally {
            isUploadingVoice = false
            voiceOrchestrator = null
        }
    }

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

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger =
                NewMessageTagger(message.text, emptyList(), emptyList(), accountViewModel)
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

    fun removePollOption(index: Int) {
        pollOptions.removeOrdered(index)
        draftTag.newVersion()
    }

    fun updatePollOption(
        index: Int,
        label: String,
    ) {
        val current = pollOptions[index]
        pollOptions[index] = OptionTag(current?.code ?: RandomInstance.randomChars(6), label)
        draftTag.newVersion()
    }

    private fun MutableMap<Int, OptionTag>.removeOrdered(index: Int) {
        val keyList = keys
        val elementList = values.toMutableList()
        run stop@{
            for (i in index until elementList.size) {
                val nextIndex = i + 1
                if (nextIndex == elementList.size) return@stop
                elementList[i] = elementList[nextIndex].also { elementList[nextIndex] = OptionTag(RandomInstance.randomChars(6), "") }
            }
        }
        elementList.removeAt(elementList.size - 1)
        val newEntries = keyList.zip(elementList) { key, content -> Pair(key, content) }
        this.clear()
        this.putAll(newEntries)
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
