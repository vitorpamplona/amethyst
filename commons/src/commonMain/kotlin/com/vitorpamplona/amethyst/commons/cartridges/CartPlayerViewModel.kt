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
import com.vitorpamplona.kiko.player.KikoPlayerHost
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.Volatile

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
    private val saveStore: CartSaveStore?,
) : ViewModel() {
    private val host =
        object : KikoPlayerHost {
            override fun log(message: String) = Log.d(TAG) { message }

            override fun utcOffsetSeconds(): Int = LocalClock.utcOffsetSeconds(TimeUtils.now()).toInt()

            // Games in several carts load the next one by its id on the original's site, or by a
            // file name. A post has no way to name its other carts yet, so those stop with a message.
            override fun loadCart(name: String): ByteArray? = null
        }

    // The sha256 of the cart the player loaded: its saves are kept under it (see [CartSaveStore]).
    @Volatile
    private var cartHash: String? = null

    private var current: KikoPlayer? = null

    /**
     * The player of this cart, made again if it was let go while the dialog was closed (see
     * [LivePlayers]). Main thread only.
     */
    fun player(): KikoPlayer {
        val player = current ?: KikoPlayer(source = ::download, saves = saveStore?.forCart { cartHash }, host = host).also { current = it }
        live.touch(this)
        return player
    }

    /** Ends the game and lets its memory go; [player] starts it again. Main thread only. */
    fun release() {
        current?.close()
        current = null
    }

    private suspend fun download(): ByteArray? {
        val bytes = httpClients.fetchMedia(url, MAX_CART_BYTES) ?: return null
        val hash = sha256(bytes).toHexKey()
        if (expectedSha256 != null && !hash.equals(expectedSha256, ignoreCase = true)) {
            Log.w(TAG) { "The cart at $url does not match its hash" }
            return null
        }
        cartHash = hash
        return bytes
    }

    override fun onCleared() {
        live.remove(this)
        release()
    }

    companion object {
        /** A cart PNG is tens of KiB; anything far past that is not one. */
        private const val MAX_CART_BYTES = 1 shl 20

        /**
         * Games kept paused at once. A closed dialog keeps its game where it was, but every game holds
         * its whole Lua heap, frames and a thread: a feed of carts opened one after another must not
         * keep them all. Past this, the least recently shown one is let go.
         */
        const val MAX_LIVE_PLAYERS = 2

        private val live = LivePlayers<CartPlayerViewModel>(MAX_LIVE_PLAYERS) { it.release() }

        private const val TAG = "CartPlayer"
    }
}

/**
 * The [max] most recently used of something that holds a lot, by use: [touch] marks one used and lets
 * the least recently used go past [max] (calling [release] on it). Main thread only.
 */
class LivePlayers<T>(
    private val max: Int,
    private val release: (T) -> Unit,
) {
    private val order = ArrayDeque<T>()

    fun touch(item: T) {
        order.remove(item)
        order.addLast(item)
        while (order.size > max) release(order.removeFirst())
    }

    fun remove(item: T) {
        order.remove(item)
    }

    val size: Int get() = order.size
}
