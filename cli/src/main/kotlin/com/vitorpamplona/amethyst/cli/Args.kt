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
 * The parser is intentionally tiny — this CLI is driven by shell scripts, not humans,
 * so we don't need subcommand groups, short flags, or help text generation.
 */
class Args(
    argv: Array<String>,
) {
    val flags: Map<String, String>
    val booleans: Set<String>
    val positional: List<String>

    init {
        val f = mutableMapOf<String, String>()
        val b = mutableSetOf<String>()
        val p = mutableListOf<String>()
        var i = 0
        while (i < argv.size) {
            val a = argv[i]
            if (a.startsWith("--")) {
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

    fun flag(
        name: String,
        default: String? = null,
    ): String? = flags[name] ?: default

    fun intFlag(
        name: String,
        default: Int,
    ): Int = flags[name]?.toIntOrNull() ?: default

    fun longFlag(
        name: String,
        default: Long,
    ): Long = flags[name]?.toLongOrNull() ?: default

    fun requireFlag(name: String): String =
        flags[name] ?: run {
            System.err.println("missing required flag: --$name")
            throw IllegalArgumentException("missing flag $name")
        }

    fun bool(name: String): Boolean = name in booleans

    fun positional(
        index: Int,
        name: String,
    ): String =
        positional.getOrNull(index) ?: run {
            System.err.println("missing positional arg: $name (index $index)")
            throw IllegalArgumentException("missing positional $name")
        }

    fun positionalOrNull(index: Int): String? = positional.getOrNull(index)
}
