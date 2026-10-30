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
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.fantasyconsole.cart.CartFormatException
import com.vitorpamplona.fantasyconsole.cart.CartLoader
import com.vitorpamplona.fantasyconsole.runtime.Audio
import com.vitorpamplona.fantasyconsole.runtime.FantasyConsole
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Downloads and runs one cart. The game runs on its own thread ([newCartDispatcher]) at its
 * frame rate, publishing each [frame] and playing its sound; the screen sends the held buttons
 * with [setButtons]. [resume]/[pause] follow the player being on screen, so a rotation or a
 * closed dialog keeps the game where it was.
 */
class CartPlayerViewModel(
    private val url: String,
    private val expectedSha256: String?,
    private val httpClients: IRoleBasedHttpClientBuilder,
) : ViewModel() {
    sealed interface Status {
        data object Loading : Status

        /** The file could not be downloaded, did not match its hash, or is not a cart. */
        data object NotACart : Status

        data object Playing : Status

        /** The game ended on its own, or stopped on an [error] (already drawn on its screen). */
        data class Stopped(
            val error: String?,
        ) : Status
    }

    /** One finished frame: 128x128 ARGB [pixels], numbered so each is a new value. */
    class Frame(
        val pixels: IntArray,
        val number: Long,
    )

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    @Volatile
    private var console: FantasyConsole? = null

    // Three buffers: the game draws into one while the screen may still be reading the last two.
    private val buffers = Array(3) { IntArray(SCREEN_SIZE * SCREEN_SIZE) }
    private var nextBuffer = 0

    private val dispatcher = lazy { newCartDispatcher() }
    private var loop: Job? = null

    /** The buttons held now, as [Buttons] bits. */
    fun setButtons(mask: Int) {
        console?.setButtons(0, mask)
    }

    fun resume() {
        if (loop?.isActive == true) return
        loop =
            viewModelScope.launch(dispatcher.value) {
                val game = console ?: load() ?: return@launch
                play(game)
            }
    }

    fun pause() {
        loop?.cancel()
        loop = null
    }

    private suspend fun load(): FantasyConsole? {
        val bytes = httpClients.fetchMedia(url, MAX_CART_BYTES)
        if (bytes == null || (expectedSha256 != null && !sha256(bytes).toHexKey().equals(expectedSha256, ignoreCase = true))) {
            _status.value = Status.NotACart
            return null
        }
        val cart =
            try {
                CartLoader.load(bytes)
            } catch (_: CartFormatException) {
                _status.value = Status.NotACart
                return null
            }
        return FantasyConsole(cart).also { console = it }
    }

    private suspend fun play(game: FantasyConsole) {
        if (game.state != FantasyConsole.State.RUNNING) {
            publishFrame(game)
            _status.value = Status.Stopped(game.error)
            return
        }
        _status.value = Status.Playing
        val sound = openCartAudioSink(Audio.SAMPLE_RATE)
        try {
            var deadline = TimeSource.Monotonic.markNow()
            while (currentCoroutineContext().isActive) {
                game.step()
                publishFrame(game)
                if (game.state != FantasyConsole.State.RUNNING) {
                    _status.value = Status.Stopped(game.error)
                    break
                }
                sound?.write(game.audioBuffer, game.audioSamples)
                deadline += 1.seconds / game.fps
                val wait = -deadline.elapsedNow()
                if (wait.isPositive()) {
                    delay(wait)
                } else if (wait < -MAX_LAG) {
                    // Far behind (the app was busy): drop the backlog instead of racing to catch up.
                    deadline = TimeSource.Monotonic.markNow()
                }
            }
        } finally {
            sound?.close()
        }
    }

    private fun publishFrame(game: FantasyConsole) {
        val buffer = buffers[nextBuffer]
        nextBuffer = (nextBuffer + 1) % buffers.size
        game.render(buffer)
        _frame.value = Frame(buffer, game.frameCount)
    }

    override fun onCleared() {
        console?.abort()
        loop?.cancel()
        if (dispatcher.isInitialized()) dispatcher.value.close()
    }

    /** The button bits [setButtons] takes. */
    object Buttons {
        const val LEFT = 1
        const val RIGHT = 2
        const val UP = 4
        const val DOWN = 8
        const val O = 16
        const val X = 32
        const val PAUSE = 64
    }

    companion object {
        const val SCREEN_SIZE = 128

        /** A cart PNG is tens of KiB; anything far past that is not one. */
        private const val MAX_CART_BYTES = 1 shl 20

        private val MAX_LAG = 250.milliseconds
    }
}
