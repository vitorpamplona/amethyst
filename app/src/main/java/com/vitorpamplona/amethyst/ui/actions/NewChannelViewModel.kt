package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

class NewChannelViewModel: ViewModel() {
    private var account: Account? = null
    private var originalChannel: Channel? = null
    private var accountStateViewModel: AccountStateViewModel? = null

    val channelName = mutableStateOf(TextFieldValue())
    val channelPicture = mutableStateOf(TextFieldValue())
    val channelDescription = mutableStateOf(TextFieldValue())


    fun load(account: Account, channel: Channel?, accountStateViewModel: AccountStateViewModel) {
        this.accountStateViewModel = accountStateViewModel
        this.account = account
        if (channel != null) {
            originalChannel = channel
            channelName.value = TextFieldValue()
            channelPicture.value = TextFieldValue()
            channelDescription.value = TextFieldValue()
        }
    }

    fun create() {
        if (originalChannel == null)
            this.account?.sendCreateNewChannel(
                channelName.value.text,
                channelDescription.value.text,
                channelPicture.value.text,
                accountStateViewModel!!
            )
        else
            this.account?.sendChangeChannel(
                channelName.value.text,
                channelDescription.value.text,
                channelPicture.value.text,
                originalChannel!!
            )

        clear()
    }

    fun clear() {
        channelName.value = TextFieldValue()
        channelPicture.value = TextFieldValue()
        channelDescription.value = TextFieldValue()
    }
}