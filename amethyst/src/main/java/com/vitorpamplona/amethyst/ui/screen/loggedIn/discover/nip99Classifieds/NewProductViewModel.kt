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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds

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
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.ProductImageMeta
import com.vitorpamplona.quartz.nip99Classifieds.image
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
open class NewProductViewModel :
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

    var productImages by mutableStateOf<List<ProductImageMeta>>(emptyList())
    val iMetaDescription = IMetaAttachments()

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Classifieds
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

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapraiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    var relayList by mutableStateOf<ImmutableList<NormalizedRelayUrl>?>(null)

    fun lnAddress(): String? = account?.userProfile()?.info?.lnAddress()

    fun hasLnAddress(): Boolean = account?.userProfile()?.info?.lnAddress() != null

    fun user(): User? = account?.userProfile()

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

    open fun quote(quote: Note) {
        val accountViewModel = accountViewModel ?: return

        message = TextFieldValue(message.text + "\nnostr:${quote.toNEvent()}")

        quote.author?.let { quotedUser ->
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

        if (!forwardZapTo.value.items.isEmpty()) {
            wantsForwardZapTo = true
        }

        urlPreviews.update(message)
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent !is ClassifiedsEvent) return

        loadFromDraft(draftEvent)
    }

    private fun loadFromDraft(draftEvent: ClassifiedsEvent) {
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

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapraiser = zapraiser != null
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
        }

        title = TextFieldValue(draftEvent.title() ?: "")
        price = TextFieldValue(draftEvent.price()?.amount ?: "")
        category = TextFieldValue(draftEvent.categories().firstOrNull() ?: "")
        locationText = TextFieldValue(draftEvent.location() ?: "")
        condition = draftEvent.conditionValid() ?: ConditionTag.CONDITION.USED_LIKE_NEW

        val imageSet = draftEvent.images().toMutableSet()

        draftEvent.imetas().forEach {
            if (it.url in imageSet) {
                productImages = productImages + ProductImageMeta.parse(it)
                imageSet.remove(it.url)
            } else {
                iMetaDescription.add(it)
            }
        }

        imageSet.forEach {
            productImages = productImages + ProductImageMeta(it)
        }

        message = TextFieldValue(draftEvent.content)

        urlPreviews.update(message)
    }

    suspend fun sendPostSync() {
        val accountViewModel = accountViewModel ?: return
        val template = createTemplate() ?: return

        val version = draftTag.current
        cancel()

        accountViewModel.account.signAndSendPrivatelyOrBroadcast(template, relayList = { relayList })
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        val accountViewModel = accountViewModel ?: return

        if (message.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        val accountViewModel = accountViewModel ?: return null

        val tagger =
            NewMessageTagger(
                message = message.text,
                dao = accountViewModel,
            )
        tagger.run()

        val emojis = findEmoji(tagger.message, account?.emoji?.myEmojis?.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaDescription.filterIsIn(urls.toSet()) + productImages.map { it.toIMeta() }

        val geoHash = if (wantsToAddGeoHash) (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString() else null

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null
        val localZapRaiserAmount = if (wantsZapraiser) zapRaiserAmount.value else null
        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null

        val quotes = findNostrUris(tagger.message)

        val template =
            ClassifiedsEvent.build(
                title.text,
                PriceTag(price.text, "SATS", null),
                tagger.message,
                locationText.text.ifBlank { null },
                condition,
            ) {
                productImages.forEach { image(it.url) }

                hashtags(listOfNotNull(category.text.ifBlank { null }) + findHashtags(tagger.message))
                quotes(quotes)

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }

                emojis(emojis)
                imetas(usedAttachments)
                references(urls)
            }

        return template
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myAccount = account ?: return@launch
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach {
                    if (it.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        if (it.result.fileHeader.mimeType
                                ?.startsWith("image") == true
                        ) {
                            productImages = productImages +
                                ProductImageMeta(
                                    it.result.url,
                                    it.result.fileHeader.mimeType,
                                    it.result.fileHeader.blurHash
                                        ?.blurhash,
                                    it.result.fileHeader.dim,
                                    alt,
                                    it.result.fileHeader.hash,
                                    it.result.fileHeader.size,
                                )
                        } else {
                            iMetaDescription.add(it.result, alt, contentWarningReason)

                            message = message.insertUrlAtCursor(it.result.url)
                            urlPreviews.update(message)
                        }
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

        multiOrchestrator = null
        isUploadingImage = false

        wantsInvoice = false
        wantsZapraiser = false
        zapRaiserAmount.value = null

        condition = ConditionTag.CONDITION.USED_LIKE_NEW
        locationText = TextFieldValue("")
        title = TextFieldValue("")
        category = TextFieldValue("")
        price = TextFieldValue("")

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.value = TextFieldValue("")

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        productImages = emptyList()
        iMetaDescription.reset()

        emojiSuggestions?.reset()

        reloadRelaySet()
    }

    fun reloadRelaySet() {
        relayList =
            accountViewModel.account.outboxRelays.flow.value
                .toImmutableList()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
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

    open fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = ":${item.code}:"

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaDescription.downloadAndPrepare(item.link) {
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
            title.text.isNotBlank() &&
            price.text.isNotBlank() &&
            category.text.isNotBlank() &&
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

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger = NewMessageTagger(message.text, emptyList(), emptyList(), accountViewModel!!)
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

    fun updateTitle(it: TextFieldValue) {
        title = it
        draftTag.newVersion()
    }

    fun updatePrice(it: TextFieldValue) {
        runCatching {
            if (it.text.isEmpty()) {
                price = TextFieldValue("")
            } else if (it.text.toLongOrNull() != null) {
                price = it
            }
        }
        draftTag.newVersion()
    }

    fun updateCondition(newCondition: ConditionTag.CONDITION) {
        condition = newCondition
        draftTag.newVersion()
    }

    fun updateCategory(value: TextFieldValue) {
        category = value
        draftTag.newVersion()
    }

    fun updateLocation(it: TextFieldValue) {
        locationText = it
        draftTag.newVersion()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
