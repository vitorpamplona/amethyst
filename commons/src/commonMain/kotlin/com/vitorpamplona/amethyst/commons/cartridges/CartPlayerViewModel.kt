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
package com.vitorpamplona.amethyst.commons.cartridges

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.kiko.player.KikoPlayer
import com.vitorpamplona.kiko.runtime.ConsoleHost
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Holds the [player] of one cart for the screen, so a rotation or a closed dialog keeps the game
 * where it was. The cart comes through the image HTTP client (Tor settings apply) and must match
 * the note's [expectedSha256] when it gives one; what the game saves (`cartdata`) goes to
 * [saveStore].
 */
class CartPlayerViewModel(
    private val url: String,
    private val expectedSha256: String?,
    private val httpClients: IRoleBasedHttpClientBuilder,
    saveStore: CartSaveStore?,
) : ViewModel() {
    private val host =
        object : ConsoleHost {
            override fun log(message: String) = Log.d(TAG) { message }

            override fun utcOffsetSeconds(): Int = LocalClock.utcOffsetSeconds(TimeUtils.now()).toInt()

            // Games in several carts load the next one by its id on the original's site, or by a
            // file name. A post has no way to name its other carts yet, so those stop with a message.
            override fun loadCart(name: String): ByteArray? = null
        }

    val player = KikoPlayer(source = ::download, saves = saveStore, host = host)

    private suspend fun download(): ByteArray? {
        val bytes = httpClients.fetchMedia(url, MAX_CART_BYTES) ?: return null
        if (expectedSha256 != null && !sha256(bytes).toHexKey().equals(expectedSha256, ignoreCase = true)) {
            Log.w(TAG) { "The cart at $url does not match its hash" }
            return null
        }
        return bytes
    }

    override fun onCleared() = player.close()

    companion object {
        /** A cart PNG is tens of KiB; anything far past that is not one. */
        private const val MAX_CART_BYTES = 1 shl 20

        private const val TAG = "CartPlayer"
    }
}
