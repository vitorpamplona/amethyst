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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send

import android.content.Context
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
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.SuspendableConfirmation
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.expiration.IExpiration
import com.vitorpamplona.amethyst.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.threading.buzzThread
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRoot
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.notify
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.notify
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Stable
open class ChannelNewMessageViewModel :
    ViewModel(),
    ILocationGrabber,
    IExpiration {
    val draftTag = DraftTagState()

    // Strong reference to the live cache note for the current draft tag (derived from the
    // versions flow below), so LocalCache cannot weakly collect it before a deletion needs it.
    var draftNote: AddressableNote? = null
        private set

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

    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account
    var channel: Channel? = null

    val replyTo = mutableStateOf<Note?>(null)

    // INLINE keeps the reply in the timeline (native reply); MINICHAT sends a kind-1111
    // thread comment that opens as a minichat. Only meaningful while replyTo is set.
    val replyMode = mutableStateOf(ReplyMode.INLINE)

    // When set, the composer is editing an existing Buzz stream message (kind 40002):
    // the next send publishes a kind-40003 edit targeting this note instead of a new
    // message. Only ever set for own messages on a Buzz-dialect relay (see editBuzzMessage).
    val editingBuzzMessage = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    val message = TextFieldState()
    var urlPreview by mutableStateOf<String?>(null)
    val isUploadingImage: Boolean get() = uploadState?.isUploadingImage ?: false
    val isUploadingFile: Boolean get() = uploadState?.isUploadingFile ?: false

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    var forwardZapTo by mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    val forwardZapToEditting = TextFieldState()

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    override var pickedGeoHash by mutableStateOf<String?>(null)
    var location: StateFlow<LocationState.LocationResult>? = null

    // Geohash location chat (Bitchat interop): messages are signed with an anonymous per-cell
    // identity (unless posting as self) and carry a small NIP-13 PoW + the n/t tags.
    var geohashNickname by mutableStateOf("")
    var geohashTeleported by mutableStateOf(false)
    var geohashPostAsSelf by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    var zapRaiserAmount by mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    open fun init(accountVM: AccountViewModel) {
        // The channel screens call this straight from their composable body, so it runs on the main
        // thread on every recomposition of that body. Guard against re-running the allocating setup
        // (new UserSuggestionState/EmojiSuggestionState/ChatFileUploadState) when nothing changed:
        // only (re)initialize when the account actually differs. Beyond the wasted allocations, a
        // blind re-init would also reset `uploadState` mid-upload, discarding in-flight progress.
        if (::accountViewModel.isInitialized && this.accountViewModel === accountVM) return

        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                priorityPubkeys = {
                    // Public channels have no membership; recent posters are the
                    // closest thing. The cutoff also bounds the note scan.
                    channel?.participatingAuthors(TimeUtils.oneMonthAgo())?.mapTo(mutableSetOf()) { it.pubkeyHex } ?: emptySet()
                },
            )

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)

        this.uploadState = ChatFileUploadState(account.settings.defaultFileServer, account.settings.stripLocationOnUpload)
    }

    open fun load(channel: Channel) {
        this.channel = channel
    }

    open fun reply(replyNote: Note) {
        replyTo.value = replyNote
        replyMode.value = ReplyMode.INLINE
        draftTag.newVersion()
    }

    fun toggleReplyMode() {
        replyMode.value = if (replyMode.value == ReplyMode.INLINE) ReplyMode.MINICHAT else ReplyMode.INLINE
    }

    fun clearReply() {
        replyTo.value = null
        replyMode.value = ReplyMode.INLINE
        draftTag.newVersion()
    }

    /**
     * Enters Buzz edit mode: pre-fills the composer with [note]'s current text and marks
     * the next send as a kind-40003 edit of it. Editing and replying are mutually
     * exclusive, so any pending reply is cleared. The caller gates this to the user's own
     * kind-40002 messages on a Buzz relay.
     */
    fun editBuzzMessage(note: Note) {
        replyTo.value = null
        replyMode.value = ReplyMode.INLINE
        editingBuzzMessage.value = note
        message.setTextAndPlaceCursorAtEnd(note.event?.content ?: "")
        draftTag.newVersion()
    }

    fun clearBuzzEdit() {
        editingBuzzMessage.value = null
        message.setTextAndPlaceCursorAtEnd("")
        draftTag.newVersion()
    }

    open fun editFromDraft(draft: Note) {
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
                    loadFromDraft(innerNote)
                }
            }
        }
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return

        val localForwardZapTo = draftEvent.tags.zapSplitSetup()
        val totalWeight = localForwardZapTo.sumOf { it.weight }
        forwardZapTo = SplitBuilder()
        localForwardZapTo.forEach {
            if (it is ZapSplitSetup) {
                val user = LocalCache.getOrCreateUser(it.pubKeyHex)
                forwardZapTo.addItem(user, (it.weight / totalWeight).toFloat())
            }
            // don't support edditing old-style splits.
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
        wantsZapraiser = zapraiser != null
        zapRaiserAmount = null
        if (zapraiser != null) {
            zapRaiserAmount = zapraiser
        }

        if (forwardZapTo.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        // Both event kinds extend BaseThreadedEvent and share `reply()`. Cast through
        // both candidates so the elvis result lands on the common supertype, then
        // load the addressed reply note in a single block.
        val threadedDraft =
            (draftEvent as? ChannelMessageEvent)
                ?: (draftEvent as? LiveActivitiesChatMessageEvent)
        threadedDraft?.reply()?.eventId?.let { replyId ->
            replyTo.value = accountViewModel.checkGetOrCreateNote(replyId)
        }

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreview = findUrlInMessage()
    }

    fun sendPost(onDone: suspend () -> Unit) {
        accountViewModel.launchSigner {
            sendPostSync()
            onDone()
        }
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        val channelRelays = channel?.relays() ?: emptySet()

        // A geohash cell with no resolvable relays has nowhere to publish. Bail before cancel() clears
        // the composer, so the user keeps their text (and draft) to retry rather than losing it silently.
        if (channel is GeohashChatChannel && channelRelays.isEmpty()) return

        val draftToDelete = draftNote
        cancel()

        val currentChannel = channel
        if (currentChannel is GeohashChatChannel) {
            // Geohash chat: sign with the anonymous per-cell identity (or the account when posting
            // as self) and mine the fixed 8-bit Bitchat PoW for that pubkey, instead of the account
            // signer + PUBLIC_CHAT PoW category.
            sendGeohashMined(template, currentChannel.geohash, channelRelays.toSet())
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            return
        }

        // Kinds 42/1311 are the PUBLIC_CHAT PoW category: route through the
        // mining gate (a no-op when the category is off). Draft deletion runs
        // inside the publish continuation so a cancelled mining job can't
        // destroy the only copy of the text.
        accountViewModel.account.sendMined(template, PoWReplay.ToRelays(channelRelays.toList())) { readyTemplate ->
            accountViewModel.account.signAndSendPrivatelyOrBroadcast(readyTemplate) {
                channelRelays.toList()
            }
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    private suspend fun sendGeohashMined(
        template: EventTemplate<out Event>,
        geohash: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty()) return
        val account = accountViewModel.account
        val signer: NostrSigner
        val pubKeyHex: String
        if (geohashPostAsSelf) {
            signer = account.signer
            pubKeyHex = account.signer.pubKey
        } else {
            val keyPair = withContext(Dispatchers.IO) { account.geohashIdentity.keyPair(geohash) }
            signer = NostrSignerInternal(keyPair)
            pubKeyHex = keyPair.pubKey.toHexKey()
        }

        val mined =
            withContext(Dispatchers.Default) {
                // Bitchat mines 8 bits by default; cap the effort so a slow device still sends.
                val deadline = System.nanoTime() + 2_000_000_000L
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                runCatching {
                    PoWMiner.mine(template, pubKeyHex, 8, threads) { System.nanoTime() < deadline }
                }.getOrDefault(template)
            }

        runCatching { account.signWithAndSendPrivately(mined, signer, relays) }
    }

    suspend fun sendDraftSync() {
        if (message.text.toString().isBlank()) {
            account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val attachments = mutableSetOf<Event>()
            nip95attachments.forEach {
                attachments.add(it.first)
                attachments.add(it.second)
            }

            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, attachments)
        }
    }

    fun pickedMedia(list: ImmutableList<SelectedMedia>) {
        uploadState?.load(list)
    }

    fun upload(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: suspend () -> Unit,
    ) = try {
        uploadUnsafe(onError, context, onceUploaded)
    } catch (_: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    fun uploadUnsafe(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: suspend () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploadState = uploadState ?: return@launch

            val myMultiOrchestrator = uploadState.multiOrchestrator ?: return@launch

            uploadState.mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            val results =
                myMultiOrchestrator.upload(
                    uploadState.caption,
                    uploadState.contentWarningReason,
                    MediaCompressor.intToCompressorQuality(uploadState.mediaQualitySlider),
                    uploadState.selectedServer,
                    account,
                    context,
                    stripMetadata = uploadState.stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )

            if (results.allGood) {
                val urls =
                    results.successful.mapNotNull { upload ->
                        if (upload.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                            val nip95 = account.createNip95(upload.result.bytes, headerInfo = upload.result.fileHeader, uploadState.caption, uploadState.contentWarningReason)
                            nip95attachments = nip95attachments + nip95
                            val note = nip95.let { it1 -> account.consumeNip95(it1.first, it1.second) }

                            note?.toNostrUri()
                        } else if (upload.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            iMetaAttachments.add(upload.result, uploadState.caption, uploadState.contentWarningReason)

                            upload.result.url
                        } else {
                            null
                        }
                    }

                message.insertUrlAtCursor(urls.joinToString(" "))
                urlPreview = findUrlInMessage()

                uploadState.reset()
                onceUploaded()
                draftTag.newVersion()
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            uploadState.mediaUploadTracker.finishUpload()
        }
    }

    /**
     * Buzz auto-invite: mentioning someone who isn't yet a member of a Buzz workspace channel adds
     * them (kind-9000 put-user) before the message goes out, so the `@`-mention resolves to a real
     * member — mirroring Buzz's own composer, which is how you pull a bot into a channel by naming it.
     *
     * Only a moderator can issue kind-9000, so this is a no-op for a plain member (the relay would
     * reject it anyway); self and already-present members are skipped. Best-effort — a failed add
     * must never block the message, so each is guarded.
     */
    private suspend fun autoInviteMentionedBuzzMembers(
        channel: Channel,
        mentioned: List<User>?,
    ) {
        if (mentioned.isNullOrEmpty()) return
        if (channel !is RelayGroupChannel || !BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) return
        val me = accountViewModel.account.userProfile().pubkeyHex
        if (!channel.membershipOf(me).canModerate()) return

        mentioned.forEach { user ->
            val pk = user.pubkeyHex
            if (pk != me && channel.membershipOf(pk) == RelayGroupMembership.NONE) {
                try {
                    accountViewModel.account.putRelayGroupUser(channel, pk, emptyList())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("BuzzAutoInvite", "Failed to add mentioned member ${pk.take(8)}: ${e.message}")
                }
            }
        }
    }

    // `protected open` so a specialized composer (e.g. the Buzz forum reply) can reuse this whole
    // rich EditFieldRow but swap only the event it builds for the composed text.
    protected open suspend fun createTemplate(): EventTemplate<out Event>? {
        val channel = channel ?: return null

        val messageText = message.text.toString()
        val tagger =
            NewMessageTagger(
                message = messageText,
                pTags = listOfNotNull(replyTo.value?.author),
                eTags = listOfNotNull(replyTo.value),
                dao = accountViewModel,
            )
        tagger.run()

        autoInviteMentionedBuzzMembers(channel, tagger.pTags)

        val urls = findURLs(messageText)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())
        val emojis = accountViewModel.account.emoji.findEmojiTags(messageText)

        val channelRelays = channel.relays()
        val geoHash = if (wantsToAddGeoHash) (pickedGeoHash ?: (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString()) else null

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        // A minichat reply is a kind-1111 thread comment rooted at the parent, independent of the
        // channel type (NIP-29 groups additionally carry the `h` tag). It carries the same mention/
        // hashtag/quote/emoji/attachment enrichment an inline message does — built from tagger.message,
        // not the raw text — so replying in a thread never silently drops any of them.
        // Buzz relays reject unknown kinds outright, and kind 1111 is not in Buzz's
        // registry — a minichat comment sent to a workspace channel would be refused
        // by the relay AFTER the composer already cleared. Buzz replies always go
        // through the 40002 branch with its thread markers instead.
        val minichatAllowed = !(channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl))
        val minichatParent = replyTo.value?.takeIf { minichatAllowed && replyMode.value == ReplyMode.MINICHAT }?.event
        if (minichatParent != null) {
            return CommentEvent.replyBuilder(tagger.message, EventHintBundle(minichatParent, channelRelays.firstOrNull())) {
                if (channel is RelayGroupChannel) {
                    hTag(channel.groupId.id)
                    previous(channel.previousEventRefs(account.userProfile().pubkeyHex))
                }
                hashtags(findHashtags(tagger.message))
                references(findURLs(tagger.message))
                quotes(findNostrUris(tagger.message))
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }
                geoHash?.let { geohash(it) }
                emojis(emojis)
                imetas(usedAttachments)
            }
        }

        return when {
            channel is PublicChatChannel -> {
                val replyingToEvent = replyTo.value?.toEventHint<ChannelMessageEvent>()
                val channelEvent = channel.event

                if (replyingToEvent != null) {
                    ChannelMessageEvent.reply(tagger.message, replyingToEvent) {
                        notify(replyingToEvent.toPTag())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

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
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        geoHash?.let { geohash(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    ChannelMessageEvent.message(tagger.message, ETag(channel.idHex, channelRelays.firstOrNull())) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        geoHash?.let { geohash(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            channel is LiveActivitiesChannel -> {
                val replyingToEvent = replyTo.value?.toEventHint<LiveActivitiesChatMessageEvent>()
                val activity = channel.info

                if (replyingToEvent != null) {
                    LiveActivitiesChatMessageEvent.reply(tagger.message, replyingToEvent) {
                        notify(replyingToEvent.toPTag())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else if (activity != null) {
                    val hint = EventHintBundle(activity, channelRelays.firstOrNull())

                    LiveActivitiesChatMessageEvent.message(tagger.message, hint) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    LiveActivitiesChatMessageEvent.message(tagger.message, channel.toATag()) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            channel is EphemeralChatChannel -> {
                EphemeralChatEvent.build(
                    tagger.message,
                    channel.roomId.relayUrl,
                    channel.roomId.id,
                ) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }

            channel is GeohashChatChannel -> {
                // Bitchat kind-20000: same mention/hashtag/quote/emoji enrichment as any message,
                // plus the g/n/t tags. Signed + PoW-mined with the per-cell identity in sendPostSync.
                GeohashChatEvent.build(
                    tagger.message,
                    channel.geohash,
                    nickname = geohashNickname.ifBlank { null },
                    teleported = geohashTeleported,
                ) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)

                    replyTo.value?.idHex?.let { add(arrayOf("e", it, "", "reply")) }
                }
            }

            channel is RelayGroupChannel &&
                BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) &&
                editingBuzzMessage.value != null -> {
                // Buzz edit (kind 40003): replaces the text of an existing kind-40002 message.
                // build() sets the group's `h` tag plus the `e` tag pointing at the edited
                // message; content is the replacement text. Kept minimal to mirror Buzz's own
                // `build_edit` (buzz-sdk builders.rs) — the relay validates edits and the author
                // match. LocalCache overlays the newest edit last-write-wins, so the edited row
                // re-renders with this content.
                val target = editingBuzzMessage.value!!
                StreamMessageEditEvent.build(channel.groupId.id, target.idHex, tagger.message)
            }

            channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) -> {
                // Buzz workspace message: the native kind is 40002 (stream message v2)
                // scoped with the group's `h` tag; Buzz threads replies with NIP-10
                // marked e-tags (["e", root, "", "root"] + ["e", parent, "", "reply"],
                // collapsing to a single "reply" when the parent IS the root — mirrors
                // thread_tags in buzz-sdk builders.rs) plus a `p` notify to the parent
                // author. The dialect check comes from BuzzRelayDialect (marked off
                // verified Buzz events), not a channel subtype: channel instances are
                // captured by screens for their whole life, so the dialect must be
                // able to flip mid-session without swapping objects.
                // Reply routing (Buzz has no kind-1111): an INLINE reply is flagged `broadcast` so it stays
                // a flat timeline sibling; a MINICHAT reply omits it so the thread markers pull it into the
                // message's minichat (mirrors block/buzz's broadcast-vs-thread split). A non-reply is neither.
                val broadcastReply = replyTo.value != null && replyMode.value == ReplyMode.INLINE
                StreamMessageV2Event.build(channel.groupId.id, tagger.message, broadcast = broadcastReply) {
                    replyTo.value?.let { parent ->
                        // The parent's root marker (when it is itself a nested reply),
                        // else the parent's OWN reply target (a direct reply's collapsed
                        // form carries the root as its "reply" marker — Buzz's relay
                        // validates ancestry and rejects a mis-derived root), else the
                        // parent starts the thread.
                        val parentTags = parent.event?.tags
                        val root =
                            parentTags?.buzzThreadRoot()
                                ?: parentTags?.buzzThreadReply()
                                ?: parent.idHex
                        buzzThread(root, parent.idHex)
                        parent.author?.pubkeyHex?.let { pTag(PTag(it)) }
                    }

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }

            channel is RelayGroupChannel -> {
                // NIP-29 group message: a kind-9 chat scoped to the group with an
                // `h` tag. The event is published only to the group's host relay
                // (channel.relays()), where relay29 authorizes and routes it.
                val replyingToEvent = replyTo.value?.toEventHint<ChatEvent>()
                if (replyingToEvent != null) {
                    // A reply quotes its parent via a NIP-18 `q` tag (added by ChatEvent.reply)
                    // + a `p` notify to its author. The group scope (`h`) alone doesn't link a
                    // reply to what it answers — without the `q` tag the quote renders in the
                    // composer but is missing from the signed event.
                    ChatEvent.reply(tagger.message, replyingToEvent) {
                        hTag(channel.groupId.id)
                        previous(channel.previousEventRefs(account.userProfile().pubkeyHex))
                        pTag(replyingToEvent.toPTag())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    ChatEvent.build(tagger.message) {
                        hTag(channel.groupId.id)
                        previous(channel.previousEventRefs(account.userProfile().pubkeyHex))

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    open fun cancel() {
        draftTag.rotate()

        message.setTextAndPlaceCursorAtEnd("")

        replyTo.value = null
        editingBuzzMessage.value = null

        urlPreview = null

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null

        forwardZapTo = SplitBuilder()
        forwardZapToEditting.clearText()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        uploadState?.reset()

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    open fun findUrlInMessage(): String? = UrlParser().parseValidUrls(message.text.toString()).withScheme.firstOrNull()

    open fun addToMessage(it: String) {
        message.setTextAndPlaceCursorAtEnd(message.text.toString() + " " + it)
        onMessageChanged()
    }

    open fun onMessageChanged() {
        urlPreview = findUrlInMessage()

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

    open fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                it.replaceCurrentWord(message, lastWord, item)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.addItem(item)
                forwardZapToEditting.clearText()
            }

            userSuggestionsMainMessage = null
            it.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link) {
                Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForImage(item.link)
            }
        }

        message.replaceCurrentWord(wordToInsert)

        emojiSuggestions?.reset()

        urlPreview = findUrlInMessage()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            uploadState?.mediaUploadTracker?.isUploading != true &&
            !wantsInvoice &&
            (!wantsZapraiser || zapRaiserAmount != null) &&
            uploadState?.multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
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
        Log.d("Init") { "OnCleared: ${this.javaClass.simpleName}" }
    }

    fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.updatePercentage(index, sliderValue)
    }

    fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger = NewMessageTagger(message.text.toString(), emptyList(), emptyList(), accountViewModel)
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

    fun toggleExpirationDate() {
        wantsExpirationDate = !wantsExpirationDate
        if (wantsExpirationDate) {
            expirationDate = TimeUtils.oneDayAhead()
        }
        draftTag.newVersion()
    }
}
