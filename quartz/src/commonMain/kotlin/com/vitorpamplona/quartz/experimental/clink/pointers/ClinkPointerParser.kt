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
package com.vitorpamplona.quartz.experimental.clink.pointers

import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * Decodes CLINK bech32 pointers (`noffer1…`, `ndebit1…`, `nmanage1…`).
 *
 * Kept deliberately separate from [com.vitorpamplona.quartz.nip19Bech32.Nip19Parser]:
 * these prefixes are not NIP-19 entities, so we don't widen the app-wide NIP-19
 * matching to recognize them.
 */
object ClinkPointerParser {
    /** All three HRPs, anchored at a `1` separator. Used to spot pointers inside free text. */
    val clinkRegex: Regex =
        Regex(
            "(noffer1|ndebit1|nmanage1)([qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Parses a single pointer string. Tolerates a leading `nostr:`/`lightning:` scheme
     * and surrounding whitespace. Returns null on any malformed input.
     */
    fun parse(pointer: String): ClinkPointer? {
        val cleaned =
            pointer
                .trim()
                .removePrefix("nostr:")
                .removePrefix("lightning:")
                .trim()

        return try {
            val (hrp, bytes, _) = Bech32.decodeBytes(cleaned)
            when (hrp.lowercase()) {
                NOffer.HRP -> NOffer.parse(bytes)
                NDebit.HRP -> NDebit.parse(bytes)
                NManage.HRP -> NManage.parse(bytes)
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.d("ClinkPointerParser") { "Failed to decode CLINK pointer $cleaned: ${e.message}" }
            null
        }
    }

    /** Finds and decodes every CLINK pointer embedded in [content]. */
    fun parseAll(content: String): List<ClinkPointer> = clinkRegex.findAll(content).mapNotNull { parse(it.value) }.toList()
}
