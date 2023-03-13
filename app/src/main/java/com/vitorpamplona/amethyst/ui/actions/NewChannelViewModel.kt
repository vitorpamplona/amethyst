package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel

class NewChannelViewModel : ViewModel() {
    private var account: Account? = null
    private var originalChannel: Channel? = null

    val channelName = mutableStateOf(TextFieldValue())
    val channelPicture = mutableStateOf(TextFieldValue())
    val channelDescription = mutableStateOf(TextFieldValue())

    fun load(account: Account, channel: Channel?) {
        this.account = account
        if (channel != null) {
            originalChannel = channel
            channelName.value = TextFieldValue(channel.info.name ?: "")
            channelPicture.value = TextFieldValue(channel.info.picture ?: "")
            channelDescription.value = TextFieldValue(channel.info.about ?: "")
        }
    }

    fun create() {
        this.account?.let { account ->
            if (originalChannel == null) {
                account.sendCreateNewChannel(
                    channelName.value.text,
                    channelDescription.value.text,
                    channelPicture.value.text
                )
            } else {
                account.sendChangeChannel(
                    channelName.value.text,
                    channelDescription.value.text,
                    channelPicture.value.text,
                    originalChannel!!
                )
            }
        }

        clear()
    }

    fun clear() {
        channelName.value = TextFieldValue()
        channelPicture.value = TextFieldValue()
        channelDescription.value = TextFieldValue()
    }
}
