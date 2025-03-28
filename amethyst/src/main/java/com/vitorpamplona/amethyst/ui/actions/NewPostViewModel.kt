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
package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.imageExtensions
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
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
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.experimental.zapPolls.closedAt
import com.vitorpamplona.quartz.experimental.zapPolls.consensusThreshold
import com.vitorpamplona.quartz.experimental.zapPolls.maxAmount
import com.vitorpamplona.quartz.experimental.zapPolls.minAmount
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTags
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.positionalMarkedTags
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip28PublicChat.base.notify
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.reply.notify
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.notify
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip73ExternalIds.GeohashId
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
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    TO_USERS,
}

@Stable
open class NewPostViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IZapField,
    IZapRaiser {
    var draftTag: String by mutableStateOf(UUID.randomUUID().toString())

    var accountViewModel: AccountViewModel? = null
    var account: Account? = null

    var originalNote: Note? by mutableStateOf<Note?>(null)
    var forkedFromNote: Note? by mutableStateOf<Note?>(null)

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by
        mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Polls
    var canUsePoll by mutableStateOf(false)
    var wantsPoll by mutableStateOf(false)
    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = newStateMapPollOptions()
    var valueMaximum by mutableStateOf<Long?>(null)
    var valueMinimum by mutableStateOf<Long?>(null)
    var consensusThreshold: Int? = null
    var closedAt: Long? = null

    var isValidRecipients = mutableStateOf(true)
    var isValidvalueMaximum = mutableStateOf(true)
    var isValidvalueMinimum = mutableStateOf(true)
    var isValidConsensusThreshold = mutableStateOf(true)
    var isValidClosedAt = mutableStateOf(true)

    // Classifieds
    var wantsProduct by mutableStateOf(false)
    var title by mutableStateOf(TextFieldValue(""))
    var price by mutableStateOf(TextFieldValue(""))
    var locationText by mutableStateOf(TextFieldValue(""))
    var category by mutableStateOf(TextFieldValue(""))
    var condition by mutableStateOf<ConditionTag.CONDITION>(ConditionTag.CONDITION.USED_LIKE_NEW)

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
    var wantsZapraiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    val draftTextChanges = Channel<String>(Channel.CONFLATED)

    fun lnAddress(): String? = account?.userProfile()?.info?.lnAddress()

    fun hasLnAddress(): Boolean = account?.userProfile()?.info?.lnAddress() != null

    fun user(): User? = account?.userProfile()

    open fun load(
        accountViewModel: AccountViewModel,
        replyingTo: Note?,
        quote: Note?,
        fork: Note?,
        version: Note?,
        draft: Note?,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account

        val noteEvent = draft?.event
        val noteAuthor = draft?.author

        if (draft != null && noteEvent is DraftEvent && noteAuthor != null) {
            viewModelScope.launch(Dispatchers.IO) {
                accountViewModel.createTempDraftNote(noteEvent) { innerNote ->
                    if (innerNote != null) {
                        val oldTag = (draft.event as? AddressableEvent)?.dTag()
                        if (oldTag != null) {
                            draftTag = oldTag
                        }
                        loadFromDraft(innerNote, accountViewModel)
                    }
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

            canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
            canAddZapRaiser = accountViewModel.userProfile().info?.lnAddress() != null
            canUsePoll = originalNote == null
            multiOrchestrator = null

            quote?.let {
                message = TextFieldValue(message.text + "\nnostr:${it.toNEvent()}")

                it.author?.let { quotedUser ->
                    if (quotedUser.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                            forwardZapTo.value.addItem(quotedUser)
                        }
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) {
                            forwardZapTo.value.addItem(accountViewModel.userProfile())
                        }

                        val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                        forwardZapTo.value.updatePercentage(pos, 0.9f)
                    }
                }
            }

            fork?.let {
                message = TextFieldValue(version?.event?.content ?: it.event?.content ?: "")

                it.event?.isSensitiveOrNSFW()?.let {
                    if (it) wantsToMarkAsSensitive = true
                }

                it.event?.zapraiserAmount()?.let {
                    zapRaiserAmount.value = it
                }

                it.event?.zapSplitSetup()?.let {
                    val totalWeight = it.sumOf { if (it is ZapSplitSetupLnAddress) 0.0 else it.weight }

                    it.forEach {
                        if (it is ZapSplitSetup) {
                            forwardZapTo.value.addItem(LocalCache.getOrCreateUser(it.pubKeyHex), (it.weight / totalWeight).toFloat())
                        }
                    }
                }

                // Only adds if it is not already set up.
                if (forwardZapTo.value.items.isEmpty()) {
                    it.author?.let { forkedAuthor ->
                        if (forkedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == forkedAuthor.pubkeyHex }) forwardZapTo.value.addItem(forkedAuthor)
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) forwardZapTo.value.addItem(accountViewModel.userProfile())

                            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == forkedAuthor.pubkeyHex }
                            forwardZapTo.value.updatePercentage(pos, 0.8f)
                        }
                    }
                }

                it.author?.let {
                    if (this.pTags == null) {
                        this.pTags = listOf(it)
                    } else if (this.pTags?.contains(it) != true) {
                        this.pTags = listOf(it) + (this.pTags ?: emptyList())
                    }
                }

                forkedFromNote = it
            } ?: run {
                forkedFromNote = null
            }

            if (!forwardZapTo.value.items.isEmpty()) {
                wantsForwardZapTo = true
            }
        }

        urlPreviews.update(message)
    }

    private fun loadFromDraft(
        draft: Note,
        accountViewModel: AccountViewModel,
    ) {
        Log.d("draft", draft.event!!.toJson())
        val draftEvent = draft.event ?: return

        canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().info?.lnAddress() != null
        multiOrchestrator = null

        val localfowardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localfowardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.value = TextFieldValue("")
        wantsForwardZapTo = localfowardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapraiser = zapraiser != null
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
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

        draftEvent.tags.filter { it.size > 3 && (it[0] == "e" || it[0] == "a") && it.get(3) == "fork" }.forEach {
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

        val polls = draftEvent.tags.filter { it.size > 1 && it[0] == "poll_option" }
        wantsPoll = polls.isNotEmpty()

        polls.forEach {
            pollOptions[it[1].toInt()] = it[2]
        }

        val minMax = draftEvent.tags.filter { it.size > 1 && (it[0] == "value_minimum" || it[0] == "value_maximum") }
        minMax.forEach {
            if (it[0] == "value_maximum") {
                valueMaximum = it[1].toLong()
            } else if (it[0] == "value_minimum") {
                valueMinimum = it[1].toLong()
            }
        }

        wantsProduct = draftEvent.kind == 30402

        title =
            TextFieldValue(
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "title" }
                    .map { it[1] }
                    ?.firstOrNull() ?: "",
            )
        price =
            TextFieldValue(
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "price" }
                    .map { it[1] }
                    ?.firstOrNull() ?: "",
            )
        category =
            TextFieldValue(
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "t" }
                    .map { it[1] }
                    ?.firstOrNull() ?: "",
            )
        locationText =
            TextFieldValue(
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "location" }
                    .map { it[1] }
                    ?.firstOrNull() ?: "",
            )
        condition = ConditionTag.CONDITION.entries.firstOrNull {
            it.value ==
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "condition" }
                    .map { it[1] }
                    .firstOrNull()
        } ?: ConditionTag.CONDITION.USED_LIKE_NEW

        message = TextFieldValue(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message)
    }

    fun sendPost(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            innerSendPost(relayList, null)
            accountViewModel?.deleteDraft(draftTag)
            cancel()
        }
    }

    fun sendDraft(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            sendDraftSync(relayList)
        }
    }

    suspend fun sendDraftSync(relayList: List<RelaySetupInfo>) {
        if (message.text.isBlank()) {
            account?.deleteDraft(draftTag)
        } else {
            innerSendPost(relayList, draftTag)
        }
    }

    private suspend fun innerSendPost(
        relayList: List<RelaySetupInfo>,
        localDraft: String?,
    ) = withContext(Dispatchers.IO) {
        if (accountViewModel == null) {
            cancel()
            return@withContext
        }

        val tagger = NewMessageTagger(message.text, pTags, eTags, originalNote?.channelHex(), accountViewModel!!)
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString()
        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount.value else null

        nip95attachments.forEach {
            if (eTags?.contains(LocalCache.getNoteIfExists(it.second.id)) == true) {
                account?.sendNip95(it.first, it.second, relayList)
            }
        }

        val emojis = findEmoji(tagger.message, account?.myEmojis?.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val replyingTo = originalNote
        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null

        val channel = originalNote?.channelHex()?.let { LocalCache.getChannelIfExists(it) }

        if (replyingTo?.event is CommentEvent || replyingTo?.event is RootScope) {
            val eventHint = replyingTo.toEventHint<Event>() ?: return@withContext

            val template =
                CommentEvent.replyBuilder(
                    msg = tagger.message,
                    replyingTo = eventHint,
                ) {
                    tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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

            account?.signAndSend(localDraft, template, relayList, setOf(replyingTo))
        } else if (wantsExclusiveGeoPost && geoHash != null && originalNote == null) {
            val template =
                CommentEvent.replyExternalIdentity(
                    msg = tagger.message,
                    extId = GeohashId(geoHash),
                ) {
                    tagger.pTags?.let { notify(it.map { it.toPTag() }) }

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    localZapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    contentWarningReason?.let { contentWarning(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }

            account?.signAndSend(localDraft, template, relayList, emptyList())
        } else if (channel != null) {
            if (channel is PublicChatChannel) {
                val replyingToEvent = originalNote?.toEventHint<ChannelMessageEvent>()
                val channelEvent = channel.event
                val channelRelays = channel.relays()

                val template =
                    if (replyingToEvent != null) {
                        ChannelMessageEvent.reply(tagger.message, replyingToEvent) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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
                    } else if (channelEvent != null) {
                        val hint = EventHintBundle(channelEvent, channelRelays.firstOrNull())
                        ChannelMessageEvent.message(tagger.message, hint) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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
                    } else {
                        ChannelMessageEvent.message(tagger.message, ETag(channel.idHex, channelRelays.firstOrNull())) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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

                val broadcast = tagger.directMentionsNotes + (tagger.eTags ?: emptyList())

                account?.signAndSendWithList(draftTag, template, channelRelays, broadcast)
            } else if (channel is LiveActivitiesChannel) {
                val replyingToEvent = originalNote?.toEventHint<LiveActivitiesChatMessageEvent>()
                val activity = channel.info
                val channelRelays = channel.relays()

                val template =
                    if (replyingToEvent != null) {
                        LiveActivitiesChatMessageEvent.reply(tagger.message, replyingToEvent) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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
                    } else if (activity != null) {
                        val hint = EventHintBundle(activity, channelRelays.firstOrNull() ?: replyingToEvent?.relay)

                        LiveActivitiesChatMessageEvent.message(tagger.message, hint) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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
                    } else {
                        LiveActivitiesChatMessageEvent.message(tagger.message, channel.toATag()) {
                            tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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

                val broadcast = tagger.directMentionsNotes + (tagger.eTags ?: emptyList())

                account?.signAndSendWithList(draftTag, template, channelRelays, broadcast)
            }
        } else if (originalNote?.event is GitIssueEvent) {
            val originalNoteHint = originalNote?.toEventHint<GitIssueEvent>() ?: return@withContext

            val template =
                GitReplyEvent.replyIssue(
                    tagger.message,
                    originalNoteHint,
                ) {
                    tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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

            val broadcast = tagger.directMentionsNotes + (tagger.eTags ?: emptyList())

            account?.signAndSend(localDraft, template, relayList, broadcast)
        } else if (originalNote?.event is TorrentCommentEvent) {
            val replyToEvent = originalNote?.event as TorrentCommentEvent

            val rootETag = replyToEvent.torrent()
            val rootNote = rootETag?.eventId?.let { LocalCache.getNoteIfExists(it) }
            val rootNoteEvent = rootNote?.event

            // only uses the root node if the event is loaded.
            val root =
                if (rootNoteEvent != null) {
                    rootNote // refreshes author and relay hint to what we have.
                } else {
                    rootETag?.let { LocalCache.getOrCreateNote(it) } // keeps what came in.
                        ?: originalNote?.replyTo?.firstOrNull { it.event != null && it.replyTo?.isEmpty() == true } // if it has loaded events with zero replies in the reply list
                        ?: originalNote?.replyTo?.firstOrNull() // old rules, first item is root.
                        ?: originalNote
                }

            if (root != null) {
                val replyToSet =
                    if (forkedFromNote != null) {
                        (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                    } else {
                        tagger.eTags
                    }

                val sortedAndMarked =
                    eTags?.map { it.toETag() }?.positionalMarkedTags(
                        root = root.toETag(),
                        replyingTo = replyingTo?.toETag(),
                        forkedFrom = forkedFromNote?.toETag(),
                    )

                val template =
                    TorrentCommentEvent.build(tagger.message) {
                        sortedAndMarked?.let { eTags(sortedAndMarked) }

                        pTags(tagger.directMentionsUsers.map { it.toPTag() })

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

                val broadcast = tagger.directMentionsNotes + (replyToSet ?: emptySet())

                account?.sendTorrentComment(localDraft, template, broadcast, relayList)
            }
        } else if (originalNote?.event is TorrentEvent) {
            val replyToSet =
                if (forkedFromNote != null) {
                    (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                } else {
                    tagger.eTags
                }

            val sortedAndMarked =
                eTags?.map { it.toETag() }?.positionalMarkedTags(
                    root = originalNote?.toETag(),
                    replyingTo = null,
                    forkedFrom = forkedFromNote?.toETag(),
                )

            val template =
                TorrentCommentEvent.build(tagger.message) {
                    sortedAndMarked?.let { eTags(sortedAndMarked) }

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

            val broadcast = tagger.directMentionsNotes + (replyToSet ?: emptySet())

            account?.sendTorrentComment(localDraft, template, broadcast, relayList)
        } else {
            if (wantsPoll) {
                val options = pollOptions.map { PollOptionTag(it.key, it.value) }

                if (options.isEmpty()) return@withContext

                val quotes = findNostrUris(tagger.message)

                val template =
                    PollNoteEvent.build(tagger.message, options) {
                        valueMinimum?.let { minAmount(it) }
                        valueMaximum?.let { maxAmount(it) }
                        closedAt?.let { closedAt(it) }
                        consensusThreshold?.let { consensusThreshold(it / 100.0) }

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

                account?.signAndSend(localDraft, template, relayList, quotes)
            } else if (wantsProduct) {
                val images =
                    urls.mapNotNull {
                        val removedParamsFromUrl =
                            if (it.contains("?")) {
                                it.split("?")[0].lowercase()
                            } else if (it.contains("#")) {
                                it.split("#")[0].lowercase()
                            } else {
                                it
                            }

                        if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                            it
                        } else {
                            null
                        }
                    }

                val quotes = findNostrUris(tagger.message)

                val template =
                    ClassifiedsEvent.build(
                        title.text,
                        PriceTag(price.text, "SATS", null),
                        tagger.message,
                        locationText.text.ifBlank { null },
                        condition,
                        images,
                    ) {
                        hashtags(listOfNotNull(category.text.ifBlank { null }) + findHashtags(tagger.message))
                        quotes(quotes)

                        geoHash?.let { geohash(it) }
                        localZapRaiserAmount?.let { zapraiser(it) }
                        zapReceiver?.let { zapSplits(it) }
                        contentWarningReason?.let { contentWarning(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }

                account?.signAndSend(localDraft, template, relayList, quotes)
            } else {
                val replyToSet =
                    if (forkedFromNote != null) {
                        (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                    } else {
                        tagger.eTags
                    }

                val template =
                    TextNoteEvent.build(
                        note = tagger.message,
                        replyingTo = originalNote?.toEventHint<TextNoteEvent>(),
                        forkingFrom = forkedFromNote?.toEventHint<TextNoteEvent>(),
                    ) {
                        tagger.pTags?.let { notify(it.map { it.toPTag() }) }

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

                val broadcast = tagger.directMentionsNotes + (replyToSet ?: emptySet())

                account?.signAndSend(localDraft, template, relayList, broadcast)
            }
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

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        isPrivate: Boolean = false,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val myAccount = account ?: return@launch

            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    viewModelScope,
                    alt,
                    contentWarningReason,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach {
                    if (it.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        account?.createNip95(it.result.bytes, headerInfo = it.result.fileHeader, alt, contentWarningReason) { nip95 ->
                            nip95attachments = nip95attachments + nip95
                            val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                            note?.let {
                                message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                                urlPreviews.update(message)
                            }
                        }
                    } else if (it.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val iMeta =
                            IMetaTagBuilder(it.result.url)
                                .apply {
                                    hash(it.result.fileHeader.hash)
                                    size(it.result.fileHeader.size)
                                    it.result.fileHeader.mimeType
                                        ?.let { mimeType(it) }
                                    it.result.fileHeader.dim
                                        ?.let { dims(it) }
                                    it.result.fileHeader.blurHash
                                        ?.let { blurhash(it.blurhash) }
                                    it.result.magnet?.let { magnet(it) }
                                    it.result.uploadedHash?.let { originalHash(it) }

                                    alt?.let { alt(it) }
                                    contentWarningReason?.let { sensitiveContent(contentWarningReason) }
                                }.build()

                        iMetaAttachments.replace(iMeta.url, iMeta)

                        message = message.insertUrlAtCursor(it.result.url)
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
        message = TextFieldValue("")

        forkedFromNote = null

        multiOrchestrator = null
        isUploadingImage = false
        pTags = null

        wantsPoll = false
        zapRecipients = mutableStateListOf<HexKey>()
        pollOptions = newStateMapPollOptions()
        valueMaximum = null
        valueMinimum = null
        consensusThreshold = null
        closedAt = null

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount.value = null

        wantsProduct = false
        condition = ConditionTag.CONDITION.USED_LIKE_NEW
        locationText = TextFieldValue("")
        title = TextFieldValue("")
        category = TextFieldValue("")
        price = TextFieldValue("")

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

        draftTag = UUID.randomUUID().toString()

        NostrSearchEventOrUserDataSource.clear()
    }

    fun deleteDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            accountViewModel?.deleteDraft(draftTag)
        }
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
    }

    open fun removeFromReplyList(userToRemove: User) {
        pTags = pTags?.filter { it != userToRemove }
    }

    private fun saveDraft() {
        draftTextChanges.trySend("")
    }

    open fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    override fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreviews.update(message)

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()

            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            userSuggestions?.processCurrentWord(lastWord)

            emojiSuggestions?.processCurrentWord(lastWord)
        }

        saveDraft()
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

        saveDraft()
    }

    open fun autocompleteWithEmoji(item: Account.EmojiMedia) {
        val wordToInsert = ":${item.code}:"

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        saveDraft()
    }

    open fun autocompleteWithEmojiUrl(item: Account.EmojiMedia) {
        val wordToInsert = item.url.url + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(
                item.url.url,
                accountViewModel?.account?.shouldUseTorForImageDownload() ?: false,
            )
        }

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        saveDraft()
    }

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, String> = mutableStateMapOf(Pair(0, ""), Pair(1, ""))

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount.value != null) &&
            (
                !wantsPoll ||
                    (
                        pollOptions.values.all { it.isNotEmpty() } &&
                            isValidvalueMinimum.value &&
                            isValidvalueMaximum.value
                    )
            ) &&
            (
                !wantsProduct ||
                    (
                        !title.text.isNullOrBlank() &&
                            !price.text.isNullOrBlank() &&
                            !category.text.isNullOrBlank()
                    )
            ) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
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

    fun updateMinZapAmountForPoll(textMin: String) {
        valueMinimum = textMin.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        saveDraft()
    }

    fun updateMaxZapAmountForPoll(textMax: String) {
        valueMaximum = textMax.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        saveDraft()
    }

    fun checkMinMax() {
        if ((valueMinimum ?: 0) > (valueMaximum ?: Long.MAX_VALUE)) {
            isValidvalueMinimum.value = false
            isValidvalueMaximum.value = false
        } else {
            isValidvalueMinimum.value = true
            isValidvalueMaximum.value = true
        }
    }

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.Default) {
            val tagger = NewMessageTagger(message.text, emptyList(), emptyList(), null, accountViewModel!!)
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
        saveDraft()
    }

    fun removePollOption(optionIndex: Int) {
        pollOptions.removeOrdered(optionIndex)
        saveDraft()
    }

    private fun MutableMap<Int, String>.removeOrdered(index: Int) {
        val keyList = keys
        val elementList = values.toMutableList()
        run stop@{
            for (i in index until elementList.size) {
                val nextIndex = i + 1
                if (nextIndex == elementList.size) return@stop
                elementList[i] = elementList[nextIndex].also { elementList[nextIndex] = "null" }
            }
        }
        elementList.removeAt(elementList.size - 1)
        val newEntries = keyList.zip(elementList) { key, content -> Pair(key, content) }
        this.clear()
        this.putAll(newEntries)
    }

    fun updatePollOption(
        optionIndex: Int,
        text: String,
    ) {
        pollOptions[optionIndex] = text
        saveDraft()
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        saveDraft()
    }

    fun updateTitle(it: TextFieldValue) {
        title = it
        saveDraft()
    }

    fun updatePrice(it: TextFieldValue) {
        runCatching {
            if (it.text.isEmpty()) {
                price = TextFieldValue("")
            } else if (it.text.toLongOrNull() != null) {
                price = it
            }
        }
        saveDraft()
    }

    fun updateCondition(newCondition: ConditionTag.CONDITION) {
        condition = newCondition
        saveDraft()
    }

    fun updateCategory(value: TextFieldValue) {
        category = value
        saveDraft()
    }

    fun updateLocation(it: TextFieldValue) {
        locationText = it
        saveDraft()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
