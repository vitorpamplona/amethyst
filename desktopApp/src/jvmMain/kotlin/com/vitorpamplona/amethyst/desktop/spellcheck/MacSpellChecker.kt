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
package com.vitorpamplona.amethyst.desktop.spellcheck

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellChecker

/**
 * The macOS spell checker (`NSSpellChecker`), the one Safari, Mail and Signal use: it follows the
 * languages the user set up in System Settings, detects the language of each word, and knows the
 * words the user taught it in other apps.
 *
 * Reached through JNA and the Objective-C runtime (`objc_msgSend`), and run on the AppKit main
 * thread because AppKit classes are not documented as thread-safe. Callers must not be on the AWT
 * event thread: [SpellCheckService] runs every call on its own thread, which may wait on the main
 * thread, and the main thread never waits on it.
 */
class MacSpellChecker private constructor(
    private val checker: Pointer,
) : SpellChecker {
    override val name = "macOS"

    override fun isCorrect(word: String): Boolean = onMainThread { withAutoreleasePool { check(word) } }

    // One hop to the main thread for the whole batch instead of one per word.
    override fun areCorrect(words: List<String>): BooleanArray =
        onMainThread {
            withAutoreleasePool { BooleanArray(words.size) { check(words[it]) } }
        }

    /** Must run on the main thread inside an autorelease pool. */
    private fun check(word: String): Boolean {
        val range = sendRange(checker, "checkSpellingOfString:startingAt:", nsString(word), 0L)
        return range.location == NS_NOT_FOUND || range.length == 0L
    }

    override fun suggest(word: String): List<String> =
        onMainThread {
            withAutoreleasePool {
                val language = send(checker, "language")
                val guesses =
                    send(
                        checker,
                        "guessesForWordRange:inString:language:inSpellDocumentWithTag:",
                        NSRange.ByValue(0, word.length.toLong()),
                        nsString(word),
                        language,
                        0L,
                    ) ?: return@withAutoreleasePool emptyList()
                val count = sendLong(guesses, "count")
                (0 until count).mapNotNull { index -> send(guesses, "objectAtIndex:", index)?.let(::kotlinString) }
            }
        }

    @Structure.FieldOrder("location", "length")
    open class NSRange(
        @JvmField var location: Long = 0,
        @JvmField var length: Long = 0,
    ) : Structure() {
        class ByValue(
            location: Long = 0,
            length: Long = 0,
        ) : NSRange(location, length),
            Structure.ByValue
    }

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?

        fun sel_registerName(name: String): Pointer

        fun objc_autoreleasePoolPush(): Pointer

        fun objc_autoreleasePoolPop(pool: Pointer)
    }

    @Suppress("FunctionName")
    private interface LibSystem : Library {
        fun dispatch_sync_f(
            queue: Pointer,
            context: Pointer?,
            work: DispatchFunction,
        )

        fun pthread_main_np(): Int
    }

    private interface DispatchFunction : Callback {
        fun invoke(context: Pointer?)
    }

    companion object {
        // NSNotFound is NSIntegerMax.
        private const val NS_NOT_FOUND = Long.MAX_VALUE

        private val objc by lazy { Native.load("objc", ObjC::class.java) }
        private val msgSend by lazy { Function.getFunction("objc", "objc_msgSend") }
        private val system by lazy { Native.load("System", LibSystem::class.java) }
        private val mainQueue by lazy { NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q") }
        private val selectors = HashMap<String, Pointer>()
        private val nsStringClass by lazy { objc.objc_getClass("NSString") ?: error("NSString is not loaded") }

        // One trampoline for every hop to the main thread: JNA allocates native code per Callback
        // instance, and calls are serialized, so the work to run is handed over in [pending].
        private var pending: (() -> Unit)? = null
        private val trampoline =
            object : DispatchFunction {
                override fun invoke(context: Pointer?) {
                    pending?.invoke()
                }
            }

        /**
         * Connects to the shared `NSSpellChecker` and checks that it answers sensibly. Throws when
         * it cannot, so the caller falls back to the built-in dictionary.
         */
        fun create(): MacSpellChecker {
            val shared =
                onMainThread {
                    val type = objc.objc_getClass("NSSpellChecker") ?: error("NSSpellChecker is not loaded")
                    send(type, "sharedSpellChecker") ?: error("No shared NSSpellChecker")
                }
            val checker = MacSpellChecker(shared)
            check(checker.isCorrect("spelling")) { "NSSpellChecker rejected a common word" }
            check(!checker.isCorrect(SELF_TEST_NONSENSE)) { "NSSpellChecker accepted nonsense" }
            checker.suggest("speling")
            return checker
        }

        private fun <T> onMainThread(block: () -> T): T {
            if (system.pthread_main_np() != 0) return block()
            var result: Result<T>? = null
            pending = { result = runCatching(block) }
            try {
                system.dispatch_sync_f(mainQueue, null, trampoline)
            } finally {
                pending = null
            }
            return (result ?: error("The main thread did not run the spell check")).getOrThrow()
        }

        private fun <T> withAutoreleasePool(block: () -> T): T {
            val pool = objc.objc_autoreleasePoolPush()
            try {
                return block()
            } finally {
                objc.objc_autoreleasePoolPop(pool)
            }
        }

        private fun selector(name: String): Pointer = selectors.getOrPut(name) { objc.sel_registerName(name) }

        private fun send(
            receiver: Pointer,
            selector: String,
            vararg args: Any?,
        ): Pointer? = msgSend.invokePointer(arrayOf(receiver, selector(selector), *args))

        private fun sendLong(
            receiver: Pointer,
            selector: String,
            vararg args: Any?,
        ): Long = msgSend.invokeLong(arrayOf(receiver, selector(selector), *args))

        private fun sendRange(
            receiver: Pointer,
            selector: String,
            vararg args: Any?,
        ): NSRange = msgSend.invoke(NSRange.ByValue::class.java, arrayOf(receiver, selector(selector), *args)) as NSRange

        /** An autoreleased NSString; only valid inside [withAutoreleasePool]. */
        private fun nsString(value: String): Pointer {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val memory = Memory(bytes.size + 1L)
            memory.write(0, bytes, 0, bytes.size)
            memory.setByte(bytes.size.toLong(), 0)
            return send(nsStringClass, "stringWithUTF8String:", memory) ?: error("Could not make an NSString")
        }

        private fun kotlinString(nsString: Pointer): String? = send(nsString, "UTF8String")?.getString(0, "UTF-8")
    }
}

/** A word no dictionary has, to check that an engine can say no. */
internal const val SELF_TEST_NONSENSE = "qzxwvkjqtp"
