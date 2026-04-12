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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.CancellationException

/**
 * Callback interface for UpdateZapAmountViewModel to interact with
 * platform-specific account operations.
 */
interface ZapAmountUpdater {
    fun launchSigner(block: suspend () -> Unit)

    suspend fun updateZapAmounts(
        amounts: List<Long>,
        zapType: LnZapEvent.ZapType,
        nip47: Nip47WalletConnect.Nip47URINorm?,
    )
}

/**
 * Initial zap settings loaded from the account.
 */
data class ZapAmountSettings(
    val zapAmountChoices: List<Long>,
    val defaultZapType: LnZapEvent.ZapType,
    val walletConnectPubkey: String,
    val walletConnectRelay: String,
    val walletConnectSecret: String,
)

@Stable
class UpdateZapAmountViewModel : ViewModel() {
    private lateinit var updater: ZapAmountUpdater
    private lateinit var initialSettings: ZapAmountSettings

    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Long>())
    var walletConnectRelay by mutableStateOf(TextFieldValue(""))
    var walletConnectPubkey by mutableStateOf(TextFieldValue(""))
    var walletConnectSecret by mutableStateOf(TextFieldValue(""))
    var selectedZapType by mutableStateOf(LnZapEvent.ZapType.PRIVATE)

    fun copyFromClipboard(text: String) {
        if (text.isBlank()) {
            return
        }
        updateNIP47(text)
    }

    fun init(updater: ZapAmountUpdater) {
        this.updater = updater
    }

    fun load(settings: ZapAmountSettings) {
        this.initialSettings = settings
        this.amountSet = settings.zapAmountChoices
        this.selectedZapType = settings.defaultZapType

        this.walletConnectPubkey = TextFieldValue(settings.walletConnectPubkey)
        this.walletConnectRelay = TextFieldValue(settings.walletConnectRelay)
        this.walletConnectSecret = TextFieldValue(settings.walletConnectSecret)
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> = commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toLongOrNull()
        if (newValue != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Long) {
        amountSet = amountSet - amount
    }

    fun sendPost() {
        updater.launchSigner {
            sendPostSuspend()
        }
    }

    suspend fun sendPostSuspend() {
        val nip47Update =
            if (walletConnectRelay.text.isNotBlank() && walletConnectPubkey.text.isNotBlank()) {
                val pubkeyHex =
                    try {
                        decodePublicKey(walletConnectPubkey.text.trim()).toHexKey()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                val relayUrl = walletConnectRelay.text.ifBlank { null }?.let { RelayUrlNormalizer.normalizeOrNull(it) }
                val privKeyHex = walletConnectSecret.text.ifBlank { null }?.let { decodePrivateKeyAsHexOrNull(it) }

                if (pubkeyHex != null && relayUrl != null) {
                    Nip47WalletConnect.Nip47URINorm(
                        pubkeyHex,
                        relayUrl,
                        privKeyHex,
                    )
                } else {
                    null
                }
            } else {
                null
            }

        updater.updateZapAmounts(amountSet, selectedZapType, nip47Update)

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean =
        selectedZapType != initialSettings.defaultZapType ||
            amountSet != initialSettings.zapAmountChoices ||
            walletConnectPubkey.text != initialSettings.walletConnectPubkey ||
            walletConnectRelay.text != initialSettings.walletConnectRelay ||
            walletConnectSecret.text != initialSettings.walletConnectSecret

    fun updateNIP47(uri: String) {
        val contact = Nip47WalletConnect.parse(uri)
        walletConnectPubkey = TextFieldValue(contact.pubKeyHex)
        walletConnectRelay = TextFieldValue(contact.relayUri.url)
        walletConnectSecret = TextFieldValue(contact.secret ?: "")
    }
}
