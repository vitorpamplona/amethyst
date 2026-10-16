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

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellChecker

/**
 * The Windows spell checker (the Spell Checking API in `spellcheck.h`, Windows 8 and later), the
 * one Edge and the built-in text controls use: it covers the languages the user installed and the
 * words they added in other apps.
 *
 * Reached through JNA and raw COM vtable calls (no jna-platform). All calls, creation included,
 * must come from one thread, the [SpellCheckService]'s, which this joins to the multithreaded
 * apartment.
 */
class WindowsSpellChecker private constructor(
    private val checker: Pointer,
    private val language: String,
) : SpellChecker {
    override val name = "Windows ($language)"

    // A Latin-script language's checker flags every word in another script.
    private val latinOnly = language.substringBefore('-').lowercase() in LATIN_SCRIPT_LANGUAGES

    override fun handles(word: String) = !latinOnly || isLatinScript(word)

    override fun isCorrect(word: String): Boolean {
        val errors = PointerByReference()
        checkResult(call(checker, SPELL_CHECKER_CHECK, WString(word), errors.pointer))
        val enumeration = errors.value ?: return true
        try {
            val error = PointerByReference()
            val hr = call(enumeration, ENUM_NEXT, error.pointer)
            error.value?.let(::release)
            return hr != S_OK
        } finally {
            release(enumeration)
        }
    }

    override fun suggest(word: String): List<String> {
        val result = PointerByReference()
        checkResult(call(checker, SPELL_CHECKER_SUGGEST, WString(word), result.pointer))
        val enumeration = result.value ?: return emptyList()
        try {
            return buildList {
                while (size < MAX_SUGGESTIONS) {
                    val item = PointerByReference()
                    val fetched = IntByReference()
                    val hr = call(enumeration, ENUM_NEXT, 1, item.pointer, fetched.pointer)
                    if (hr != S_OK || fetched.value == 0) break
                    val text = item.value ?: break
                    add(text.getWideString(0))
                    ole32.CoTaskMemFree(text)
                }
            }
        } finally {
            release(enumeration)
        }
    }

    @Structure.FieldOrder("data1", "data2", "data3", "data4")
    class Guid(
        @JvmField var data1: Int = 0,
        @JvmField var data2: Short = 0,
        @JvmField var data3: Short = 0,
        @JvmField var data4: ByteArray = ByteArray(8),
    ) : Structure() {
        companion object {
            fun of(value: String): Guid {
                val hex = value.replace("-", "")
                require(hex.length == 32) { "Not a GUID: $value" }
                return Guid(
                    hex.substring(0, 8).toLong(16).toInt(),
                    hex.substring(8, 12).toInt(16).toShort(),
                    hex.substring(12, 16).toInt(16).toShort(),
                    ByteArray(8) { hex.substring(16 + it * 2, 18 + it * 2).toInt(16).toByte() },
                )
            }
        }
    }

    @Suppress("FunctionName")
    private interface Ole32 : Library {
        fun CoInitializeEx(
            reserved: Pointer?,
            coInit: Int,
        ): Int

        fun CoCreateInstance(
            clsid: Guid,
            outer: Pointer?,
            context: Int,
            iid: Guid,
            result: PointerByReference,
        ): Int

        fun CoTaskMemFree(memory: Pointer?)
    }

    companion object {
        private const val S_OK = 0
        private const val COINIT_MULTITHREADED = 0
        private const val CLSCTX_INPROC_SERVER = 1
        private const val MAX_SUGGESTIONS = 10

        // vtable slots, after IUnknown's QueryInterface (0), AddRef (1) and Release (2).
        private const val RELEASE = 2
        private const val FACTORY_IS_SUPPORTED = 4
        private const val FACTORY_CREATE_SPELL_CHECKER = 5
        private const val SPELL_CHECKER_CHECK = 4
        private const val SPELL_CHECKER_SUGGEST = 5

        // Next is the first method of both IEnumSpellingError and IEnumString.
        private const val ENUM_NEXT = 3

        private val CLSID_SPELL_CHECKER_FACTORY = Guid.of("7ab36653-1796-484b-bdfa-e74f1db7c1dc")
        private val IID_ISPELL_CHECKER_FACTORY = Guid.of("8e018a9d-2415-4677-bf08-794ea61f94bb")

        private val LATIN_SCRIPT_LANGUAGES =
            setOf(
                "ca",
                "cs",
                "cy",
                "da",
                "de",
                "en",
                "es",
                "et",
                "eu",
                "fi",
                "fr",
                "ga",
                "gl",
                "hr",
                "hu",
                "id",
                "is",
                "it",
                "lt",
                "lv",
                "ms",
                "mt",
                "nb",
                "nl",
                "nn",
                "no",
                "pl",
                "pt",
                "ro",
                "sk",
                "sl",
                "sq",
                "sv",
                "sw",
                "tl",
                "tr",
                "vi",
            )

        private val ole32 by lazy { Native.load("ole32", Ole32::class.java) }

        /**
         * A checker for [languageTag] (a BCP 47 tag like "pt-BR"), falling back to its language
         * alone and then to US English, or null when Windows has none of them. Throws when the
         * Spell Checking API is missing or fails its self-test.
         */
        fun create(languageTag: String): WindowsSpellChecker? {
            // S_FALSE (already initialized) and RPC_E_CHANGED_MODE (already single-threaded) both
            // leave COM usable on this thread.
            ole32.CoInitializeEx(null, COINIT_MULTITHREADED)

            val factoryRef = PointerByReference()
            checkResult(ole32.CoCreateInstance(CLSID_SPELL_CHECKER_FACTORY, null, CLSCTX_INPROC_SERVER, IID_ISPELL_CHECKER_FACTORY, factoryRef))
            val factory = factoryRef.value ?: error("No ISpellCheckerFactory")
            try {
                val candidates = listOf(languageTag, languageTag.substringBefore('-'), "en-US").filter { it.isNotBlank() }.distinct()
                for (tag in candidates) {
                    val supported = IntByReference()
                    if (call(factory, FACTORY_IS_SUPPORTED, WString(tag), supported.pointer) != S_OK || supported.value == 0) continue

                    val checkerRef = PointerByReference()
                    checkResult(call(factory, FACTORY_CREATE_SPELL_CHECKER, WString(tag), checkerRef.pointer))
                    val checker = WindowsSpellChecker(checkerRef.value ?: continue, tag)
                    check(checker.isCorrect("spelling") || !tag.startsWith("en")) { "Windows rejected a common word" }
                    check(!checker.isCorrect(SELF_TEST_NONSENSE)) { "Windows accepted nonsense" }
                    checker.suggest("speling")
                    return checker
                }
                return null
            } finally {
                release(factory)
            }
        }

        /** Calls method [slot] of the COM object [target], passing [target] as `this`. */
        private fun call(
            target: Pointer,
            slot: Int,
            vararg args: Any?,
        ): Int {
            val vtable = target.getPointer(0)
            val method = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            return method.invokeInt(arrayOf(target, *args))
        }

        private fun release(target: Pointer) {
            call(target, RELEASE)
        }

        private fun checkResult(hr: Int) {
            check(hr >= 0) { "COM call failed: 0x${hr.toUInt().toString(16)}" }
        }
    }
}
