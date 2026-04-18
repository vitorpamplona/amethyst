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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.post

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent

@Stable
class NewBadgeViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var badgeId by mutableStateOf(TextFieldValue(""))
    var name by mutableStateOf(TextFieldValue(""))
    var description by mutableStateOf(TextFieldValue(""))
    var imageUrl by mutableStateOf(TextFieldValue(""))
    var thumbUrl by mutableStateOf(TextFieldValue(""))

    var isEdit by mutableStateOf(false)

    fun init(
        accountVM: AccountViewModel,
        editDTag: String?,
    ) {
        this.accountViewModel = accountVM
        this.account = accountVM.account

        if (editDTag.isNullOrBlank()) return

        val existing =
            LocalCache
                .getAddressableNoteIfExists(
                    Address(BadgeDefinitionEvent.KIND, account.signer.pubKey, editDTag),
                )?.event as? BadgeDefinitionEvent ?: return

        isEdit = true
        badgeId = TextFieldValue(existing.dTag())
        name = TextFieldValue(existing.name() ?: "")
        description = TextFieldValue(existing.description() ?: "")
        imageUrl = TextFieldValue(existing.image() ?: "")
        thumbUrl = TextFieldValue(existing.thumb() ?: "")
    }

    fun canPost(): Boolean = badgeId.text.isNotBlank() && name.text.isNotBlank()

    fun cancel() {
        badgeId = TextFieldValue("")
        name = TextFieldValue("")
        description = TextFieldValue("")
        imageUrl = TextFieldValue("")
        thumbUrl = TextFieldValue("")
        isEdit = false
    }

    suspend fun sendPost() {
        if (!canPost()) return

        val thumb = thumbUrl.text.ifBlank { null }
        val thumbs =
            if (thumb != null) {
                listOf(
                    com.vitorpamplona.quartz.nip58Badges.definition.tags
                        .ThumbTag(thumb),
                )
            } else {
                emptyList()
            }

        account.sendBadgeDefinition(
            badgeId = badgeId.text.trim(),
            name = name.text.trim(),
            imageUrl = imageUrl.text.ifBlank { null },
            imageDim = null,
            description = description.text.ifBlank { null },
            thumbs = thumbs,
        )

        cancel()
    }
}
