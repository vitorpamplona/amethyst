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
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.components.Split
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.experimental.nip95.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.geohash.getGeoHash
import com.vitorpamplona.quartz.nip04Dm.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.BaseTextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.findURLs
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.NIP17Group
import com.vitorpamplona.quartz.nip19Bech32Entities.toNpub
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip34Git.GitIssueEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiserAmount
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.Price
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.round

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    TO_USERS,
}

@Stable
open class NewPostViewModel : ViewModel() {
    var draftTag: String by mutableStateOf(UUID.randomUUID().toString())

    var accountViewModel: AccountViewModel? = null
    var account: Account? = null
    var requiresNIP17: Boolean = false

    var originalNote: Note? by mutableStateOf<Note?>(null)
    var forkedFromNote: Note? by mutableStateOf<Note?>(null)

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    var iMetaAttachments by mutableStateOf<List<IMetaTag>>(emptyList())
    var nip95attachments by
        mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)

    var userSuggestions by mutableStateOf<List<User>>(emptyList())
    var userSuggestionAnchor: TextRange? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    val emojiSearch: MutableStateFlow<String> = MutableStateFlow("")
    val emojiSuggestions: StateFlow<List<Account.EmojiMedia>> by lazy {
        account!!
            .myEmojis
            .combine(emojiSearch) { list, search ->
                if (search.length == 1) {
                    list
                } else if (search.isNotEmpty()) {
                    val code = search.removePrefix(":")
                    list.filter { it.code.startsWith(code) }
                } else {
                    emptyList()
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )
    }

    // DMs
    var wantsDirectMessage by mutableStateOf(false)
    var toUsers by mutableStateOf(TextFieldValue(""))
    var subject by mutableStateOf(TextFieldValue(""))

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Polls
    var canUsePoll by mutableStateOf(false)
    var wantsPoll by mutableStateOf(false)
    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = newStateMapPollOptions()
    var valueMaximum by mutableStateOf<Int?>(null)
    var valueMinimum by mutableStateOf<Int?>(null)
    var consensusThreshold: Int? = null
    var closedAt: Int? = null

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
    var condition by
        mutableStateOf<ClassifiedsEvent.CONDITION>(ClassifiedsEvent.CONDITION.USED_LIKE_NEW)

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    var forwardZapTo by mutableStateOf<Split<User>>(Split())
    var forwardZapToEditting by mutableStateOf(TextFieldValue(""))

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationState.LocationResult>? = null
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    var zapRaiserAmount by mutableStateOf<Long?>(null)

    // NIP17 Wrapped DMs / Group messages
    var nip17 by mutableStateOf(false)

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
                if (replyNote.event is BaseTextNoteEvent) {
                    this.eTags = (replyNote.replyTo ?: emptyList()).plus(replyNote)
                } else {
                    this.eTags = listOf(replyNote)
                }

                if (replyNote.event !is CommunityDefinitionEvent) {
                    replyNote.author?.let { replyUser ->
                        val currentMentions =
                            (replyNote.event as? TextNoteEvent)
                                ?.mentions()
                                ?.filter { it.isNotEmpty() }
                                ?.map { LocalCache.getOrCreateUser(it) }
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
            canUsePoll = originalNote?.event !is PrivateDmEvent && originalNote?.channelHex() == null
            multiOrchestrator = null

            quote?.let {
                message = TextFieldValue(message.text + "\nnostr:${it.toNEvent()}")
                urlPreview = findUrlInMessage()

                it.author?.let { quotedUser ->
                    if (quotedUser.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                        if (forwardZapTo.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                            forwardZapTo.addItem(quotedUser)
                        }
                        if (forwardZapTo.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) {
                            forwardZapTo.addItem(accountViewModel.userProfile())
                        }

                        val pos = forwardZapTo.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                        forwardZapTo.updatePercentage(pos, 0.9f)
                    }
                }
            }

            fork?.let {
                message = TextFieldValue(version?.event?.content ?: it.event?.content ?: "")
                urlPreview = findUrlInMessage()

                it.event?.isSensitiveOrNSFW()?.let {
                    if (it) wantsToMarkAsSensitive = true
                }

                it.event?.zapraiserAmount()?.let {
                    zapRaiserAmount = it
                }

                it.event?.zapSplitSetup()?.let {
                    val totalWeight = it.sumOf { if (it.isLnAddress) 0.0 else it.weight }

                    it.forEach {
                        if (!it.isLnAddress) {
                            forwardZapTo.addItem(LocalCache.getOrCreateUser(it.lnAddressOrPubKeyHex), (it.weight / totalWeight).toFloat())
                        }
                    }
                }

                // Only adds if it is not already set up.
                if (forwardZapTo.items.isEmpty()) {
                    it.author?.let { forkedAuthor ->
                        if (forkedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                            if (forwardZapTo.items.none { it.key.pubkeyHex == forkedAuthor.pubkeyHex }) forwardZapTo.addItem(forkedAuthor)
                            if (forwardZapTo.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) forwardZapTo.addItem(accountViewModel.userProfile())

                            val pos = forwardZapTo.items.indexOfFirst { it.key.pubkeyHex == forkedAuthor.pubkeyHex }
                            forwardZapTo.updatePercentage(pos, 0.8f)
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

            if (!forwardZapTo.items.isEmpty()) {
                wantsForwardZapTo = true
            }
        }
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
        forwardZapTo = Split()
        localfowardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.addItem(user, value)
        }
        forwardZapToEditting = TextFieldValue("")
        wantsForwardZapTo = localfowardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.tags.any { it.size > 1 && it[0] == "content-warning" }

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapraiser = draftEvent.tags.filter { it.size > 1 && it[0] == "zapraiser" }
        wantsZapraiser = zapraiser.isNotEmpty()
        zapRaiserAmount = null
        if (wantsZapraiser) {
            zapRaiserAmount = zapraiser.first()[1].toLongOrNull() ?: 0
        }

        eTags =
            draftEvent.tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) != "fork" }.mapNotNull {
                val note = LocalCache.checkGetOrCreateNote(it[1])
                note
            }

        if (draftEvent !is PrivateDmEvent && draftEvent !is NIP17Group) {
            pTags =
                draftEvent.tags.filter { it.size > 1 && it[0] == "p" }.map {
                    LocalCache.getOrCreateUser(it[1])
                }
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

        canUsePoll = originalNote?.event !is PrivateDmEvent && originalNote?.channelHex() == null

        if (forwardZapTo.items.isNotEmpty()) {
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
                valueMaximum = it[1].toInt()
            } else if (it[0] == "value_minimum") {
                valueMinimum = it[1].toInt()
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
        condition = ClassifiedsEvent.CONDITION.entries.firstOrNull {
            it.value ==
                draftEvent
                    .tags
                    .filter { it.size > 1 && it[0] == "condition" }
                    .map { it[1] }
                    .firstOrNull()
        } ?: ClassifiedsEvent.CONDITION.USED_LIKE_NEW

        wantsDirectMessage = draftEvent is PrivateDmEvent || draftEvent is NIP17Group

        draftEvent.subject()?.let {
            subject = TextFieldValue()
        }

        message =
            if (draftEvent is PrivateDmEvent) {
                val recepientNpub = draftEvent.verifiedRecipientPubKey()?.let { Hex.decode(it).toNpub() }
                toUsers = TextFieldValue("@$recepientNpub")
                TextFieldValue(draftEvent.cachedContentFor(accountViewModel.account.signer) ?: "")
            } else {
                TextFieldValue(draftEvent.content)
            }

        requiresNIP17 = draftEvent is NIP17Group
        nip17 = draftEvent is NIP17Group

        if (draftEvent is NIP17Group) {
            toUsers =
                TextFieldValue(
                    draftEvent.groupMembers().mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
                )
        }

        urlPreview = findUrlInMessage()
    }

    fun sendPost(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            innerSendPost(relayList, null)
            accountViewModel?.deleteDraft(draftTag)
            cancel()
        }
    }

    fun sendDraft(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch {
            sendDraftSync(relayList)
        }
    }

    suspend fun sendDraftSync(relayList: List<RelaySetupInfo>) {
        innerSendPost(relayList, draftTag)
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

        val toUsersTagger = NewMessageTagger(toUsers.text, null, null, null, accountViewModel!!)
        toUsersTagger.run()
        val dmUsers = toUsersTagger.pTags

        val zapReceiver =
            if (wantsForwardZapTo) {
                forwardZapTo.items.mapNotNull { split ->
                    if (split.percentage > 0.00001) {
                        val homeRelay =
                            accountViewModel?.getRelayListFor(split.key)?.writeRelays()?.firstOrNull()
                                ?: split.key.relaysBeingUsed.keys
                                    .firstOrNull { !it.contains("localhost") }

                        ZapSplitSetup(
                            lnAddressOrPubKeyHex = split.key.pubkeyHex,
                            relay = homeRelay,
                            weight = round(split.percentage.toDouble() * 10000) / 10000,
                            isLnAddress = false,
                        )
                    } else {
                        null
                    }
                }
            } else {
                null
            }

        val geoHash = (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString()
        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount else null

        nip95attachments.forEach {
            if (eTags?.contains(LocalCache.getNoteIfExists(it.second.id)) == true) {
                account?.sendNip95(it.first, it.second, relayList)
            }
        }

        val emojis = findEmoji(tagger.message, account?.myEmojis?.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filter { it.url in urls.toSet() }

        val replyingTo = originalNote

        if (replyingTo?.event is CommentEvent || (replyingTo?.event is Event && replyingTo.event is RootScope)) {
            account?.sendReplyComment(
                message = tagger.message,
                replyingTo = replyingTo,
                directMentionsUsers = tagger.directMentionsUsers,
                directMentionsNotes = tagger.directMentionsNotes,
                imetas = usedAttachments,
                geohash = geoHash,
                zapReceiver = zapReceiver,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = localZapRaiserAmount,
                relayList = relayList,
                emojis = emojis,
                draftTag = localDraft,
            )
        } else if (wantsExclusiveGeoPost && geoHash != null && (originalNote == null || originalNote?.event is CommentEvent)) {
            account?.sendGeoComment(
                message = tagger.message,
                geohash = geoHash,
                replyingTo = originalNote,
                directMentionsUsers = tagger.directMentionsUsers,
                directMentionsNotes = tagger.directMentionsNotes,
                imetas = usedAttachments,
                zapReceiver = zapReceiver,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = localZapRaiserAmount,
                relayList = relayList,
                emojis = emojis,
                draftTag = localDraft,
            )
        } else if (originalNote?.channelHex() != null) {
            if (originalNote is AddressableEvent && originalNote?.address() != null) {
                account?.sendLiveMessage(
                    message = tagger.message,
                    toChannel = originalNote?.address()!!,
                    replyTo = tagger.eTags,
                    mentions = tagger.pTags,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            } else {
                account?.sendChannelMessage(
                    message = tagger.message,
                    toChannel = tagger.channelHex!!,
                    replyTo = tagger.eTags,
                    mentions = tagger.pTags,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    directMentions = tagger.directMentions,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            }
        } else if (originalNote?.event is PrivateDmEvent) {
            account?.sendPrivateMessage(
                message = tagger.message,
                toUser = originalNote!!.author!!,
                replyingTo = originalNote!!,
                mentions = tagger.pTags,
                zapReceiver = zapReceiver,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = localZapRaiserAmount,
                geohash = geoHash,
                imetas = usedAttachments,
                draftTag = localDraft,
            )
        } else if (originalNote?.event is NIP17Group) {
            account?.sendNIP17PrivateMessage(
                message = tagger.message,
                toUsers = (originalNote?.event as NIP17Group).groupMembers().toList(),
                subject = subject.text.ifBlank { null },
                replyingTo = originalNote!!,
                mentions = tagger.pTags,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapReceiver = zapReceiver,
                zapRaiserAmount = localZapRaiserAmount,
                geohash = geoHash,
                imetas = usedAttachments,
                emojis = emojis,
                draftTag = localDraft,
            )
        } else if (!dmUsers.isNullOrEmpty()) {
            if (nip17 || dmUsers.size > 1) {
                account?.sendNIP17PrivateMessage(
                    message = tagger.message,
                    toUsers = dmUsers.map { it.pubkeyHex },
                    subject = subject.text.ifBlank { null },
                    replyingTo = tagger.eTags?.firstOrNull(),
                    mentions = tagger.pTags,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapReceiver = zapReceiver,
                    zapRaiserAmount = localZapRaiserAmount,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            } else {
                account?.sendPrivateMessage(
                    message = tagger.message,
                    toUser = dmUsers.first().pubkeyHex,
                    replyingTo = originalNote,
                    mentions = tagger.pTags,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapReceiver = zapReceiver,
                    zapRaiserAmount = localZapRaiserAmount,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    draftTag = localDraft,
                )
            }
        } else if (originalNote?.event is GitIssueEvent) {
            val originalNoteEvent = originalNote?.event as GitIssueEvent
            // adds markers
            val rootId =
                originalNoteEvent.rootIssueOrPatch() // if it has a marker as root
                    ?: originalNote
                        ?.replyTo
                        ?.firstOrNull { it.event != null && it.replyTo?.isEmpty() == true }
                        ?.idHex // if it has loaded events with zero replies in the reply list
                    ?: originalNote?.replyTo?.firstOrNull()?.idHex // old rules, first item is root.
                    ?: originalNote?.idHex

            val replyId = originalNote?.idHex

            val replyToSet =
                if (forkedFromNote != null) {
                    (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                } else {
                    tagger.eTags
                }

            val repositoryAddress = originalNoteEvent.repository()

            account?.sendGitReply(
                message = tagger.message,
                replyTo = replyToSet,
                mentions = tagger.pTags,
                repository = repositoryAddress,
                zapReceiver = zapReceiver,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = localZapRaiserAmount,
                replyingTo = replyId,
                root = rootId,
                directMentions = tagger.directMentions,
                forkedFrom = forkedFromNote?.event as? Event,
                relayList = relayList,
                geohash = geoHash,
                imetas = usedAttachments,
                emojis = emojis,
                draftTag = localDraft,
            )
        } else if (originalNote?.event is TorrentCommentEvent) {
            val originalNoteEvent = originalNote?.event as TorrentCommentEvent
            // adds markers
            val rootId =
                originalNoteEvent.torrent() // if it has a marker as root
                    ?: originalNote
                        ?.replyTo
                        ?.firstOrNull { it.event != null && it.replyTo?.isEmpty() == true }
                        ?.idHex // if it has loaded events with zero replies in the reply list
                    ?: originalNote?.replyTo?.firstOrNull()?.idHex // old rules, first item is root.
                    ?: originalNote?.idHex

            if (rootId != null) {
                // There must be a torrent ID
                val replyId = originalNote?.idHex

                val replyToSet =
                    if (forkedFromNote != null) {
                        (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                    } else {
                        tagger.eTags
                    }

                account?.sendTorrentComment(
                    message = tagger.message,
                    replyTo = replyToSet,
                    mentions = tagger.pTags,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    replyingTo = replyId,
                    root = rootId,
                    directMentions = tagger.directMentions,
                    forkedFrom = forkedFromNote?.event as? Event,
                    relayList = relayList,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            }
        } else if (originalNote?.event is TorrentEvent) {
            val originalNoteEvent = originalNote?.event as TorrentEvent
            // adds markers
            val rootId = originalNoteEvent.id

            val replyToSet =
                if (forkedFromNote != null) {
                    (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                } else {
                    tagger.eTags
                }

            account?.sendTorrentComment(
                message = tagger.message,
                replyTo = replyToSet,
                mentions = tagger.pTags,
                zapReceiver = zapReceiver,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = localZapRaiserAmount,
                replyingTo = null,
                root = rootId,
                directMentions = tagger.directMentions,
                forkedFrom = forkedFromNote?.event as? Event,
                relayList = relayList,
                geohash = geoHash,
                imetas = usedAttachments,
                emojis = emojis,
                draftTag = localDraft,
            )
        } else {
            if (wantsPoll) {
                account?.sendPoll(
                    message = tagger.message,
                    replyTo = tagger.eTags,
                    mentions = tagger.pTags,
                    pollOptions = pollOptions,
                    valueMaximum = valueMaximum,
                    valueMinimum = valueMinimum,
                    consensusThreshold = consensusThreshold,
                    closedAt = closedAt,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    relayList = relayList,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            } else if (wantsProduct) {
                account?.sendClassifieds(
                    title = title.text,
                    price = Price(price.text, "SATS", null),
                    condition = condition,
                    message = tagger.message,
                    replyTo = tagger.eTags,
                    mentions = tagger.pTags,
                    location = locationText.text,
                    category = category.text,
                    directMentions = tagger.directMentions,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    relayList = relayList,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            } else {
                // adds markers
                val rootId =
                    (originalNote?.event as? TextNoteEvent)?.root() // if it has a marker as root
                        ?: originalNote
                            ?.replyTo
                            ?.firstOrNull { it.event != null && it.replyTo?.isEmpty() == true }
                            ?.idHex // if it has loaded events with zero replies in the reply list
                        ?: originalNote?.replyTo?.firstOrNull()?.idHex // old rules, first item is root.
                        ?: originalNote?.idHex

                val replyId = originalNote?.idHex

                val replyToSet =
                    if (forkedFromNote != null) {
                        (listOfNotNull(forkedFromNote) + (tagger.eTags ?: emptyList())).ifEmpty { null }
                    } else {
                        tagger.eTags
                    }

                account?.sendPost(
                    message = tagger.message,
                    replyTo = replyToSet,
                    mentions = tagger.pTags,
                    tags = null,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    replyingTo = replyId,
                    root = rootId,
                    directMentions = tagger.directMentions,
                    forkedFrom = forkedFromNote?.event as? Event,
                    relayList = relayList,
                    geohash = geoHash,
                    imetas = usedAttachments,
                    emojis = emojis,
                    draftTag = localDraft,
                )
            }
        }
    }

    fun findEmoji(
        message: String,
        myEmojiSet: List<Account.EmojiMedia>?,
    ): List<EmojiUrl> {
        if (myEmojiSet == null) return emptyList()
        return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
            myEmojiSet.firstOrNull { it.code == possibleEmoji }?.let { EmojiUrl(it.code, it.url.url) }
        }
    }

    fun upload(
        alt: String?,
        sensitiveContent: Boolean,
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
                    sensitiveContent,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach {
                    if (it.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        account?.createNip95(it.result.bytes, headerInfo = it.result.fileHeader, alt, sensitiveContent) { nip95 ->
                            nip95attachments = nip95attachments + nip95
                            val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                            note?.let {
                                message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                            }

                            urlPreview = findUrlInMessage()
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
                                    // TODO: Support Reasons on images
                                    if (sensitiveContent) sensitiveContent("")
                                }.build()

                        iMetaAttachments = iMetaAttachments.filter { it.url != iMeta.url } + iMeta

                        message = message.insertUrlAtCursor(it.result.url)
                        urlPreview = findUrlInMessage()
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
        toUsers = TextFieldValue("")
        subject = TextFieldValue("")

        forkedFromNote = null

        multiOrchestrator = null
        urlPreview = null
        isUploadingImage = false
        pTags = null

        wantsDirectMessage = false

        wantsPoll = false
        zapRecipients = mutableStateListOf<HexKey>()
        pollOptions = newStateMapPollOptions()
        valueMaximum = null
        valueMinimum = null
        consensusThreshold = null
        closedAt = null

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount = null

        wantsProduct = false
        condition = ClassifiedsEvent.CONDITION.USED_LIKE_NEW
        locationText = TextFieldValue("")
        title = TextFieldValue("")
        category = TextFieldValue("")
        price = TextFieldValue("")

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsExclusiveGeoPost = false
        forwardZapTo = Split()
        forwardZapToEditting = TextFieldValue("")

        userSuggestions = emptyList()
        userSuggestionAnchor = null
        userSuggestionsMainMessage = null

        if (emojiSearch.value.isNotEmpty()) {
            emojiSearch.tryEmit("")
        }

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

    open fun findUrlInMessage(): String? = RichTextParser().parseValidUrls(message.text).firstOrNull()

    open fun removeFromReplyList(userToRemove: User) {
        pTags = pTags?.filter { it != userToRemove }
    }

    private fun saveDraft() {
        draftTextChanges.trySend("")
    }

    open fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    open fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreview = findUrlInMessage()

        if (it.selection.collapsed) {
            val lastWord =
                it.text
                    .substring(0, it.selection.end)
                    .substringAfterLast("\n")
                    .substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                val prefix = lastWord.removePrefix("@")
                NostrSearchEventOrUserDataSource.search(prefix)
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions = LocalCache.findUsersStartingWith(prefix, account)
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }

            if (lastWord.startsWith(":")) {
                emojiSearch.tryEmit(lastWord)
            } else {
                if (emojiSearch.value.isNotBlank()) {
                    emojiSearch.tryEmit("")
                }
            }
        }

        saveDraft()
    }

    open fun updateToUsers(it: TextFieldValue) {
        toUsers = it

        if (it.selection.collapsed) {
            val lastWord =
                it.text
                    .substring(0, it.selection.end)
                    .substringAfterLast("\n")
                    .substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.TO_USERS
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                val prefix = lastWord.removePrefix("@")
                NostrSearchEventOrUserDataSource.search(prefix)
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache
                            .findUsersStartingWith(prefix, account)
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }
        }
        saveDraft()
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
        saveDraft()
    }

    open fun updateZapForwardTo(it: TextFieldValue) {
        forwardZapToEditting = it
        if (it.selection.collapsed) {
            val lastWord = it.text
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            if (lastWord.length > 2) {
                val prefix = lastWord.removePrefix("@")
                NostrSearchEventOrUserDataSource.search(prefix)
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache.findUsersStartingWith(prefix, account)
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestionAnchor?.let {
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord =
                    message.text
                        .substring(0, it.end)
                        .substringAfterLast("\n")
                        .substringAfterLast(" ")
                val lastWordStart = it.end - lastWord.length
                val wordToInsert = "@${item.pubkeyNpub()}"

                message =
                    TextFieldValue(
                        message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                        TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                    )
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.addItem(item)
                forwardZapToEditting = TextFieldValue("")
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.TO_USERS) {
                val lastWord =
                    toUsers.text
                        .substring(0, it.end)
                        .substringAfterLast("\n")
                        .substringAfterLast(" ")
                val lastWordStart = it.end - lastWord.length
                val wordToInsert = "@${item.pubkeyNpub()}"

                toUsers =
                    TextFieldValue(
                        toUsers.text.replaceRange(lastWordStart, it.end, wordToInsert),
                        TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                    )

                val relayList = (LocalCache.getAddressableNoteIfExists(AdvertisedRelayListEvent.createAddressTag(item.pubkeyHex))?.event as? AdvertisedRelayListEvent)?.readRelays()
                nip17 = relayList != null
            }

            userSuggestionAnchor = null
            userSuggestionsMainMessage = null
            userSuggestions = emptyList()
        }

        saveDraft()
    }

    open fun autocompleteWithEmoji(item: Account.EmojiMedia) {
        userSuggestionAnchor?.let {
            val lastWord =
                message.text
                    .substring(0, it.end)
                    .substringAfterLast("\n")
                    .substringAfterLast(" ")
            val lastWordStart = it.end - lastWord.length
            val wordToInsert = ":${item.code}:"

            message =
                TextFieldValue(
                    message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                    TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                )

            userSuggestionAnchor = null
            emojiSearch.tryEmit("")
        }

        saveDraft()
    }

    open fun autocompleteWithEmojiUrl(item: Account.EmojiMedia) {
        userSuggestionAnchor?.let {
            val lastWord =
                message.text
                    .substring(0, it.end)
                    .substringAfterLast("\n")
                    .substringAfterLast(" ")
            val lastWordStart = it.end - lastWord.length
            val wordToInsert = item.url.url + " "

            viewModelScope.launch(Dispatchers.IO) {
                val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(item.url.url)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))

                val forceProxy = accountViewModel?.account?.shouldUseTorForImageDownload() ?: false
                val imeta =
                    FileHeader.prepare(item.url.url, mimeType, null, forceProxy).getOrNull()?.let {
                        IMetaTagBuilder(item.url.url)
                            .apply {
                                hash(it.hash)
                                size(it.size)
                                it.mimeType?.let { mimeType(it) }
                                it.dim?.let { dims(it) }
                                it.blurHash?.let { blurhash(it.blurhash) }
                            }.build()
                    }

                if (imeta != null) {
                    iMetaAttachments += imeta
                }
            }

            message =
                TextFieldValue(
                    message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                    TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                )

            userSuggestionAnchor = null
            emojiSearch.tryEmit("")
        }

        urlPreview = findUrlInMessage()

        saveDraft()
    }

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, String> = mutableStateMapOf(Pair(0, ""), Pair(1, ""))

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount != null) &&
            (!wantsDirectMessage || !toUsers.text.isNullOrBlank()) &&
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

    fun locationFlow(): StateFlow<LocationState.LocationResult> {
        if (location == null) {
            location = Amethyst.instance.locationManager.geohashStateFlow
        }

        return location!!
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
        if (message.text.isNotBlank()) {
            saveDraft()
        }
    }

    fun updateMinZapAmountForPoll(textMin: String) {
        if (textMin.isNotEmpty()) {
            try {
                val int = textMin.toInt()
                if (int < 1) {
                    valueMinimum = null
                } else {
                    valueMinimum = int
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        } else {
            valueMinimum = null
        }

        checkMinMax()
        saveDraft()
    }

    fun updateMaxZapAmountForPoll(textMax: String) {
        if (textMax.isNotEmpty()) {
            try {
                val int = textMax.toInt()
                if (int < 1) {
                    valueMaximum = null
                } else {
                    valueMaximum = int
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        } else {
            valueMaximum = null
        }

        checkMinMax()
        saveDraft()
    }

    fun checkMinMax() {
        if ((valueMinimum ?: 0) > (valueMaximum ?: Int.MAX_VALUE)) {
            isValidvalueMinimum.value = false
            isValidvalueMaximum.value = false
        } else {
            isValidvalueMinimum.value = true
            isValidvalueMaximum.value = true
        }
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

    fun updateCondition(newCondition: ClassifiedsEvent.CONDITION) {
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
}

enum class GeohashPrecision(
    val digits: Int,
) {
    KM_5000_X_5000(1), // 5,000km	×	5,000km
    KM_1250_X_625(2), // 1,250km	×	625km
    KM_156_X_156(3), //   156km	×	156km
    KM_39_X_19(4), //  39.1km	×	19.5km
    KM_5_X_5(5), //  4.89km	×	4.89km
    M_1000_X_600(6), //  1.22km	×	0.61km
    M_153_X_153(7), //    153m	×	153m
    M_38_X_19(8), //   38.2m	×	19.1m
    M_5_X_5(9), //   4.77m	×	4.77m
    MM_1000_X_1000(10), //   1.19m	×	0.596m
    MM_149_X_149(11), //   149mm	×	149mm
    MM_37_X_18(12), //  37.2mm	×	18.6mm
}
