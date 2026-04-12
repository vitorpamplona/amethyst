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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.nip75Goals

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.posting.NewGoalState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Android ViewModel wrapper around [NewGoalState].
 *
 * Provides Compose [TextFieldValue] properties for UI binding and
 * delegates validation and template creation to the commons state class.
 */
@Stable
class NewGoalViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    val state = NewGoalState()

    var description by mutableStateOf(TextFieldValue(""))
    var amount by mutableStateOf(TextFieldValue(""))
    var summary by mutableStateOf(TextFieldValue(""))
    var imageUrl by mutableStateOf(TextFieldValue(""))
    var websiteUrl by mutableStateOf(TextFieldValue(""))

    var wantsDeadline by mutableStateOf(false)
    var deadlineTimestamp by mutableLongStateOf(TimeUtils.now() + TimeUtils.ONE_WEEK)

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
    }

    fun canPost(): Boolean {
        syncToState()
        return state.canPost()
    }

    fun cancel() {
        state.cancel()
        description = TextFieldValue("")
        amount = TextFieldValue("")
        summary = TextFieldValue("")
        imageUrl = TextFieldValue("")
        websiteUrl = TextFieldValue("")
        wantsDeadline = false
        deadlineTimestamp = TimeUtils.now() + TimeUtils.ONE_WEEK
    }

    suspend fun sendPostSync() {
        syncToState()

        val relays =
            account.outboxRelays.flow.value
                .toList()

        val template = state.createTemplate(relays) ?: return
        cancel()
        account.signAndComputeBroadcast(template)
    }

    /** Push Compose mutable state into the platform-independent state object. */
    private fun syncToState() {
        state.updateDescription(description.text)
        state.updateAmount(amount.text)
        state.updateSummary(summary.text)
        state.updateImageUrl(imageUrl.text)
        state.updateWebsiteUrl(websiteUrl.text)
        state.updateWantsDeadline(wantsDeadline)
        state.updateDeadlineTimestamp(deadlineTimestamp)
    }
}
