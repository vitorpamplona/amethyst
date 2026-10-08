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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.wot.onboarding.KnownTrustProviders
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/** Where a guided sign-up is. */
@Immutable
sealed interface WebOfTrustSetup {
    data object Idle : WebOfTrustSetup

    /** Signing up with [providerId], at [step]. */
    data class Running(
        val providerId: String,
        val step: TrustProviderOnboardingStep,
    ) : WebOfTrustSetup

    /** Signed up; publishing the new provider list. */
    data class Saving(
        val providerId: String,
    ) : WebOfTrustSetup

    /** [reason] is null for an error the providers do not classify; [message] says what happened. */
    data class Failed(
        val providerId: String,
        val reason: TrustProviderException.Reason?,
        val message: String,
    ) : WebOfTrustSetup
}

/**
 * The Web of Trust settings' actions: guided sign-ups ([providers]) and choosing a provider by
 * hand. The network itself (download, minimum score, removal) is read and changed through
 * [Account] directly.
 */
class WebOfTrustViewModel(
    private val account: Account,
    http: TrustProviderHttp,
) : ViewModel() {
    val providers: List<TrustProviderOnboarding> = KnownTrustProviders.all(http)

    private val _setup = MutableStateFlow<WebOfTrustSetup>(WebOfTrustSetup.Idle)
    val setup: StateFlow<WebOfTrustSetup> = _setup.asStateFlow()

    /** Whether the provider is written to the encrypted part of the kind 10040. */
    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    fun setPrivate(value: Boolean) {
        _isPrivate.value = value
    }

    /** Signs up with [onboarding] and makes it the account's provider. One sign-up at a time. */
    fun setUp(onboarding: TrustProviderOnboarding) {
        if (_setup.value is WebOfTrustSetup.Running || _setup.value is WebOfTrustSetup.Saving) return
        _setup.value = WebOfTrustSetup.Running(onboarding.id, TrustProviderOnboardingStep.SIGNING_IN)
        viewModelScope.launch {
            _setup.value =
                try {
                    val registration = onboarding.register(account.signer) { step -> _setup.value = WebOfTrustSetup.Running(onboarding.id, step) }
                    _setup.value = WebOfTrustSetup.Saving(onboarding.id)
                    account.setTrustScoreProvider(registration.serviceKey, registration.relay, _isPrivate.value)
                    WebOfTrustSetup.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TrustProviderException) {
                    WebOfTrustSetup.Failed(onboarding.id, e.reason, e.message.orEmpty())
                } catch (e: SignerExceptions) {
                    WebOfTrustSetup.Failed(onboarding.id, TrustProviderException.Reason.SIGNER_DECLINED, e.message.orEmpty())
                } catch (e: Exception) {
                    WebOfTrustSetup.Failed(onboarding.id, null, e.message ?: e::class.simpleName.orEmpty())
                }
        }
    }

    /** Uses the provider signing with [key] on [relay], entered by hand. */
    fun useProvider(
        key: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        viewModelScope.launch {
            try {
                account.setTrustScoreProvider(key, relay, _isPrivate.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _setup.value = WebOfTrustSetup.Failed(MANUAL, (e as? SignerExceptions)?.let { TrustProviderException.Reason.SIGNER_DECLINED }, e.message.orEmpty())
            }
        }
    }

    class Factory(
        private val account: Account,
        private val http: TrustProviderHttp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = WebOfTrustViewModel(account, http) as T
    }

    companion object {
        /** [WebOfTrustSetup.Failed.providerId] for a provider entered by hand. */
        const val MANUAL = "manual"
    }
}
