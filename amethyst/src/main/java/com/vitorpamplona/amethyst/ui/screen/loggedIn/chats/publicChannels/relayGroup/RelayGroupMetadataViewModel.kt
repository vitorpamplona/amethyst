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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.AvifMetadataNotVerifiableException
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MetadataStripper
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the create/edit NIP-29 group metadata screens. Holds the full editable metadata
 * (name, about, picture, and the four status flags), lets the user pick a picture from the
 * gallery, uploads it to their configured media server on submit, then publishes the kind
 * 9007+9002 (create) or 9002 (edit) events. Mirrors [EmojiPackMetadataViewModel].
 */
@Stable
class RelayGroupMetadataViewModel : ViewModel() {
    private lateinit var account: Account

    /** Non-null in edit mode; null while creating a new group. */
    private var channel: RelayGroupChannel? = null
    val isNewGroup by derivedStateOf { channel == null }

    /** Host relay (create + edit) and the group id (generated in create mode). */
    var relay: NormalizedRelayUrl? = null
        private set
    var groupId: String = ""
        private set

    val name = mutableStateOf(TextFieldValue())
    val about = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())

    var isPrivate by mutableStateOf(false)
    var isClosed by mutableStateOf(false)
    var isHidden by mutableStateOf(false)
    var isRestricted by mutableStateOf(false)

    var pickedMedia by mutableStateOf<SelectedMedia?>(null)
        private set

    var isWorking by mutableStateOf(false)

    /** Set once the user edits any field, so late-arriving metadata won't clobber their input. */
    var touched by mutableStateOf(false)
        private set

    val canPost by derivedStateOf { !isWorking && name.value.text.isNotBlank() }

    fun hasImage(): Boolean = pickedMedia != null || picture.value.text.isNotBlank()

    fun initCreate(
        accountViewModel: AccountViewModel,
        relay: NormalizedRelayUrl,
    ) {
        this.account = accountViewModel.account
        if (this.relay == null) {
            this.relay = relay
            // Random NIP-29 group id: 8 secure bytes, hex-encoded (matches Armada).
            this.groupId = RandomInstance.bytes(8).toHexKey()
        }
    }

    fun initEdit(
        accountViewModel: AccountViewModel,
        channel: RelayGroupChannel,
    ) {
        this.account = accountViewModel.account
        this.channel = channel
        this.relay = channel.groupId.relayUrl
        this.groupId = channel.groupId.id
    }

    /** Seed the fields from the group's current relay-signed metadata, unless the user has edited. */
    fun prefillFrom(channel: RelayGroupChannel) {
        if (touched) return
        val event = channel.event
        name.value = TextFieldValue(event?.name() ?: "")
        about.value = TextFieldValue(event?.about() ?: "")
        picture.value = TextFieldValue(event?.picture() ?: "")
        isPrivate = channel.isPrivate()
        isClosed = channel.isClosed()
        isHidden = event?.isHidden() ?: false
        isRestricted = event?.isRestricted() ?: false
    }

    fun markTouched() {
        touched = true
    }

    fun pickMedia(media: SelectedMedia) {
        pickedMedia = media
        touched = true
    }

    fun submit(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        if (isWorking) return
        viewModelScope.launch(Dispatchers.IO) {
            isWorking = true
            try {
                val local = pickedMedia
                if (local != null) {
                    val uploadedUrl = uploadImage(local, context, onError) ?: return@launch
                    picture.value = TextFieldValue(uploadedUrl)
                    pickedMedia = null
                }

                try {
                    publish()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    onError(
                        stringRes(context, R.string.read_only_user),
                        stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    return@launch
                }
                onSuccess()
            } finally {
                isWorking = false
            }
        }
    }

    private suspend fun publish() {
        val name = name.value.text.trim()
        val about =
            about.value.text
                .trim()
                .ifBlank { null }
        val picture =
            picture.value.text
                .trim()
                .ifBlank { null }
        val existing = channel
        if (existing == null) {
            account.createRelayGroup(
                relay = relay!!,
                groupId = groupId,
                name = name,
                about = about,
                picture = picture,
                isPrivate = isPrivate,
                isClosed = isClosed,
                isHidden = isHidden,
                isRestricted = isRestricted,
            )
        } else {
            account.editRelayGroupMetadata(
                channel = existing,
                name = name,
                about = about,
                picture = picture,
                isPrivate = isPrivate,
                isClosed = isClosed,
                isHidden = isHidden,
                isRestricted = isRestricted,
            )
        }
    }

    private suspend fun uploadImage(
        galleryUri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ): String? {
        val sourceUri =
            try {
                if (account.settings.stripLocationOnUpload) {
                    val result = MetadataStripper.strip(galleryUri.uri, galleryUri.mimeType, context.applicationContext)
                    if (!result.stripped) {
                        onError(
                            stringRes(context, R.string.metadata_strip_failed_title),
                            stringRes(context, R.string.metadata_strip_failed_upload_cancelled),
                        )
                        return null
                    }
                    result.uri
                } else {
                    galleryUri.uri
                }
            } catch (e: AvifMetadataNotVerifiableException) {
                onError(
                    stringRes(context, R.string.metadata_strip_failed_title),
                    stringRes(context, R.string.avif_metadata_strip_failed, e.message ?: e.javaClass.simpleName),
                )
                return null
            }
        val compResult = MediaCompressor().compress(sourceUri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

        return try {
            val result =
                if (account.settings.defaultFileServer.type == ServerType.NIP96) {
                    Nip96Uploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        onProgress = {},
                        httpAuth = account::createHTTPAuthorization,
                        context = context,
                    )
                } else {
                    BlossomUploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        httpAuth = account::createBlossomUploadAuth,
                        context = context,
                    )
                }

            result.url ?: run {
                onError(
                    stringRes(context, R.string.failed_to_upload_media_no_details),
                    stringRes(context, R.string.server_did_not_provide_a_url_after_uploading),
                )
                null
            }
        } catch (_: SignerExceptions.ReadOnlyException) {
            onError(
                stringRes(context, R.string.failed_to_upload_media_no_details),
                stringRes(context, R.string.login_with_a_private_key_to_be_able_to_upload),
            )
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
            null
        }
    }
}
