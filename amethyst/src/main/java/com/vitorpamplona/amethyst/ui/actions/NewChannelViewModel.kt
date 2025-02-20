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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewChannelViewModel : ViewModel() {
    private var account: Account? = null
    private var originalChannel: PublicChatChannel? = null

    val channelName = mutableStateOf(TextFieldValue())
    val channelPicture = mutableStateOf(TextFieldValue())
    val channelDescription = mutableStateOf(TextFieldValue())

    fun load(
        account: Account,
        channel: PublicChatChannel?,
    ) {
        this.account = account
        if (channel != null) {
            originalChannel = channel
            channelName.value = TextFieldValue(channel.info.name ?: "")
            channelPicture.value = TextFieldValue(channel.info.picture ?: "")
            channelDescription.value = TextFieldValue(channel.info.about ?: "")
        }
    }

    fun create() {
        viewModelScope.launch(Dispatchers.IO) {
            account?.let { account ->
                val channel = originalChannel
                if (channel == null) {
                    val template =
                        ChannelCreateEvent.build(
                            channelName.value.text,
                            channelDescription.value.text,
                            channelPicture.value.text,
                            null,
                        )

                    account.sendCreateNewChannel(template)
                } else {
                    val event = channel.event

                    val template =
                        if (event != null) {
                            val hint = EventHintBundle<ChannelCreateEvent>(event, channel.relays().firstOrNull())

                            ChannelMetadataEvent.build(
                                channelName.value.text,
                                channelDescription.value.text,
                                channelPicture.value.text,
                                null,
                                hint,
                            )
                        } else {
                            val eTag = ETag(channel.idHex, channel.relays().firstOrNull())

                            ChannelMetadataEvent.build(
                                channelName.value.text,
                                channelDescription.value.text,
                                channelPicture.value.text,
                                null,
                                eTag,
                            )
                        }

                    account.sendChangeChannel(template)
                }
            }

            clear()
        }
    }

    fun clear() {
        channelName.value = TextFieldValue()
        channelPicture.value = TextFieldValue()
        channelDescription.value = TextFieldValue()
    }
}
