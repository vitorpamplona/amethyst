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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.privacy.PrivacyTransport
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException

// Global pick of which privacy transport carries clearnet traffic. Lives apart
// from TorSharedPreferences / I2pSharedPreferences because it spans both — each
// transport owns its own daemon-enable + per-feature toggles; this owns the
// "which one is in charge of clearnet" decision.
@Stable
class PrivacySharedPreferences(
    initial: PrivacyTransport,
    val context: Context,
    val scope: CoroutineScope,
) {
    val preferredClearnetTransport: MutableStateFlow<PrivacyTransport> = MutableStateFlow(initial)

    @OptIn(FlowPreview::class)
    val saving =
        preferredClearnetTransport
            .debounce(1000)
            .distinctUntilChanged()
            .onEach { save(it, context) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, initial)

    companion object {
        val PREFERRED_CLEARNET_TRANSPORT_KEY = stringPreferencesKey("privacy.preferredClearnetTransport")

        suspend fun preferredClearnetTransport(context: Context): PrivacyTransport =
            try {
                val name = context.sharedPreferencesDataStore.data.first()[PREFERRED_CLEARNET_TRANSPORT_KEY]
                name?.let { runCatching { PrivacyTransport.valueOf(it) }.getOrNull() } ?: PrivacyTransport.DIRECT
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SharedPreferences") { "Error reading preferredClearnetTransport: ${e.message}" }
                PrivacyTransport.DIRECT
            }

        suspend fun save(
            transport: PrivacyTransport,
            context: Context,
        ) {
            try {
                context.sharedPreferencesDataStore.edit { prefs ->
                    prefs[PREFERRED_CLEARNET_TRANSPORT_KEY] = transport.name
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SharedPreferences") { "Error saving preferredClearnetTransport: ${e.message}" }
            }
        }
    }
}
