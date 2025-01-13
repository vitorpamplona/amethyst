/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core

import android.util.Log
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.nip01Core.core.Event

fun Event.generateId(): String = EventHasher.hashId(pubKey, createdAt, kind, tags, content)

fun Event.hasCorrectIDHash(): Boolean {
    if (id.isEmpty()) return false
    return id == generateId()
}

fun Event.hasVerifiedSignature(): Boolean {
    if (id.isEmpty() || sig.isEmpty()) return false
    return CryptoUtils.verifySignature(Hex.decode(sig), Hex.decode(id), Hex.decode(pubKey))
}

/** Checks if the ID is correct and then if the pubKey's secret key signed the event. */
fun Event.checkSignature() {
    if (!hasCorrectIDHash()) {
        throw Exception(
            """
            |Unexpected ID.
            |  Event: ${toJson()}
            |  Actual ID: $id
            |  Generated: ${generateId()}
            """.trimIndent(),
        )
    }
    if (!hasVerifiedSignature()) {
        throw Exception("""Bad signature!""")
    }
}

fun Event.hasValidSignature(): Boolean =
    try {
        hasCorrectIDHash() && hasVerifiedSignature()
    } catch (e: Exception) {
        Log.w("Event", "Event $id does not have a valid signature: ${toJson()}", e)
        false
    }