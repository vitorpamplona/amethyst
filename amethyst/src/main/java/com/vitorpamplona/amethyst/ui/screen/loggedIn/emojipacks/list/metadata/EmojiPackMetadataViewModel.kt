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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Stable
class EmojiPackMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    var pack by mutableStateOf<OwnedEmojiPack?>(null)
    val isNewPack by derivedStateOf { pack == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    val canPost by derivedStateOf {
        name.value.text.isNotBlank()
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        pack = null
        clear()
    }

    fun load(dTag: String) {
        val existing = account.ownedEmojiPacks.getPack(dTag)
        pack = existing
        name.value = TextFieldValue(existing?.title ?: "")
        picture.value = TextFieldValue(existing?.image ?: "")
        description.value = TextFieldValue(existing?.description ?: "")
    }

    fun createOrUpdate() {
        accountViewModel.launchSigner {
            val currentPack = pack
            if (currentPack == null) {
                account.createOwnedEmojiPack(
                    title = name.value.text,
                    description = description.value.text,
                    image = picture.value.text,
                )
            } else {
                account.updateOwnedEmojiPackMetadata(
                    dTag = currentPack.identifier,
                    newTitle = name.value.text,
                    newDescription = description.value.text,
                    newImage = picture.value.text,
                )
            }
            clear()
        }
    }

    fun clear() {
        name.value = TextFieldValue()
        picture.value = TextFieldValue()
        description.value = TextFieldValue()
    }
}
