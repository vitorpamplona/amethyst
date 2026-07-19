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
package com.vitorpamplona.amethyst.cli

/**
 * Minimal argv parser. Splits flags (--key value or --key=value) from positional args.
 * Boolean flags are those whose next token starts with "--" or is absent.
 *
 * A literal `--` token ends flag parsing: every later token is positional even if
 * it starts with `--` (the same escape hatch getopt-style tools offer, so
 * `amy notes post -- "--good morning"` posts the literal text).
 *
 * Every accessor records the flag names a command supports; [rejectUnknown] then
 * turns any leftover `--typo` into a `bad_args` failure instead of a silent no-op.
 */
class Args(
    argv: Array<String>,
) {
    val flags: Map<String, String>
    val booleans: Set<String>
    val positional: List<String>

    /** Flag names a command declared support for by reading them. */
    private val consumed = mutableSetOf<String>()

    init {
        val f = mutableMapOf<String, String>()
        val b = mutableSetOf<String>()
        val p = mutableListOf<String>()
        var i = 0
        var flagsEnded = false
        while (i < argv.size) {
            val a = argv[i]
            if (!flagsEnded && a == "--") {
                flagsEnded = true
                i++
            } else if (!flagsEnded && a.startsWith("--")) {
                val eq = a.indexOf('=')
                if (eq >= 0) {
                    f[a.substring(2, eq)] = a.substring(eq + 1)
                    i++
                } else {
                    val key = a.substring(2)
                    val next = argv.getOrNull(i + 1)
                    if (next == null || next.startsWith("--")) {
                        b.add(key)
                        i++
                    } else {
                        f[key] = next
                        i += 2
                    }
                }
            } else {
                p.add(a)
                i++
            }
        }
        flags = f
        booleans = b
        positional = p
    }

    /** True when the caller asked for help (`--help`, or `-h` slipping in as a positional). */
    val help: Boolean get() = "help" in booleans || positional.firstOrNull() == "-h"

    fun flag(
        name: String,
        default: String? = null,
    ): String? {
        consumed.add(name)
        return flags[name] ?: default
    }

    fun intFlag(
        name: String,
        default: Int,
    ): Int {
        consumed.add(name)
        val raw = flags[name] ?: return default
        return raw.toIntOrNull()
            ?: throw IllegalArgumentException("--$name expects a number, got '$raw'")
    }

    fun longFlag(
        name: String,
        default: Long,
    ): Long {
        consumed.add(name)
        val raw = flags[name] ?: return default
        return raw.toLongOrNull()
            ?: throw IllegalArgumentException("--$name expects a number, got '$raw'")
    }

    /** Read `--timeout` (seconds) and return milliseconds; non-numeric input is bad_args. */
    fun timeoutMs(defaultSecs: Long): Long = longFlag("timeout", defaultSecs) * 1000

    /** Like [timeoutMs] but with no default: null when `--timeout` is absent; non-numeric input is bad_args. */
    fun timeoutMsOrNull(): Long? {
        val raw = flag("timeout") ?: return null
        val secs =
            raw.toLongOrNull()
                ?: throw IllegalArgumentException("--timeout expects a number, got '$raw'")
        return secs * 1000
    }

    fun requireFlag(name: String): String {
        consumed.add(name)
        return flags[name]
            ?: throw IllegalArgumentException("missing required flag: --$name")
    }

    fun bool(name: String): Boolean {
        consumed.add(name)
        return name in booleans
    }

    /**
     * Fail with `bad_args` (exit 2) when argv carried a flag no accessor asked
     * about — the difference between `--limt 5` silently no-oping and the user
     * learning about the typo. Call after every supported flag has been read
     * (a conditional read still counts: `flag()`/`bool()` record the name even
     * when the flag is absent). Commands that forward arbitrary flags simply
     * don't call this.
     */
    fun rejectUnknown(vararg alsoAllowed: String) {
        val known = consumed + alsoAllowed + setOf("help")
        val unknown = (flags.keys + booleans).filterNot { it in known }
        if (unknown.isNotEmpty()) {
            throw IllegalArgumentException(
                "unknown flag${if (unknown.size > 1) "s" else ""}: ${unknown.joinToString(", ") { "--$it" }}",
            )
        }
    }

    fun positional(
        index: Int,
        name: String,
    ): String =
        positional.getOrNull(index)
            ?: throw IllegalArgumentException("missing positional arg: $name")

    fun positionalOrNull(index: Int): String? = positional.getOrNull(index)
}
