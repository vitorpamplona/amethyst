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
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.kiko.cart.CartLoader
import com.vitorpamplona.kiko.cart.P8scii
import com.vitorpamplona.kiko.runtime.ConsoleHost
import com.vitorpamplona.kiko.runtime.FantasyConsole
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Downloads and runs one cart. The game runs on its own thread ([newCartDispatcher]) at its
 * frame rate, publishing each [frame] and playing its sound; the screen sends the held buttons
 * with [setButtons], and the pointer and typed text for games that read them. Input reaches the
 * game on its thread, between frames. [resume]/[pause] follow the player being on screen, so a
 * rotation or a closed dialog keeps the game where it was. What the game saves (`cartdata`)
 * goes to [saveStore] through [CartSaves]; the game in progress lives only in memory.
 */
class CartPlayerViewModel(
    private val url: String,
    private val expectedSha256: String?,
    private val httpClients: IRoleBasedHttpClientBuilder,
    saveStore: CartSaveStore?,
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

    // When the frame running now started, for the watchdog: a cart can loop for ever in `_draw`
    // or in a menu callback, where the console never ends the frame on its own.
    @Volatile
    private var stepStarted: TimeMark? = null

    @Volatile
    private var hung = false

    @Volatile
    private var menuOpen = false

    private val saves = CartSaves(saveStore)

    private val host =
        object : ConsoleHost {
            override fun log(message: String) = Log.d(TAG) { message }

            override fun loadCartData(id: String): IntArray? = saves.get(id)

            override fun saveCartData(
                id: String,
                values: IntArray,
            ) = saves.save(id, values)

            override fun utcOffsetSeconds(): Int = LocalClock.utcOffsetSeconds(TimeUtils.now()).toInt()

            // Games in several carts load the next one by its id on the original's site, or by a
            // file name. A post has no way to name its other carts yet, so those stop with a message.
            override fun loadCart(name: String): ByteArray? = null

            override fun memoryLow(): Boolean = isCartMemoryLow()
        }

    private var buttons = 0

    /** The buttons held now, as [Buttons] bits. */
    fun setButtons(mask: Int) {
        buttons = mask
        onGameThread { it.setButtons(0, mask) }
    }

    /** A tap of the pause button, which opens the game's pause menu (or closes it). */
    fun tapPause() {
        val held = buttons
        onGameThread {
            it.setButtons(0, held or Buttons.PAUSE)
            it.setButtons(0, held)
        }
    }

    /**
     * Back while playing opens the game's pause menu, so a stray swipe never ends a game.
     * Returns false when there is nothing to pause (or it is paused already): the player closes.
     */
    fun openPauseMenu(): Boolean {
        if (_status.value != Status.Playing || menuOpen) return false
        // Until the next frame says so: a quick second Back must close, not toggle the menu shut.
        menuOpen = true
        tapPause()
        return true
    }

    /** The pointer over the screen, in game pixels (0..127), and its buttons (1 left, 2 right, 4 middle). */
    fun setPointer(
        x: Int,
        y: Int,
        pointerButtons: Int,
    ) = onGameThread { it.setPointer(x, y, pointerButtons) }

    fun scroll(steps: Int) = onGameThread { it.scroll(steps) }

    /** Typed characters, for games that read the keyboard. */
    fun type(text: String) = onGameThread { it.type(P8scii.fromUnicode(text)) }

    private fun onGameThread(action: (FantasyConsole) -> Unit) {
        val game = console ?: return
        viewModelScope.launch(dispatcher.value) { action(game) }
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
        // Cancelling cannot interrupt a frame that never ends; stop such a game instead of
        // leaving its thread spinning behind a closed player.
        if (isStuck(PAUSE_STUCK)) stopHungGame()
        loop?.cancel()
        loop = null
        saves.flush()
    }

    private fun isStuck(after: Duration) = (stepStarted?.elapsedNow() ?: Duration.ZERO) > after

    private fun stopHungGame() {
        hung = true
        console?.abort()
    }

    private suspend fun load(): FantasyConsole? {
        val bytes = httpClients.fetchMedia(url, MAX_CART_BYTES)
        if (bytes == null || (expectedSha256 != null && !sha256(bytes).toHexKey().equals(expectedSha256, ignoreCase = true))) {
            _status.value = Status.NotACart
            return null
        }
        saves.load()
        return try {
            FantasyConsole(CartLoader.load(bytes), host).also { console = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A hostile file must never take the app down: anything that fails to load, even
            // with an Error, is simply not a cart.
            Log.w(TAG, "Could not load the cart at $url", e)
            _status.value = Status.NotACart
            null
        }
    }

    private suspend fun play(game: FantasyConsole) {
        if (game.state != FantasyConsole.State.RUNNING) {
            publishFrame(game)
            _status.value = Status.Stopped(game.error)
            return
        }
        _status.value = Status.Playing
        val sound = openCartAudioSink(FantasyConsole.SAMPLE_RATE)
        val watchdog =
            viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(WATCHDOG_INTERVAL)
                    if (isStuck(MAX_FRAME)) stopHungGame()
                }
            }
        try {
            var deadline = TimeSource.Monotonic.markNow()
            while (currentCoroutineContext().isActive) {
                stepStarted = TimeSource.Monotonic.markNow()
                try {
                    game.step()
                } finally {
                    stepStarted = null
                }
                menuOpen = game.isPaused
                publishFrame(game)
                if (game.state != FantasyConsole.State.RUNNING) {
                    _status.value = Status.Stopped(if (hung) NOT_RESPONDING else game.error)
                    break
                }
                sound?.write(game.audioBuffer, game.audioSamples)
                deadline += 1.seconds / game.fps
                val wait = -deadline.elapsedNow()
                if (wait.isPositive()) {
                    delay(wait)
                } else {
                    // Behind: still let the input queued for this thread in before the next frame.
                    yield()
                    // Far behind (the app was busy): drop the backlog instead of racing to catch up.
                    if (wait < -MAX_LAG) deadline = TimeSource.Monotonic.markNow()
                }
            }
        } finally {
            watchdog.cancel()
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

        /** A frame this long is a game stuck in a loop the console cannot end. */
        private val MAX_FRAME = 10.seconds
        private val PAUSE_STUCK = 1.seconds
        private val WATCHDOG_INTERVAL = 1.seconds
        private const val NOT_RESPONDING = "the game stopped responding"
        private const val TAG = "CartPlayer"
    }
}
