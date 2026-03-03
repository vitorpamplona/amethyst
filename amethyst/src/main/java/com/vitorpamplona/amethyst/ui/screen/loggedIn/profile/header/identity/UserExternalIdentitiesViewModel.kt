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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.identity

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
class UserExternalIdentitiesViewModel(
    val user: User,
) : ViewModel() {
    private val _identities = MutableStateFlow<List<IdentityClaimTag>>(emptyList())
    val identities = _identities.asStateFlow()

    private val note =
        LocalCache.getOrCreateAddressableNote(
            ExternalIdentitiesEvent.createAddress(user.pubkeyHex),
        )

    init {
        viewModelScope.launch {
            note.flow().metadata.stateFlow.collect { state ->
                val event = state.note.event as? ExternalIdentitiesEvent
                _identities.value = event?.identityClaims() ?: emptyList()
            }
        }
    }

    class Factory(
        val user: User,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserExternalIdentitiesViewModel(user) as T
    }
}
