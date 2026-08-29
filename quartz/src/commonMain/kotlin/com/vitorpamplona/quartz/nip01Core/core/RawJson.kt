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
package com.vitorpamplona.quartz.nip01Core.core

/**
 * JSON that is already serialized and must reach the wire as-is.
 *
 * Used where the exact BYTES matter and not merely the value. The case that
 * forced it: NIP-57 sets a zap invoice's `description_hash` to the sha256 of the
 * raw zap-request JSON the LNURL callback received, so a wallet binding a stored
 * zap request to the invoice it labels hashes those same bytes. Handing the
 * serializer a decomposed `Map` and hoping it reassembles them identically makes
 * that binding depend on key order, escaping and number formatting agreeing by
 * coincidence — and it fails silently, as an unlabelled row, when they do not.
 *
 * Both backends emit the string verbatim: Jackson via `writeRawValue`, kotlinx via
 * `JsonUnquotedLiteral`. [json] MUST already be well-formed JSON; nothing
 * validates it, and an invalid value corrupts the whole document.
 */
class RawJson(
    val json: String,
) {
    override fun toString() = json

    override fun equals(other: Any?) = other is RawJson && other.json == json

    override fun hashCode() = json.hashCode()
}
