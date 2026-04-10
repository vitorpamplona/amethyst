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
package com.vitorpamplona.amethyst.ios.nwc

import com.vitorpamplona.quartz.nip47WalletConnect.Nip47Client
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * Persists the NWC (Nostr Wallet Connect) connection string per account
 * and provides reactive state for the UI.
 */
object NwcSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    private const val KEY_NWC_URI = "nwc_connection_uri"

    private val _connectionUri = MutableStateFlow(loadUri())
    val connectionUri: StateFlow<String?> = _connectionUri.asStateFlow()

    private val _parsedConfig = MutableStateFlow<Nip47WalletConnect.Nip47URINorm?>(null)
    val parsedConfig: StateFlow<Nip47WalletConnect.Nip47URINorm?> = _parsedConfig.asStateFlow()

    init {
        loadUri()?.let { tryParse(it) }
    }

    fun setConnectionUri(uri: String?) {
        if (uri.isNullOrBlank()) {
            defaults.removeObjectForKey(KEY_NWC_URI)
            _connectionUri.value = null
            _parsedConfig.value = null
        } else {
            defaults.setObject(uri, KEY_NWC_URI)
            _connectionUri.value = uri
            tryParse(uri)
        }
    }

    fun isConfigured(): Boolean = _parsedConfig.value != null

    /**
     * Creates an Nip47Client from the stored connection URI.
     * Returns null if not configured or if parsing fails.
     */
    fun createClient(): Nip47Client? {
        val uri = _connectionUri.value ?: return null
        return try {
            Nip47Client.fromUri(uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadUri(): String? = defaults.stringForKey(KEY_NWC_URI)

    private fun tryParse(uri: String) {
        _parsedConfig.value =
            try {
                Nip47WalletConnect.parse(uri)
            } catch (e: Exception) {
                null
            }
    }
}
