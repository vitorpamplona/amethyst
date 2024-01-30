/**
 * Copyright (c) 2023 Vitor Pamplona
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
import android.net.Uri
import android.util.Log
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
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.LocationUtil
import com.vitorpamplona.amethyst.service.Nip96Uploader
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.noProtocolUrlValidator
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.MediaCompressor
import com.vitorpamplona.amethyst.ui.components.Split
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.Price
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.ZapSplitSetup
import com.vitorpamplona.quartz.events.findURLs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    TO_USERS,
}

@Stable
open class NewPostViewModel() : ViewModel() {
    var accountViewModel: AccountViewModel? = null
    var account: Account? = null
    var requiresNIP24: Boolean = false

    var originalNote: Note? = null

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    var nip94attachments by mutableStateOf<List<FileHeaderEvent>>(emptyList())
    var nip95attachments by
        mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError =
        MutableSharedFlow<String?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var userSuggestions by mutableStateOf<List<User>>(emptyList())
    var userSuggestionAnchor: TextRange? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    // DMs
    var wantsDirectMessage by mutableStateOf(false)
    var toUsers by mutableStateOf(TextFieldValue(""))
    var subject by mutableStateOf(TextFieldValue(""))

    // Images and Videos
    var contentToAddUrl by mutableStateOf<Uri?>(null)

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
    var locUtil: LocationUtil? = null
    var location: Flow<String>? = null

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    var zapRaiserAmount by mutableStateOf<Long?>(null)

    // NIP24 Wrapped DMs / Group messages
    var nip24 by mutableStateOf(false)

    open fun load(
        accountViewModel: AccountViewModel,
        replyingTo: Note?,
        quote: Note?,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account

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
                        (replyNote.event as? TextNoteEvent)?.mentions()?.map { LocalCache.getOrCreateUser(it) }
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

        quote?.let {
            message = TextFieldValue(message.text + "\nnostr:${it.toNEvent()}")
            urlPreview = findUrlInMessage()
        }

        canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().info?.lnAddress() != null
        canUsePoll = originalNote?.event !is PrivateDmEvent && originalNote?.channelHex() == null
        contentToAddUrl = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsZapraiser = false
        zapRaiserAmount = null
        forwardZapTo = Split()
        forwardZapToEditting = TextFieldValue("")
    }

    fun sendPost(relayList: List<Relay>? = null) {
        viewModelScope.launch(Dispatchers.IO) { innerSendPost(relayList) }
    }

    suspend fun innerSendPost(relayList: List<Relay>? = null) {
        if (accountViewModel == null) {
            cancel()
            return
        }

        val tagger =
            NewMessageTagger(message.text, pTags, eTags, originalNote?.channelHex(), accountViewModel!!)
        tagger.run()

        val toUsersTagger = NewMessageTagger(toUsers.text, null, null, null, accountViewModel!!)
        toUsersTagger.run()
        val dmUsers = toUsersTagger.pTags

        val zapReceiver =
            if (wantsForwardZapTo) {
                forwardZapTo.items.map {
                    ZapSplitSetup(
                        lnAddressOrPubKeyHex = it.key.pubkeyHex,
                        relay = it.key.relaysBeingUsed.keys.firstOrNull(),
                        weight = it.percentage.toDouble(),
                        isLnAddress = false,
                    )
                }
            } else {
                null
            }

        val geoLocation = locUtil?.locationStateFlow?.value
        val geoHash =
            if (wantsToAddGeoHash && geoLocation != null) {
                geoLocation.toGeoHash(GeohashPrecision.KM_5_X_5.digits).toString()
            } else {
                null
            }

        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount else null

        nip95attachments.forEach {
            if (eTags?.contains(LocalCache.getNoteIfExists(it.second.id)) == true) {
                account?.sendNip95(it.first, it.second, relayList)
            }
        }

        val urls = findURLs(tagger.message)
        val usedAttachments = nip94attachments.filter { it.urls().intersect(urls.toSet()).isNotEmpty() }
        usedAttachments.forEach { account?.sendHeader(it, relayList, {}) }

        if (originalNote?.channelHex() != null) {
            if (originalNote is AddressableEvent && originalNote?.address() != null) {
                account?.sendLiveMessage(
                    tagger.message,
                    originalNote?.address()!!,
                    tagger.eTags,
                    tagger.pTags,
                    zapReceiver,
                    wantsToMarkAsSensitive,
                    localZapRaiserAmount,
                    geoHash,
                    nip94attachments = usedAttachments,
                )
            } else {
                account?.sendChannelMessage(
                    tagger.message,
                    tagger.channelHex!!,
                    tagger.eTags,
                    tagger.pTags,
                    zapReceiver,
                    wantsToMarkAsSensitive,
                    localZapRaiserAmount,
                    geoHash,
                    nip94attachments = usedAttachments,
                )
            }
        } else if (originalNote?.event is PrivateDmEvent) {
            account?.sendPrivateMessage(
                tagger.message,
                originalNote!!.author!!,
                originalNote!!,
                tagger.pTags,
                zapReceiver,
                wantsToMarkAsSensitive,
                localZapRaiserAmount,
                geoHash,
            )
        } else if (originalNote?.event is ChatMessageEvent) {
            val receivers =
                (originalNote?.event as ChatMessageEvent)
                    .recipientsPubKey()
                    .plus(originalNote?.author?.pubkeyHex)
                    .filterNotNull()
                    .toSet()
                    .toList()

            account?.sendNIP24PrivateMessage(
                message = tagger.message,
                toUsers = receivers,
                subject = subject.text.ifBlank { null },
                replyingTo = originalNote!!,
                mentions = tagger.pTags,
                wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                zapReceiver = zapReceiver,
                zapRaiserAmount = localZapRaiserAmount,
                geohash = geoHash,
            )
        } else if (!dmUsers.isNullOrEmpty()) {
            if (nip24 || dmUsers.size > 1) {
                account?.sendNIP24PrivateMessage(
                    message = tagger.message,
                    toUsers = dmUsers.map { it.pubkeyHex },
                    subject = subject.text.ifBlank { null },
                    replyingTo = tagger.eTags?.firstOrNull(),
                    mentions = tagger.pTags,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapReceiver = zapReceiver,
                    zapRaiserAmount = localZapRaiserAmount,
                    geohash = geoHash,
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
                )
            }
        } else {
            if (wantsPoll) {
                account?.sendPoll(
                    tagger.message,
                    tagger.eTags,
                    tagger.pTags,
                    pollOptions,
                    valueMaximum,
                    valueMinimum,
                    consensusThreshold,
                    closedAt,
                    zapReceiver,
                    wantsToMarkAsSensitive,
                    localZapRaiserAmount,
                    relayList,
                    geoHash,
                    nip94attachments = usedAttachments,
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
                    nip94attachments = usedAttachments,
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

                account?.sendPost(
                    message = tagger.message,
                    replyTo = tagger.eTags,
                    mentions = tagger.pTags,
                    tags = null,
                    zapReceiver = zapReceiver,
                    wantsToMarkAsSensitive = wantsToMarkAsSensitive,
                    zapRaiserAmount = localZapRaiserAmount,
                    replyingTo = replyId,
                    root = rootId,
                    directMentions = tagger.directMentions,
                    relayList = relayList,
                    geohash = geoHash,
                    nip94attachments = usedAttachments,
                )
            }
        }

        cancel()
    }

    fun upload(
        galleryUri: Uri,
        alt: String?,
        sensitiveContent: Boolean,
        isPrivate: Boolean = false,
        server: ServerOption,
        context: Context,
    ) {
        isUploadingImage = true
        contentToAddUrl = null

        val contentResolver = context.contentResolver
        val contentType = contentResolver.getType(galleryUri)

        viewModelScope.launch(Dispatchers.IO) {
            MediaCompressor()
                .compress(
                    galleryUri,
                    contentType,
                    context.applicationContext,
                    onReady = { fileUri, contentType, size ->
                        if (server.isNip95) {
                            contentResolver.openInputStream(fileUri)?.use {
                                createNIP95Record(it.readBytes(), contentType, alt, sensitiveContent)
                            }
                        } else {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val result =
                                        Nip96Uploader(account)
                                            .uploadImage(
                                                uri = fileUri,
                                                contentType = contentType,
                                                size = size,
                                                alt = alt,
                                                sensitiveContent = if (sensitiveContent) "" else null,
                                                server = server.server,
                                                contentResolver = contentResolver,
                                                onProgress = {},
                                            )

                                    if (!isPrivate) {
                                        createNIP94Record(
                                            uploadingResult = result,
                                            localContentType = contentType,
                                            alt = alt,
                                            sensitiveContent = sensitiveContent,
                                        )
                                    } else {
                                        noNIP94(
                                            uploadingResult = result,
                                            localContentType = contentType,
                                            alt = alt,
                                            sensitiveContent = sensitiveContent,
                                        )
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "ImageUploader",
                                        "Failed to upload ${e.message}",
                                        e,
                                    )
                                    isUploadingImage = false
                                    viewModelScope.launch {
                                        imageUploadingError.emit("Failed to upload: ${e.message}")
                                    }
                                }
                            }
                        }
                    },
                    onError = {
                        isUploadingImage = false
                        viewModelScope.launch { imageUploadingError.emit(it) }
                    },
                )
        }
    }

    open fun cancel() {
        message = TextFieldValue("")
        toUsers = TextFieldValue("")
        subject = TextFieldValue("")

        contentToAddUrl = null
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
        price = TextFieldValue("")

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        forwardZapTo = Split()
        forwardZapToEditting = TextFieldValue("")

        userSuggestions = emptyList()
        userSuggestionAnchor = null
        userSuggestionsMainMessage = null

        NostrSearchEventOrUserDataSource.clear()
    }

    open fun findUrlInMessage(): String? {
        return message.text.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
            }
        }
    }

    open fun removeFromReplyList(userToRemove: User) {
        pTags = pTags?.filter { it != userToRemove }
    }

    open fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreview = findUrlInMessage()

        if (it.selection.collapsed) {
            val lastWord =
                it.text.substring(0, it.selection.end).substringAfterLast("\n").substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                NostrSearchEventOrUserDataSource.search(lastWord.removePrefix("@"))
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache.findUsersStartingWith(lastWord.removePrefix("@"))
                            .sortedWith(compareBy({ account?.isFollowing(it) }, { it.toBestDisplayName() }))
                            .reversed()
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }
        }
    }

    open fun updateToUsers(it: TextFieldValue) {
        toUsers = it

        if (it.selection.collapsed) {
            val lastWord =
                it.text.substring(0, it.selection.end).substringAfterLast("\n").substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.TO_USERS
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                NostrSearchEventOrUserDataSource.search(lastWord.removePrefix("@"))
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache.findUsersStartingWith(lastWord.removePrefix("@"))
                            .sortedWith(compareBy({ account?.isFollowing(it) }, { it.toBestDisplayName() }))
                            .reversed()
                }
            } else {
                NostrSearchEventOrUserDataSource.clear()
                userSuggestions = emptyList()
            }
        }
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
    }

    open fun updateZapForwardTo(it: TextFieldValue) {
        forwardZapToEditting = it
        if (it.selection.collapsed) {
            val lastWord = it.text
            userSuggestionAnchor = it.selection
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            if (lastWord.length > 2) {
                NostrSearchEventOrUserDataSource.search(lastWord.removePrefix("@"))
                viewModelScope.launch(Dispatchers.IO) {
                    userSuggestions =
                        LocalCache.findUsersStartingWith(lastWord.removePrefix("@"))
                            .sortedWith(
                                compareBy(
                                    { account?.isFollowing(it) },
                                    { it.toBestDisplayName() },
                                ),
                            )
                            .reversed()
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
                    message.text.substring(0, it.end).substringAfterLast("\n").substringAfterLast(" ")
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
        /*
        val lastWord = forwardZapToEditting.text.substring(0, it.end).substringAfterLast("\n").substringAfterLast(" ")
        val lastWordStart = it.end - lastWord.length
        val wordToInsert = "@${item.pubkeyNpub()}"
        forwardZapTo = item

        forwardZapToEditting = TextFieldValue(
            forwardZapToEditting.text.replaceRange(lastWordStart, it.end, wordToInsert),
            TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length)
        )*/
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.TO_USERS) {
                val lastWord =
                    toUsers.text.substring(0, it.end).substringAfterLast("\n").substringAfterLast(" ")
                val lastWordStart = it.end - lastWord.length
                val wordToInsert = "@${item.pubkeyNpub()}"

                toUsers =
                    TextFieldValue(
                        toUsers.text.replaceRange(lastWordStart, it.end, wordToInsert),
                        TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
                    )
            }

            userSuggestionAnchor = null
            userSuggestionsMainMessage = null
            userSuggestions = emptyList()
        }
    }

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, String> {
        return mutableStateMapOf(Pair(0, ""), Pair(1, ""))
    }

    fun canPost(): Boolean {
        return message.text.isNotBlank() &&
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
            contentToAddUrl == null
    }

    fun includePollHashtagInMessage(
        include: Boolean,
        hashtag: String,
    ) {
        if (include) {
            updateMessage(TextFieldValue(message.text + " $hashtag"))
        } else {
            updateMessage(
                TextFieldValue(
                    message.text.replace(" $hashtag", "").replace(hashtag, ""),
                ),
            )
        }
    }

    suspend fun createNIP94Record(
        uploadingResult: Nip96Uploader.PartialEvent,
        localContentType: String?,
        alt: String?,
        sensitiveContent: Boolean,
    ) {
        // Images don't seem to be ready immediately after upload
        val imageUrl = uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)
        val remoteMimeType =
            uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "m" }?.get(1)?.ifBlank { null }
        val originalHash =
            uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "ox" }?.get(1)?.ifBlank { null }
        val dim =
            uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "dim" }?.get(1)?.ifBlank { null }
        val magnet =
            uploadingResult.tags
                ?.firstOrNull { it.size > 1 && it[0] == "magnet" }
                ?.get(1)
                ?.ifBlank { null }

        if (imageUrl.isNullOrBlank()) {
            Log.e("ImageDownload", "Couldn't download image from server")
            cancel()
            isUploadingImage = false
            viewModelScope.launch { imageUploadingError.emit("Failed to upload the image / video") }
            return
        }

        FileHeader.prepare(
            fileUrl = imageUrl,
            mimeType = remoteMimeType ?: localContentType,
            dimPrecomputed = dim,
            onReady = { header: FileHeader ->
                account?.createHeader(imageUrl, magnet, header, alt, sensitiveContent, originalHash) {
                        event,
                    ->
                    isUploadingImage = false
                    nip94attachments = nip94attachments + event
                    val contentWarning = if (sensitiveContent) "" else null
                    message =
                        TextFieldValue(
                            message.text +
                                "\n" +
                                addInlineMetadataAsNIP54(
                                    imageUrl,
                                    header.dim,
                                    header.mimeType,
                                    alt,
                                    header.blurHash,
                                    header.hash,
                                    contentWarning,
                                ),
                        )
                    urlPreview = findUrlInMessage()
                }
            },
            onError = {
                isUploadingImage = false
                viewModelScope.launch { imageUploadingError.emit("Failed to upload the image / video") }
            },
        )
    }

    suspend fun noNIP94(
        uploadingResult: Nip96Uploader.PartialEvent,
        localContentType: String?,
        alt: String?,
        sensitiveContent: Boolean,
    ) {
        // Images don't seem to be ready immediately after upload
        val imageUrl = uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)
        val remoteMimeType =
            uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "m" }?.get(1)?.ifBlank { null }
        val dim =
            uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "dim" }?.get(1)?.ifBlank { null }

        if (imageUrl.isNullOrBlank()) {
            Log.e("ImageDownload", "Couldn't download image from server")
            cancel()
            isUploadingImage = false
            viewModelScope.launch { imageUploadingError.emit("Server failed to return a url") }
            return
        }

        FileHeader.prepare(
            fileUrl = imageUrl,
            mimeType = remoteMimeType ?: localContentType,
            dimPrecomputed = dim,
            onReady = { header: FileHeader ->
                isUploadingImage = false
                val contentWarning = if (sensitiveContent) "" else null
                message =
                    TextFieldValue(
                        message.text +
                            "\n" +
                            addInlineMetadataAsNIP54(
                                imageUrl,
                                header.dim,
                                header.mimeType,
                                alt,
                                header.blurHash,
                                header.hash,
                                contentWarning,
                            ),
                    )
                urlPreview = findUrlInMessage()
            },
            onError = {
                isUploadingImage = false
                viewModelScope.launch { imageUploadingError.emit("Failed to upload the image / video") }
            },
        )
    }

    fun addInlineMetadataAsNIP54(
        imageUrl: String,
        dim: String?,
        m: String?,
        alt: String?,
        blurHash: String?,
        x: String?,
        sensitiveContent: String?,
    ): String {
        val extension =
            listOfNotNull(
                m?.ifBlank { null }?.let { "m=${URLEncoder.encode(it, "utf-8")}" },
                dim?.ifBlank { null }?.let { "dim=${URLEncoder.encode(it, "utf-8")}" },
                alt?.ifBlank { null }?.let { "alt=${URLEncoder.encode(it, "utf-8")}" },
                blurHash?.ifBlank { null }?.let { "blurhash=${URLEncoder.encode(it, "utf-8")}" },
                x?.ifBlank { null }?.let { "x=${URLEncoder.encode(it, "utf-8")}" },
                sensitiveContent?.let { "content-warning=${URLEncoder.encode(it, "utf-8")}" },
            )
                .joinToString("&")

        return if (imageUrl.contains("#")) {
            "$imageUrl&$extension"
        } else {
            "$imageUrl#$extension"
        }
    }

    fun createNIP95Record(
        bytes: ByteArray,
        mimeType: String?,
        alt: String?,
        sensitiveContent: Boolean,
    ) {
        if (bytes.size > 80000) {
            viewModelScope.launch {
                imageUploadingError.emit("Media is too big for NIP-95")
                isUploadingImage = false
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                mimeType,
                null,
                onReady = {
                    account?.createNip95(bytes, headerInfo = it, alt, sensitiveContent) { nip95 ->
                        nip95attachments = nip95attachments + nip95
                        val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                        isUploadingImage = false

                        note?.let { message = TextFieldValue(message.text + "\nnostr:" + it.toNEvent()) }

                        urlPreview = findUrlInMessage()
                    }
                },
                onError = {
                    isUploadingImage = false
                    viewModelScope.launch { imageUploadingError.emit("Failed to upload the image / video") }
                },
            )
        }
    }

    fun selectImage(uri: Uri) {
        contentToAddUrl = uri
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startLocation(context: Context) {
        locUtil = LocationUtil(context)
        locUtil?.let {
            location =
                it.locationStateFlow.mapLatest { it.toGeoHash(GeohashPrecision.KM_5_X_5.digits).toString() }
        }
        viewModelScope.launch(Dispatchers.IO) { locUtil?.start() }
    }

    fun stopLocation() {
        viewModelScope.launch(Dispatchers.IO) { locUtil?.stop() }
        location = null
        locUtil = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        viewModelScope.launch(Dispatchers.IO) { locUtil?.stop() }
        location = null
        locUtil = null
    }

    fun toggleNIP04And24() {
        if (requiresNIP24) {
            nip24 = true
        } else {
            nip24 = !nip24
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
}

enum class GeohashPrecision(val digits: Int) {
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
