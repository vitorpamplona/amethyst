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
package com.vitorpamplona.quartz.nip64Chess.jester

import kotlinx.serialization.Serializable

/**
 * JSON content structure for Jester events
 */
@Serializable
data class JesterContent(
    val version: String = "0",
    val kind: Int,
    val fen: String = JesterProtocol.FEN_START,
    val move: String? = null,
    val history: List<String> = emptyList(),
    val nonce: String? = null,
    // Extended fields for Amethyst (backward compatible - jesterui ignores unknown fields)
    val playerColor: String? = null, // "white" or "black" - challenger's color choice
    val result: String? = null, // "1-0", "0-1", "1/2-1/2" for game end
    val termination: String? = null, // "checkmate", "resignation", "draw_agreement", etc.
)
