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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.dal

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ZapAmount(
    val user: User,
    val amount: BigDecimal,
)

@Stable
class UserProfileZapsViewModel(
    val user: User,
    val account: Account,
) : ViewModel() {
    val zapsToUser =
        Filter(
            kinds = listOf(LnZapEvent.KIND),
            tags = mapOf("p" to listOf(user.pubkeyHex)),
        )

    val sortingModel: Comparator<ZapAmount> =
        compareByDescending<ZapAmount> { it.amount }.thenBy { it.user.pubkeyHex }

    suspend fun mapRequest(zapEvent: LnZapEvent): ZapAmount? {
        val zapRequest =
            zapEvent.zapRequest ?: return ZapAmount(
                LocalCache.getOrCreateUser(zapEvent.pubKey),
                zapEvent.amount ?: BigDecimal.ZERO,
            )

        return if (zapRequest.isPrivateZap()) {
            // if user is not the logged in, we cannot decrypt.
            if (user.pubkeyHex == account.pubKey) {
                val cachedPrivateRequest = account.privateZapsDecryptionCache.decryptPrivateZap(zapRequest)
                if (cachedPrivateRequest != null) {
                    ZapAmount(
                        LocalCache.getOrCreateUser(cachedPrivateRequest.pubKey),
                        zapEvent.amount ?: BigDecimal.ZERO,
                    )
                } else {
                    ZapAmount(
                        LocalCache.getOrCreateUser(zapRequest.pubKey),
                        zapEvent.amount ?: BigDecimal.ZERO,
                    )
                }
            } else {
                ZapAmount(
                    LocalCache.getOrCreateUser(zapRequest.pubKey),
                    zapEvent.amount ?: BigDecimal.ZERO,
                )
            }
        } else {
            ZapAmount(
                LocalCache.getOrCreateUser(zapRequest.pubKey),
                zapEvent.amount ?: BigDecimal.ZERO,
            )
        }
    }

    suspend fun List<Event>.sumAmountsByUser(): List<ZapAmount> {
        val results = mutableMapOf<User, BigDecimal>()

        this.forEach { zapEvent ->
            if (zapEvent is LnZapEvent) {
                val zapAmount = mapRequest(zapEvent)
                if (zapAmount != null) {
                    val existingAmount = results[zapAmount.user] ?: BigDecimal.ZERO
                    results[zapAmount.user] = existingAmount + zapAmount.amount
                }
            }
        }

        return results.map { (user, amount) -> ZapAmount(user, amount) }.sortedWith(sortingModel)
    }

    val receivedZapAmountsByUser: StateFlow<List<ZapAmount>> =
        account.cache
            .observeEvents(zapsToUser)
            .sample(500)
            .map { zapEvents ->
                zapEvents.sumAmountsByUser()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.Lazily,
            )

    val totalReceivedZaps =
        receivedZapAmountsByUser
            .map { it.sumOf { it.amount } }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                initialValue = BigDecimal.ZERO,
                started = SharingStarted.Lazily,
            )

    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserProfileZapsViewModel(user, account) as T
    }
}
