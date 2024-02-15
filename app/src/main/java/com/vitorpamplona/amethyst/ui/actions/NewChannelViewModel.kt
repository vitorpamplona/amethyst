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
                if (originalChannel == null) {
                    account.sendCreateNewChannel(
                        channelName.value.text,
                        channelDescription.value.text,
                        channelPicture.value.text,
                    )
                } else {
                    account.sendChangeChannel(
                        channelName.value.text,
                        channelDescription.value.text,
                        channelPicture.value.text,
                        originalChannel!!,
                    )
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
