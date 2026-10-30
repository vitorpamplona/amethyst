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
package com.vitorpamplona.fantasyconsole.runtime

import com.vitorpamplona.fantasyconsole.cart.Cart
import com.vitorpamplona.fantasyconsole.lua.Closure
import com.vitorpamplona.fantasyconsole.lua.Fix
import com.vitorpamplona.fantasyconsole.lua.Interpreter
import com.vitorpamplona.fantasyconsole.lua.LuaAbort
import com.vitorpamplona.fantasyconsole.lua.LuaError
import com.vitorpamplona.fantasyconsole.lua.LuaFunction
import com.vitorpamplona.fantasyconsole.lua.LuaSyntaxError
import com.vitorpamplona.fantasyconsole.lua.NONE
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/** What the console needs from the app around it. Every method has a do-nothing default. */
interface ConsoleHost {
    /** `printh`. */
    fun log(message: String) {}

    /** The 64 saved numbers (raw 16:16 patterns) for `cartdata(id)`, or null if there are none. */
    fun loadCartData(id: String): IntArray? = null

    fun saveCartData(
        id: String,
        values: IntArray,
    ) {}
}

/**
 * One running cartridge. The host calls [step] once per frame at [fps] on a single thread,
 * then reads [render] and [audioBuffer]/[audioSamples] on that same thread. [abort] is the only
 * call that may come from elsewhere: it stops a cart stuck in a loop. Give that thread a
 * [RECOMMENDED_STACK_BYTES] stack: each Lua call takes several Kotlin frames, and a default
 * 1 MiB stack runs out between 500 and 1000 nested calls.
 *
 * The cart runs as one Kotlin coroutine: its top-level code, then `_init`, then `_update` (or
 * `_update60`) and `_draw` each frame. Between frames it is suspended, and so is a cart that
 * calls `flip()` in its own loop, so both styles work.
 */
class FantasyConsole(
    val cart: Cart,
    val host: ConsoleHost = object : ConsoleHost {},
) {
    enum class State { RUNNING, FINISHED, STOPPED, ERROR }

    val ram = Ram()
    val graphics = Graphics(ram)
    val audio = Audio(ram)
    val input = Input(ram)

    internal var interp = Interpreter()
        private set

    var state = State.RUNNING
        private set

    /** The error that stopped the cart, if one did. */
    var error: String? = null
        private set

    /** 30, or 60 for a cart with `_update60`. */
    var fps = 30
        internal set

    var frameCount = 0L
        private set

    /** `time()`, in 16:16: advances 1/[fps] each frame. */
    internal var timeBits = 0

    /** The sound of the last [step]: [audioSamples] samples at [Audio.SAMPLE_RATE], in [-1, 1]. */
    val audioBuffer = FloatArray(Audio.SAMPLE_RATE / 30 + 1)
    var audioSamples = 0
        private set
    private var audioRemainder = 0

    private var chunk: Closure? = null
    private var started = false
    private var frameWaiter: Continuation<Unit>? = null
    private var restartRequested = false

    internal var cartDataId: String? = null
    internal var cartDataDirty = false

    init {
        boot()
    }

    private fun boot() {
        ram.bytes.fill(0)
        cart.rom.copyInto(ram.bytes, 0, 0, Addr.CART_DATA_END)
        graphics.reset()
        interp = Interpreter()
        interp.memory = ram
        Api(this, interp).install()
        state = State.RUNNING
        error = null
        started = false
        frameWaiter = null
        restartRequested = false
        timeBits = 0
        fps = 30
        chunk =
            try {
                interp.load(cart.code)
            } catch (e: LuaSyntaxError) {
                fail(e.message ?: "syntax error")
                null
            }
    }

    /** Runs one frame. Does nothing once the cart has stopped. */
    fun step() {
        if (state != State.RUNNING) return
        input.beginFrame()
        val waiter = frameWaiter
        when {
            !started -> {
                started = true
                val body = chunk ?: return
                val main: suspend () -> Unit = { mainLoop(body) }
                interp.scheduler.post { main.startCoroutine(Continuation(EmptyCoroutineContext) { ended(it) }) }
            }
            waiter != null -> {
                frameWaiter = null
                interp.scheduler.post { waiter.resume(Unit) }
            }
            else -> return
        }
        interp.scheduler.run()
        frameCount++
        timeBits += Fix.ONE / fps
        mixAudio()
        flushCartData()
        if (restartRequested) boot()
    }

    /** Stops the cart from any thread, even in the middle of a frame. */
    fun abort() {
        interp.abortRequested = true
    }

    fun setButtons(
        player: Int,
        mask: Int,
    ) = input.setButtons(player, mask)

    /** The screen as 128x128 ARGB pixels. */
    fun render(out: IntArray) = graphics.render(out)

    private suspend fun mainLoop(body: Closure) {
        body.call(interp, NONE)
        global("_init")?.let { interp.call(it, NONE) }
        while (true) {
            val update60 = global("_update60")
            val update = update60 ?: global("_update")
            val draw = global("_draw")
            if (update == null && draw == null) return
            fps = if (update60 != null) 60 else 30
            update?.let { interp.call(it, NONE) }
            draw?.let { interp.call(it, NONE) }
            waitFrame()
        }
    }

    private fun global(name: String): LuaFunction? = interp.globals.rawget(name) as? LuaFunction

    /** Suspends the cart until the next [step]: the end of a frame, or `flip()`. */
    internal suspend fun waitFrame() = suspendCoroutine { frameWaiter = it }

    private fun ended(result: Result<Unit>) {
        val e = result.exceptionOrNull()
        when (e) {
            null -> state = State.FINISHED
            is RestartRequest -> restartRequested = true
            is StopRequest -> state = State.STOPPED
            is LuaAbort -> state = State.STOPPED
            is LuaError -> fail("runtime error ${e.luaMessage}")
            else ->
                if (e::class.simpleName == "StackOverflowError") {
                    fail("runtime error: stack overflow")
                } else {
                    fail("internal error: $e")
                }
        }
    }

    private fun fail(message: String) {
        state = State.ERROR
        error = message
        // Like the console: the message over whatever was on screen.
        graphics.reset()
        val lines = message.chunked(31).take(6)
        graphics.rect(0, 127 - lines.size * 6 - 2, 127, 127, 0, filled = true)
        lines.forEachIndexed { i, line -> graphics.print(line, 1, 127 - (lines.size - i) * 6, 8) }
    }

    private fun mixAudio() {
        val total = Audio.SAMPLE_RATE + audioRemainder
        audioSamples = total / fps
        audioRemainder = total % fps
        audio.render(audioBuffer, 0, audioSamples)
    }

    private fun flushCartData() {
        val id = cartDataId ?: return
        if (!cartDataDirty) return
        cartDataDirty = false
        host.saveCartData(id, IntArray(64) { ram.peek4(Addr.CART_DATA + it * 4) })
    }

    internal fun requestRestart(): Nothing = throw RestartRequest()

    internal fun requestStop(): Nothing = throw StopRequest()

    private class RestartRequest : Exception("run")

    companion object {
        /** Enough for [Interpreter.MAX_DEPTH] nested Lua calls with room to spare. */
        const val RECOMMENDED_STACK_BYTES = 16L shl 20
    }

    private class StopRequest : Exception("stop")
}
