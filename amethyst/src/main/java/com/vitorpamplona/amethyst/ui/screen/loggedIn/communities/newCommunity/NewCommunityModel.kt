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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.SuspendableConfirmation
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class CommunityRelayEntry(
    val url: NormalizedRelayUrl,
    val marker: String? = null,
)

@Stable
class NewCommunityModel : ViewModel() {
    var account: Account? = null

    // When set, publish() reuses this d-tag so the kind-34550 replaceable event
    // is updated in place (edit flow). Null in the create flow.
    var existingDTag: String? = null

    var isPublishing by mutableStateOf(false)

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var rules by mutableStateOf("")
    var existingImageUrl by mutableStateOf<String?>(null)

    // Image upload state - mirrors NewBadgeModel.
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    var selectedServer by mutableStateOf<ServerName?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(1)
    var stripMetadata by mutableStateOf(true)

    val strippingFailureConfirmation = SuspendableConfirmation()

    // Moderator/Relay lists - backed by mutableStateListOf for direct Compose observation.
    val moderators = mutableStateListOf<User>()
    val relays = mutableStateListOf<CommunityRelayEntry>()

    fun init(account: Account) {
        if (this.account == account) return
        this.account = account
        this.selectedServer = defaultServer()
        this.stripMetadata = account.settings.stripLocationOnUpload
    }

    /**
     * Preloads the form with the current contents of [existing] so the user can edit
     * the replaceable kind 34550 event. Keeps the original `d` tag so relays replace
     * the previous version instead of creating a new community.
     */
    fun loadFrom(existing: CommunityDefinitionEvent) {
        existingDTag = existing.dTag()
        name = existing.name().orEmpty()
        description = existing.description().orEmpty()
        rules = existing.rules().orEmpty()
        existingImageUrl = existing.image()?.imageUrl

        val ownerKey = account?.signer?.pubKey
        moderators.clear()
        existing
            .moderatorKeys()
            .asSequence()
            .filter { it != ownerKey }
            .distinct()
            .forEach { pubkey ->
                val user = account?.cache?.getOrCreateUser(pubkey) ?: return@forEach
                if (moderators.none { it.pubkeyHex == user.pubkeyHex }) {
                    moderators.add(user)
                }
            }

        relays.clear()
        existing.relays().forEach { tag ->
            if (relays.none { it.url == tag.url }) {
                relays.add(CommunityRelayEntry(tag.url, tag.marker))
            }
        }
    }

    fun isEditing(): Boolean = existingDTag != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun setPickedMedia(uris: ImmutableList<SelectedMedia>) {
        this.multiOrchestrator = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun hasPickedImage(): Boolean = multiOrchestrator != null

    fun addModerator(user: User) {
        if (moderators.none { it.pubkeyHex == user.pubkeyHex }) {
            moderators.add(user)
        }
    }

    fun removeModerator(user: User) {
        moderators.removeAll { it.pubkeyHex == user.pubkeyHex }
    }

    fun addRelay(url: NormalizedRelayUrl) {
        if (relays.none { it.url == url }) {
            relays.add(CommunityRelayEntry(url))
        }
    }

    fun removeRelay(entry: CommunityRelayEntry) {
        relays.removeAll { it.url == entry.url }
    }

    fun setRelayMarker(
        entry: CommunityRelayEntry,
        marker: String?,
    ) {
        val index = relays.indexOfFirst { it.url == entry.url }
        if (index >= 0) {
            relays[index] = entry.copy(marker = marker)
        }
    }

    fun canPost(): Boolean =
        !isPublishing &&
            name.isNotBlank() &&
            description.isNotBlank()

    @OptIn(ExperimentalUuidApi::class)
    fun publish(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) = try {
        publishUnsafe(context, onSuccess, onError)
    } catch (e: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun publishUnsafe(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val myAccount = account ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isPublishing = true
            try {
                val uploadedUrl =
                    if (multiOrchestrator != null) {
                        uploadImageIfAny(context, myAccount, onError) ?: return@launch
                    } else {
                        null
                    }

                val imageUrl = uploadedUrl ?: existingImageUrl

                val ownerKey = myAccount.signer.pubKey
                val moderatorTags =
                    buildList {
                        add(ModeratorTag(ownerKey, null, "moderator"))
                        moderators
                            .asSequence()
                            .filter { it.pubkeyHex != ownerKey }
                            .forEach { add(ModeratorTag(it.pubkeyHex, null, "moderator")) }
                    }

                val relayTags = relays.map { RelayTag(it.url, it.marker) }

                val dTag = existingDTag ?: Uuid.random().toString()

                val definition =
                    myAccount.sendCommunityDefinition(
                        name = name.trim(),
                        description = description.trim(),
                        moderators = moderatorTags,
                        image = imageUrl,
                        rules = rules.trim().ifBlank { null },
                        relays = relayTags.ifEmpty { null },
                        dTag = dTag,
                    )

                if (definition == null) {
                    onError(
                        stringRes(context, R.string.read_only_user),
                        stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    return@launch
                }

                // Auto-follow only on the create flow; editing doesn't change the follow set.
                if (existingDTag == null) {
                    val communityNote = myAccount.cache.getOrCreateAddressableNote(definition.address())
                    myAccount.follow(communityNote)
                }

                selectedServer?.let { myAccount.settings.changeDefaultFileServer(it) }
                myAccount.settings.changeStripLocationOnUpload(stripMetadata)

                reset()
                onSuccess()
            } finally {
                isPublishing = false
            }
        }
    }

    private suspend fun uploadImageIfAny(
        context: Context,
        myAccount: Account,
        onError: (String, String) -> Unit,
    ): String? {
        val orch = multiOrchestrator ?: return null
        val serverToUse = selectedServer ?: defaultServer()

        val results =
            orch.upload(
                alt = name.trim().ifBlank { "Community cover" },
                contentWarningReason = null,
                mediaQuality = MediaCompressor.intToCompressorQuality(mediaQualitySlider),
                server = serverToUse,
                account = myAccount,
                context = context,
                useH265 = false,
                stripMetadata = stripMetadata,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            )

        if (!results.allGood) {
            val messages =
                results.errors
                    .map { stringRes(context, it.errorResource, *it.params) }
                    .distinct()
                    .joinToString(".\n")
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), messages)
            return null
        }

        val uploaded =
            results.successful.firstNotNullOfOrNull {
                it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
            } ?: run {
                onError(
                    stringRes(context, R.string.failed_to_upload_media_no_details),
                    "Upload succeeded but no image URL was returned by the server.",
                )
                return null
            }

        return uploaded.url
    }

    fun reset() {
        name = ""
        description = ""
        rules = ""
        existingImageUrl = null
        existingDTag = null
        multiOrchestrator = null
        moderators.clear()
        relays.clear()
        selectedServer = defaultServer()
    }
}
