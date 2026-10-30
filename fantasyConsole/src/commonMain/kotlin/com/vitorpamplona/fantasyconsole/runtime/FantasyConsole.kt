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
import com.vitorpamplona.fantasyconsole.cart.CartFormatException
import com.vitorpamplona.fantasyconsole.cart.CartLoader
import com.vitorpamplona.fantasyconsole.lua.Closure
import com.vitorpamplona.fantasyconsole.lua.Fix
import com.vitorpamplona.fantasyconsole.lua.Interpreter
import com.vitorpamplona.fantasyconsole.lua.LuaAbort
import com.vitorpamplona.fantasyconsole.lua.LuaError
import com.vitorpamplona.fantasyconsole.lua.LuaFunction
import com.vitorpamplona.fantasyconsole.lua.LuaSyntaxError
import com.vitorpamplona.fantasyconsole.lua.NONE
import com.vitorpamplona.fantasyconsole.lua.Values
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

    /** The local time zone's offset from UTC, for `stat(90..95)`. */
    fun utcOffsetSeconds(): Int = 0

    /**
     * The bytes of another cartridge, for a game that spans several: `load(name)` switches to it
     * and `reload(…, name)` reads its data. [name] is what the cart wrote, `#id` for a cart on
     * the original's site and a file name otherwise. Called on the console's thread, so it may
     * block; null when the host cannot find it.
     */
    fun loadCart(name: String): ByteArray? = null

    /**
     * Whether the process is short of memory. Asked every thousand or so loop iterations and
     * calls; true ends the cart with "out of memory" before it takes the app's heap with it.
     */
    fun memoryLow(): Boolean = false
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
    cart: Cart,
    val host: ConsoleHost = object : ConsoleHost {},
) {
    /** The cartridge running now: `load()` replaces it. */
    var cart: Cart = cart
        private set

    enum class State { RUNNING, FINISHED, STOPPED, ERROR }

    val ram = Ram()
    val graphics = Graphics(ram)
    val audio = Audio(ram)
    val input = Input(ram)
    internal val printer = Printer(ram, graphics, audio)
    internal val menu = PauseMenu()

    /** `run(param)`/`stat(6)`: the parameter string the cart was (re)started with. */
    internal var runParam = ""

    /** Carts left by `load(name, label)`, newest last: `extcmd("breadcrumb")` goes back to one. */
    private val breadcrumbs = ArrayList<Breadcrumb>()

    private class Breadcrumb(
        val cart: Cart,
        val label: String,
        val param: String,
    )

    /** `stat(100)`: the label of the cart `load()` came from. */
    internal val breadcrumbLabel: String? get() = breadcrumbs.lastOrNull()?.label

    /** Other carts read by `reload(…, name)`, null for one the host could not supply. */
    private val otherCarts = HashMap<String, Cart?>()

    private var frameStart = TimeSource.Monotonic.markNow()
    private var pauseRequested = false
    private var inCallback = false

    /** True while `_draw` runs: the original never cuts a frame in the middle of drawing it. */
    private var inDraw = false

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
        interp.frameHook = {
            if (host.memoryLow()) throw LuaError("out of memory")
            if (!inDraw && !inCallback && frameStart.elapsedNow() > 1.seconds / fps) waitFrame()
        }
        Api(this, interp).install()
        state = State.RUNNING
        error = null
        started = false
        frameWaiter = null
        restartRequested = false
        cartDataId = null
        cartDataDirty = false
        timeBits = 0
        fps = 30
        menu.close()
        menu.items.fill(null)
        pauseRequested = false
        chunk =
            try {
                interp.load(BUILTIN_LOCALS + cart.code + "\n" + MAIN_LOOP)
            } catch (e: LuaSyntaxError) {
                fail(e.message ?: "syntax error")
                null
            }
    }

    /** Runs one frame. Does nothing once the cart has stopped. */
    fun step() {
        if (state != State.RUNNING) return
        frameStart = TimeSource.Monotonic.markNow()
        input.beginFrame()
        if (stepMenu()) {
            audioSamples = 0
            return
        }
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

    /**
     * While the pause menu is open it takes the frame (true). The pause button opens it, unless
     * the cart set 0x5f30 to swallow that press.
     */
    private fun stepMenu(): Boolean {
        if (!menu.isOpen) {
            val pausePressed = input.btnp(6, 0, fps) || input.btnp(6, 1, fps)
            if (!pausePressed && !pauseRequested) return false
            pauseRequested = false
            if (ram[Addr.PAUSE_SUPPRESS] != 0 && pausePressed) {
                ram[Addr.PAUSE_SUPPRESS] = 0
                return false
            }
            menu.open()
            return true
        }
        when (val action = menu.step(input, fps)) {
            PauseMenu.Action.None -> {}
            PauseMenu.Action.Resume -> menu.close()
            PauseMenu.Action.Reset -> {
                menu.close()
                boot()
            }
            is PauseMenu.Action.Call -> {
                val keepOpen = callNow(action.callback, listOf(Fix.fromInt(action.buttons)))?.firstOrNull()
                if (keepOpen != true) menu.close()
            }
        }
        return true
    }

    /**
     * Calls [fn] outside the cart's frame (a menu callback) and runs it to its end. A `flip` in
     * it does not wait; an error stops the cart as any other would.
     */
    private fun callNow(
        fn: LuaFunction,
        args: Values,
    ): Values? {
        var result: Values? = null
        inCallback = true
        val body: suspend () -> Values = { fn.call(interp, args) }
        interp.scheduler.post {
            body.startCoroutine(
                Continuation(EmptyCoroutineContext) { r ->
                    result = r.getOrNull()
                    r.exceptionOrNull()?.let { ended(Result.failure(it)) }
                },
            )
        }
        interp.scheduler.run()
        inCallback = false
        if (restartRequested) boot()
        return result
    }

    /** `extcmd("pause")`: open the pause menu at the next frame. */
    internal fun requestPause() {
        pauseRequested = true
    }

    /**
     * `stat(1)`: how much of this frame's time the cart has used so far (1.0 = all of it). Capped,
     * since a pause in the host (a slow first frame, a collection) is not the cart's doing, and
     * carts print `stat(1)*100`, which would overflow past 327.
     */
    internal fun cpuLoad(): Double = (frameStart.elapsedNow().inWholeMicroseconds / (1_000_000.0 / fps)).coerceAtMost(MAX_CPU_LOAD)

    /** Devkit mode (`poke(0x5f2d, 1)`): the pointer in screen pixels and its buttons (1 left, 2 right, 4 middle). */
    fun setPointer(
        x: Int,
        y: Int,
        buttons: Int,
    ) = input.setPointer(x, y, buttons)

    /** Devkit mode: mouse wheel steps (`stat(36)`). */
    fun scroll(steps: Int) = input.scroll(steps)

    /** Devkit mode: typed text, read by the cart with `stat(30)`/`stat(31)`. */
    fun type(text: String) = input.type(text)

    /** Stops the cart from any thread, even in the middle of a frame. */
    fun abort() {
        interp.abortRequested = true
    }

    fun setButtons(
        player: Int,
        mask: Int,
    ) = input.setButtons(player, mask)

    /** Whether the pause menu is open: the game is frozen behind it. */
    val isPaused: Boolean get() = menu.isOpen

    /** The screen as 128x128 ARGB pixels, with the pause menu over it while it is open. */
    fun render(out: IntArray) {
        graphics.render(out)
        if (menu.isOpen) menu.draw(out)
    }

    /** The cart's code, followed by [MAIN_LOOP], which drives `_init`, `_update` and `_draw`. */
    private suspend fun mainLoop(body: Closure) {
        body.call(interp, NONE)
    }

    /** `_draw`, marked so a frame is never cut in the middle of drawing it. */
    internal suspend fun runDraw(draw: Any?) {
        inDraw = true
        try {
            interp.call(draw, NONE)
        } finally {
            inDraw = false
        }
    }

    private fun global(name: String): LuaFunction? = interp.globals.rawget(name) as? LuaFunction

    /** Suspends the cart until the next [step]: the end of a frame, or `flip()`. */
    internal suspend fun waitFrame() {
        if (inCallback) return
        suspendCoroutine { frameWaiter = it }
    }

    /** `\^1`-`\^9` and `\^d` in print: let [n] frames go by. */
    internal suspend fun waitFrames(n: Int) = repeat(n) { waitFrame() }

    private fun ended(result: Result<Unit>) {
        val e = result.exceptionOrNull()
        when (e) {
            null -> state = State.FINISHED
            is RestartRequest -> restartRequested = true
            is StopRequest -> state = State.STOPPED
            is LoadRequest -> switchTo(e)
            is LuaAbort -> state = State.STOPPED
            is LuaError -> fail("runtime error ${e.luaMessage}", e.trace)
            else ->
                if (e::class.simpleName == "StackOverflowError") {
                    fail("runtime error: stack overflow")
                } else {
                    fail("internal error: $e")
                }
        }
    }

    private fun fail(
        message: String,
        trace: List<String> = emptyList(),
    ) {
        state = State.ERROR
        error = if (trace.isEmpty()) message else message + trace.joinToString("") { "\n  in $it" }
        // Like the console: the message over whatever was on screen.
        graphics.reset()
        val lines = message.chunked(31).take(6)
        graphics.rect(0, 127 - lines.size * 6 - 2, 127, 127, 0, filled = true)
        lines.forEachIndexed { i, line -> printer.printNow(line, 1, 127 - (lines.size - i) * 6, 8) }
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

    internal fun requestRestart(param: String?): Nothing {
        if (param != null) runParam = param
        throw RestartRequest()
    }

    /** `stop([message])`: the message is printed where the cursor is, then the cart stops. */
    internal fun requestStop(message: String?): Nothing {
        if (message != null) printer.printNow(message, ram[Addr.CURSOR_X], ram[Addr.CURSOR_Y], ram[Addr.PEN])
        throw StopRequest()
    }

    private class RestartRequest : Exception("run")

    private class LoadRequest(
        val name: String,
        val breadcrumb: String?,
        val param: String?,
    ) : Exception("load $name")

    /** `load(name, breadcrumb, param)`: ends this cart; [switchTo] starts the other one. */
    internal fun requestLoad(
        name: String,
        breadcrumb: String?,
        param: String?,
    ): Nothing = throw LoadRequest(name, breadcrumb, param)

    /** `extcmd("breadcrumb")`: back to the cart that loaded this one, if there is one. */
    internal fun goBack() {
        val back = breadcrumbs.removeLastOrNull() ?: return
        cart = back.cart
        runParam = back.param
        throw RestartRequest()
    }

    private fun switchTo(request: LoadRequest) {
        val next = otherCart(request.name)
        if (next == null) {
            fail("this game continues in another cartridge (${request.name}), which can't be loaded here")
            return
        }
        if (request.breadcrumb != null && request.breadcrumb.length > 1) {
            breadcrumbs.add(Breadcrumb(cart, request.breadcrumb, runParam))
        }
        cart = next
        runParam = request.param ?: ""
        restartRequested = true
    }

    /** Another cartridge by the name the cart used, read once and remembered. */
    internal fun otherCart(name: String): Cart? =
        otherCarts.getOrPut(name) {
            try {
                host.loadCart(name)?.let(CartLoader::load)
            } catch (e: CartFormatException) {
                null
            }
        }

    companion object {
        private const val MAX_CPU_LOAD = 2.0

        /**
         * The built-ins the console copies into top-level locals before a cart runs, on the cart's
         * first line so line numbers stay put. They resolve lexically, so a function whose `_ENV`
         * is a plain table still reaches `circfill`. (`rnd` is not among them on the original
         * either.)
         */
        private val BUILTIN_LOCALS: String =
            listOf(
                "time",
                "sub",
                "chr",
                "ord",
                "tostr",
                "tonum",
                "add",
                "del",
                "deli",
                "clip",
                "color",
                "pal",
                "palt",
                "fillp",
                "pget",
                "pset",
                "sget",
                "sset",
                "fget",
                "fset",
                "circ",
                "circfill",
                "rect",
                "rectfill",
                "oval",
                "ovalfill",
                "line",
                "spr",
                "sspr",
                "mget",
                "mset",
                "tline",
                "peek",
                "poke",
                "peek2",
                "poke2",
                "peek4",
                "poke4",
                "memcpy",
                "memset",
                "max",
                "min",
                "mid",
                "flr",
                "ceil",
                "cos",
                "sin",
                "atan2",
                "srand",
                "band",
                "bor",
                "bxor",
                "bnot",
                "shl",
                "shr",
                "lshr",
                "rotl",
                "rotr",
            ).let { "local " + it.joinToString(",") + "=" + it.joinToString(",") + " " }

        /**
         * Appended to the cart's code, as the console does, so it sees the cart's locals: a cart
         * may declare `local function _draw()`. Each frame runs `_update60` (60 fps) or `_update`,
         * then `_draw`, then ends the frame.
         */
        private const val MAIN_LOOP =
            "if (_init) _init()\n" +
                "if _update or _update60 or _draw then while true do\n" +
                "if _update60 then __fc_fps(60) _update60() elseif _update then __fc_fps(30) _update() end\n" +
                "if (_draw) __fc_draw(_draw)\n" +
                "__fc_flip() end end"

        /**
         * Room for tens of thousands of nested Lua calls: some carts run a small interpreter of
         * their own that recurses over 10,000 deep. A chain that still runs out of stack ends the
         * cart with a stack overflow error, like [Interpreter.MAX_DEPTH] does.
         */
        const val RECOMMENDED_STACK_BYTES = 64L shl 20
    }

    private class StopRequest : Exception("stop")
}
