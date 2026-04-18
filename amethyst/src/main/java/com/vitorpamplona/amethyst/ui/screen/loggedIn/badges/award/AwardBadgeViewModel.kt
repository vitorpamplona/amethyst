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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.award

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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent

@Stable
class AwardBadgeViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var definition by mutableStateOf<BadgeDefinitionEvent?>(null)
    var awardeesText by mutableStateOf(TextFieldValue(""))

    fun init(
        accountVM: AccountViewModel,
        kind: Int,
        pubKeyHex: HexKey,
        dTag: String,
    ) {
        this.accountViewModel = accountVM
        this.account = accountVM.account

        val ev =
            LocalCache.getAddressableNoteIfExists(Address(kind, pubKeyHex, dTag))?.event as? BadgeDefinitionEvent
        definition = ev
    }

    fun parsedPubKeys(): List<HexKey> =
        awardeesText.text
            .split('\n', ',', ' ', ';')
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) null else decodePublicKeyAsHexOrNull(trimmed)
            }.distinct()

    fun canPost(): Boolean = definition != null && parsedPubKeys().isNotEmpty()

    fun cancel() {
        awardeesText = TextFieldValue("")
    }

    suspend fun sendPost() {
        val def = definition ?: return
        val awardees = parsedPubKeys().map { PTag(it) }
        if (awardees.isEmpty()) return

        account.sendBadgeAward(def, awardees)
        cancel()
    }
}
