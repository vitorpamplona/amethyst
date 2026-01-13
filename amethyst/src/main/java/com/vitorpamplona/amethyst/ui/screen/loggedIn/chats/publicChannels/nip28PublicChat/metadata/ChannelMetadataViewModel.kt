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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata

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
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Stable
class ChannelMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private var originalChannel: PublicChatChannel? = null

    val channelName = mutableStateOf(TextFieldValue())
    val channelPicture = mutableStateOf(TextFieldValue())
    val channelDescription = mutableStateOf(TextFieldValue())

    private val _channelRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val channelRelays = _channelRelays.asStateFlow()

    var isUploadingImageForPicture by mutableStateOf(false)

    val canPost by derivedStateOf {
        channelName.value.text.isNotBlank()
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        originalChannel = null
        clear()
    }

    fun load(channel: PublicChatChannel) {
        originalChannel = channel
        channelName.value = TextFieldValue(channel.info.name ?: "")
        channelPicture.value = TextFieldValue(channel.info.picture ?: "")
        channelDescription.value = TextFieldValue(channel.info.about ?: "")

        val relays =
            channel.info.relays
                ?.map { relaySetupInfoBuilder(it) }
                ?.distinctBy { it.relay }

        _channelRelays.update { relays ?: emptyList() }
    }

    fun isNewChannel() = originalChannel == null && _channelRelays.value.isNotEmpty()

    fun createOrUpdate(onDone: (PublicChatChannel) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            account?.let { account ->
                val channel = originalChannel
                if (channel == null) {
                    val template =
                        ChannelCreateEvent.build(
                            channelName.value.text,
                            channelDescription.value.text,
                            channelPicture.value.text,
                            channelRelays.value.map { it.relay },
                        )

                    val signedResult =
                        account.signAndSendPrivatelyOrBroadcast(
                            template,
                            relayList = { it.channelInfo().relays },
                        )
                    val channel = LocalCache.getOrCreatePublicChatChannel(signedResult.id)
                    // follows the channel
                    account.follow(channel)
                    onDone(channel)
                } else {
                    val event = channel.event

                    val template =
                        if (event != null) {
                            val hint = EventHintBundle<ChannelCreateEvent>(event, channel.relays().firstOrNull())

                            ChannelMetadataEvent.build(
                                channelName.value.text,
                                channelDescription.value.text,
                                channelPicture.value.text,
                                channelRelays.value.map { it.relay },
                                hint,
                            )
                        } else {
                            val eTag = ETag(channel.idHex, channel.relays().firstOrNull())

                            ChannelMetadataEvent.build(
                                channelName.value.text,
                                channelDescription.value.text,
                                channelPicture.value.text,
                                channelRelays.value.map { it.relay },
                                eTag,
                            )
                        }

                    val signedResult =
                        account.signAndSendPrivatelyOrBroadcast(
                            template,
                            relayList = { it.channelInfo().relays },
                        )
                    val channel = LocalCache.getOrCreatePublicChatChannel(signedResult.id)
                    onDone(channel)
                }
            }

            clear()
        }
    }

    fun addHomeRelay(relay: BasicRelaySetupInfo) {
        if (_channelRelays.value.any { it.relay == relay.relay }) return

        _channelRelays.update { it.plus(relay) }
    }

    fun deleteHomeRelay(relay: BasicRelaySetupInfo) {
        _channelRelays.update { it.minus(relay) }
    }

    fun clear() {
        channelName.value = TextFieldValue()
        channelPicture.value = TextFieldValue()
        channelDescription.value = TextFieldValue()
        _channelRelays.update { emptyList() }
    }

    fun uploadForPicture(
        uri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingImageForPicture = it },
                onUploaded = { channelPicture.value = TextFieldValue(it) },
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        context: Context,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        val account = account ?: return
        onUploading(true)

        val compResult = MediaCompressor().compress(galleryUri.uri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

        try {
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

            if (result.url != null) {
                onUploading(false)
                onUploaded(result.url)
            } else {
                onUploading(false)
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            }
        } catch (_: SignerExceptions.ReadOnlyException) {
            onUploading(false)
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.login_with_a_private_key_to_be_able_to_upload))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onUploading(false)
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
        }
    }
}
